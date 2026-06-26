package com.securevault.events;

import java.time.LocalDateTime;

/**
 * Immutable value object representing an application event.
 *
 * <p>All application events (login, file operations, encryption) are
 * represented as instances of this class and routed through {@link EventBus}.
 *
 * <p>Factory methods are provided for each event type, ensuring consistent
 * event construction across the codebase.
 *
 * @author SecureVault Pro
 * @version 1.0.0
 */
public final class SecureVaultEvent {

    // ------------------------------------------------------------------ //
    //  Event type enumeration
    // ------------------------------------------------------------------ //

    public enum Type {
        // Auth events
        USER_REGISTERED,
        LOGIN_SUCCESS,
        LOGIN_FAILED,
        ACCOUNT_LOCKED,
        ACCOUNT_UNLOCKED,

        // File events
        FILE_ENCRYPTED,
        FILE_DECRYPTED,
        FILE_DELETED,
        INTEGRITY_VIOLATION,

        // Task events
        TASK_SUBMITTED,
        TASK_COMPLETED,
        TASK_FAILED
    }

    // ------------------------------------------------------------------ //
    //  Fields
    // ------------------------------------------------------------------ //

    private final Type          type;
    private final int           userId;       // -1 for system events
    private final String        username;
    private final String        detail;
    private final LocalDateTime timestamp;

    // ------------------------------------------------------------------ //
    //  Constructor
    // ------------------------------------------------------------------ //

    private SecureVaultEvent(Type type, int userId, String username, String detail) {
        this.type      = type;
        this.userId    = userId;
        this.username  = username;
        this.detail    = detail;
        this.timestamp = LocalDateTime.now();
    }

    // ------------------------------------------------------------------ //
    //  Factory methods — one per event type
    // ------------------------------------------------------------------ //

    public static SecureVaultEvent userRegistered(int userId, String username) {
        return new SecureVaultEvent(Type.USER_REGISTERED, userId, username, "New user registered");
    }

    public static SecureVaultEvent loginSuccess(int userId, String username) {
        return new SecureVaultEvent(Type.LOGIN_SUCCESS, userId, username, "Login successful");
    }

    public static SecureVaultEvent loginFailed(int userId, String username, String reason) {
        return new SecureVaultEvent(Type.LOGIN_FAILED, userId, username, "Login failed: " + reason);
    }

    public static SecureVaultEvent accountUnlocked(int userId, String username) {
        return new SecureVaultEvent(Type.ACCOUNT_UNLOCKED, userId, username, "Account unlocked by admin");
    }

    public static SecureVaultEvent fileEncrypted(int userId, String filename, String algorithm) {
        return new SecureVaultEvent(Type.FILE_ENCRYPTED, userId, "system",
                "File encrypted: " + filename + " [" + algorithm + "]");
    }

    public static SecureVaultEvent fileDecrypted(int userId, String filename) {
        return new SecureVaultEvent(Type.FILE_DECRYPTED, userId, "system",
                "File decrypted: " + filename);
    }

    public static SecureVaultEvent integrityViolation(int userId, String filename) {
        return new SecureVaultEvent(Type.INTEGRITY_VIOLATION, userId, "system",
                "INTEGRITY VIOLATION detected on: " + filename);
    }

    public static SecureVaultEvent taskCompleted(int userId, String detail) {
        return new SecureVaultEvent(Type.TASK_COMPLETED, userId, "system", detail);
    }

    public static SecureVaultEvent taskFailed(int userId, String detail) {
        return new SecureVaultEvent(Type.TASK_FAILED, userId, "system", detail);
    }

    // ------------------------------------------------------------------ //
    //  Getters
    // ------------------------------------------------------------------ //

    public Type          getType()      { return type;      }
    public int           getUserId()    { return userId;    }
    public String        getUsername()  { return username;  }
    public String        getDetail()    { return detail;    }
    public LocalDateTime getTimestamp() { return timestamp; }

    @Override
    public String toString() {
        return "[" + timestamp + "] " + type + " | user=" + username + " | " + detail;
    }
}
