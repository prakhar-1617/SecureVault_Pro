package com.securevault.auth;

import java.util.Arrays;

/**
 * Singleton managing the active user session.
 * Stores the currently logged-in user and their derived encryption key.
 *
 * <p><b>Security Practice:</b>
 * The encryption key is stored as a raw byte array so that it can be explicitly
 * overwritten with zeros (zeroized) upon logout or application shutdown, preventing
 * key remnants from remaining in memory (a common issue with garbage collection
 * of immutable objects like Strings).
 *
 * @author SecureVault Pro
 * @version 1.0.0
 */
public final class SessionManager {

    private static volatile SessionManager instance;

    private User currentUser;
    private byte[] derivedKey;

    private SessionManager() {}

    /**
     * Returns the singleton instance of SessionManager.
     *
     * @return the single SessionManager instance
     */
    public static SessionManager getInstance() {
        if (instance == null) {
            synchronized (SessionManager.class) {
                if (instance == null) {
                    instance = new SessionManager();
                }
            }
        }
        return instance;
    }

    /**
     * Authenticates a session with the user and their derived master key.
     *
     * @param user the authenticated User
     * @param key  the derived key bytes
     */
    public synchronized void login(User user, byte[] key) {
        logout(); // Clear any existing session first
        this.currentUser = user;
        this.derivedKey = Arrays.copyOf(key, key.length);
    }

    /**
     * Clears the current session and securely wipes the key bytes from memory.
     */
    public synchronized void logout() {
        this.currentUser = null;
        if (this.derivedKey != null) {
            Arrays.fill(this.derivedKey, (byte) 0);
            this.derivedKey = null;
        }
    }

    /**
     * Checks if a session is currently active.
     *
     * @return true if a user is logged in
     */
    public synchronized boolean isActive() {
        return currentUser != null;
    }

    /**
     * Gets the currently logged-in user.
     *
     * @return the User or null
     */
    public synchronized User getCurrentUser() {
        return currentUser;
    }

    /**
     * Gets a copy of the derived key bytes.
     * Callers must zero out the returned array after use if they copy it.
     *
     * @return copy of derived key bytes, or null if no session active
     */
    public synchronized byte[] getDerivedKey() {
        if (derivedKey == null) {
            return null;
        }
        return Arrays.copyOf(derivedKey, derivedKey.length);
    }
}
