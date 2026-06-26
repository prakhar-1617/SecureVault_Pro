package com.securevault.auth;

import java.time.LocalDateTime;

/**
 * Immutable value object representing a registered user.
 *
 * <p>Contains all security metadata needed by {@link AuthenticationService}
 * including the account-locking fields that allow an interviewer-friendly
 * demonstration of the Account Locking Mechanism:
 *
 * <pre>
 *   Wrong password → failedAttempts++ → 5 attempts → accountLocked = true
 * </pre>
 *
 * <p><b>Design Decision:</b> {@code User} is a pure data class. Business logic
 * (hashing, locking) lives in {@link AuthenticationService}, following
 * the Single Responsibility Principle.
 *
 * @author SecureVault Pro
 * @version 1.0.0
 */
public class User {

    private final int       userId;
    private final String    username;
    private final String    salt;
    private final String    hashedPassword;
    private int             failedAttempts;
    private boolean         accountLocked;
    private LocalDateTime   lastLogin;
    private final LocalDateTime createdAt;

    /**
     * Full constructor used when loading a user from the database.
     */
    public User(int userId, String username, String salt, String hashedPassword,
                int failedAttempts, boolean accountLocked,
                LocalDateTime lastLogin, LocalDateTime createdAt) {
        this.userId         = userId;
        this.username       = username;
        this.salt           = salt;
        this.hashedPassword = hashedPassword;
        this.failedAttempts = failedAttempts;
        this.accountLocked  = accountLocked;
        this.lastLogin      = lastLogin;
        this.createdAt      = createdAt;
    }

    /**
     * Constructor for new user registration (before DB insert).
     */
    public User(String username, String salt, String hashedPassword) {
        this(-1, username, salt, hashedPassword, 0, false, null, LocalDateTime.now());
    }

    // ------------------------------------------------------------------ //
    //  Getters
    // ------------------------------------------------------------------ //

    public int           getUserId()        { return userId;         }
    public String        getUsername()      { return username;       }
    public String        getSalt()          { return salt;           }
    public String        getHashedPassword(){ return hashedPassword; }
    public int           getFailedAttempts(){ return failedAttempts; }
    public boolean       isAccountLocked()  { return accountLocked;  }
    public LocalDateTime getLastLogin()     { return lastLogin;      }
    public LocalDateTime getCreatedAt()     { return createdAt;      }

    // ------------------------------------------------------------------ //
    //  State mutators (called by AuthenticationService)
    // ------------------------------------------------------------------ //

    /** Increments failed login counter. */
    public void incrementFailedAttempts() { this.failedAttempts++;  }

    /** Resets failed counter to zero on successful login. */
    public void resetFailedAttempts()     { this.failedAttempts = 0; }

    /** Locks the account. */
    public void lockAccount()             { this.accountLocked = true; }

    /** Unlocks the account (admin reset). */
    public void unlockAccount()           { this.accountLocked = false; this.failedAttempts = 0; }

    /** Updates last login timestamp. */
    public void setLastLogin(LocalDateTime dt) { this.lastLogin = dt; }

    @Override
    public String toString() {
        return "User{id=" + userId + ", username='" + username + '\''
                + ", locked=" + accountLocked
                + ", failedAttempts=" + failedAttempts + '}';
    }
}
