package com.securevault.auth;

import com.securevault.config.ConfigurationManager;
import com.securevault.database.DatabaseManager;
import com.securevault.events.EventBus;
import com.securevault.events.SecureVaultEvent;
import com.securevault.exceptions.AuthenticationException;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;

/**
 * Manages user registration, login, and account administration.
 *
 * <p><b>Account Locking Mechanism (interviewers love this):</b>
 * <pre>
 *   Wrong password → failedAttempts++ → persisted to DB
 *   5 failures     → accountLocked=true → further attempts rejected
 *   Admin unlock   → failedAttempts=0, accountLocked=false
 * </pre>
 *
 * <p><b>Thread Safety:</b> All methods are {@code synchronized} because
 * in theory two concurrent sessions could attempt login with the same username.
 *
 * <p><b>Observer Integration:</b> Fires {@link SecureVaultEvent} on login
 * success/failure so {@code AuditLogger} and {@code AnalyticsService}
 * are automatically updated without explicit coupling.
 *
 * @author SecureVault Pro
 * @version 1.0.0
 */
public class AuthenticationService {

    private final DatabaseManager  db;
    private final PasswordHasher   hasher;
    private final EventBus         eventBus;
    private final int              maxFailedAttempts;

    public AuthenticationService() {
        this.db               = DatabaseManager.getInstance();
        this.hasher           = new PasswordHasher();
        this.eventBus         = EventBus.getInstance();
        this.maxFailedAttempts = ConfigurationManager.getInstance().getMaxFailedAttempts();
    }

    // ------------------------------------------------------------------ //
    //  Registration
    // ------------------------------------------------------------------ //

    /**
     * Registers a new user.
     *
     * <ol>
     *   <li>Validates password strength</li>
     *   <li>Checks username uniqueness</li>
     *   <li>Generates salt + PBKDF2 hash</li>
     *   <li>Persists to DB</li>
     * </ol>
     *
     * @param username desired username (3–50 characters)
     * @param password plaintext password
     * @return the newly created {@link User}
     * @throws AuthenticationException if username is taken or password is weak
     */
    public synchronized User register(String username, String password) {
        validateUsername(username);
        hasher.validateStrength(password);

        if (findByUsername(username) != null) {
            throw new AuthenticationException(
                    "Username '" + username + "' is already taken.",
                    AuthenticationException.USER_EXISTS
            );
        }

        String salt   = hasher.generateSalt();
        String hash   = hasher.hash(password, salt);
        User   user   = new User(username, salt, hash);

        long userId = db.executeUpdate(
                "INSERT INTO users (username, salt, hashed_password) VALUES (?, ?, ?)",
                username, salt, hash
        );

        // Fire event so AuditLogger records the registration
        eventBus.publish(SecureVaultEvent.userRegistered((int) userId, username));

        System.out.println("[AuthService] Registered user: " + username);
        return new User((int) userId, username, salt, hash, 0, false, null, LocalDateTime.now());
    }

    // ------------------------------------------------------------------ //
    //  Login
    // ------------------------------------------------------------------ //

    /**
     * Attempts to log in with the given credentials.
     *
     * @param username the username
     * @param password the plaintext password
     * @return the authenticated {@link User}
     * @throws AuthenticationException if credentials are wrong or account is locked
     */
    public synchronized User login(String username, String password) {
        User user = findByUsername(username);

        if (user == null) {
            // Do not reveal whether username exists (security best practice)
            throw new AuthenticationException(
                    "Invalid username or password.",
                    AuthenticationException.INVALID_CREDENTIALS
            );
        }

        // Account lock check
        if (user.isAccountLocked()) {
            eventBus.publish(SecureVaultEvent.loginFailed(user.getUserId(), username, "ACCOUNT_LOCKED"));
            throw new AuthenticationException(
                    "Account '" + username + "' is locked. Contact administrator.",
                    AuthenticationException.ACCOUNT_LOCKED
            );
        }

        // Password verification (constant-time)
        if (!hasher.verify(password, user.getSalt(), user.getHashedPassword())) {
            handleFailedAttempt(user);
            eventBus.publish(SecureVaultEvent.loginFailed(user.getUserId(), username, "WRONG_PASSWORD"));
            throw new AuthenticationException(
                    "Invalid username or password.",
                    AuthenticationException.INVALID_CREDENTIALS
            );
        }

        // Success — reset counter, update lastLogin
        handleSuccessfulLogin(user);
        eventBus.publish(SecureVaultEvent.loginSuccess(user.getUserId(), username));
        return user;
    }

    // ------------------------------------------------------------------ //
    //  Admin operations
    // ------------------------------------------------------------------ //

    /**
     * Unlocks a locked account and resets its failed-attempt counter.
     *
     * @param username the username to unlock
     * @throws AuthenticationException if the user is not found
     */
    public synchronized void unlockAccount(String username) {
        User user = findByUsername(username);
        if (user == null) {
            throw new AuthenticationException("User not found: " + username);
        }
        user.unlockAccount();
        db.executeUpdate(
                "UPDATE users SET account_locked = 0, failed_attempts = 0 WHERE user_id = ?",
                user.getUserId()
        );
        eventBus.publish(SecureVaultEvent.accountUnlocked(user.getUserId(), username));
    }

    // ------------------------------------------------------------------ //
    //  Private helpers
    // ------------------------------------------------------------------ //

    /**
     * Increments failed-attempt counter and locks the account if the threshold is reached.
     */
    private void handleFailedAttempt(User user) {
        user.incrementFailedAttempts();

        if (user.getFailedAttempts() >= maxFailedAttempts) {
            user.lockAccount();
            db.executeUpdate(
                    "UPDATE users SET failed_attempts = ?, account_locked = 1 WHERE user_id = ?",
                    user.getFailedAttempts(), user.getUserId()
            );
            System.out.println("[AuthService] Account LOCKED: " + user.getUsername());
        } else {
            db.executeUpdate(
                    "UPDATE users SET failed_attempts = ? WHERE user_id = ?",
                    user.getFailedAttempts(), user.getUserId()
            );
        }
    }

    /**
     * Resets the failed-attempt counter and updates lastLogin on a successful login.
     */
    private void handleSuccessfulLogin(User user) {
        user.resetFailedAttempts();
        user.setLastLogin(LocalDateTime.now());
        db.executeUpdate(
                "UPDATE users SET failed_attempts = 0, last_login = ? WHERE user_id = ?",
                Timestamp.valueOf(user.getLastLogin()).toString(), user.getUserId()
        );
    }

    /**
     * Loads a {@link User} from the DB by username, or returns {@code null}.
     */
    public User findByUsername(String username) {
        String sql = "SELECT * FROM users WHERE username = ?";
        try (PreparedStatement ps = db.prepareStatement(sql, username);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return mapRow(rs);
            }
        } catch (SQLException e) {
            throw new AuthenticationException("DB error looking up user: " + e.getMessage());
        }
        return null;
    }

    /**
     * Maps a {@link ResultSet} row to a {@link User} object.
     */
    private User mapRow(ResultSet rs) throws SQLException {
        Timestamp lastLoginTs = rs.getTimestamp("last_login");
        Timestamp createdTs   = rs.getTimestamp("created_at");
        return new User(
                rs.getInt("user_id"),
                rs.getString("username"),
                rs.getString("salt"),
                rs.getString("hashed_password"),
                rs.getInt("failed_attempts"),
                rs.getBoolean("account_locked"),
                lastLoginTs  != null ? lastLoginTs.toLocalDateTime()  : null,
                createdTs    != null ? createdTs.toLocalDateTime()    : LocalDateTime.now()
        );
    }

    /**
     * Validates username length and character constraints.
     */
    private void validateUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new AuthenticationException("Username cannot be empty.");
        }
        if (username.length() < 3 || username.length() > 50) {
            throw new AuthenticationException("Username must be between 3 and 50 characters.");
        }
        if (!username.matches("[a-zA-Z0-9_]+")) {
            throw new AuthenticationException("Username may only contain letters, digits, and underscores.");
        }
    }
}
