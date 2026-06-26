package com.securevault.exceptions;

/**
 * Thrown when authentication fails or an account is in an invalid state.
 *
 * <p>Common scenarios:
 * <ul>
 *   <li>Wrong password supplied during login</li>
 *   <li>Account has been locked after too many failed attempts</li>
 *   <li>Username does not exist</li>
 *   <li>Password does not meet strength requirements during registration</li>
 * </ul>
 *
 * <p>Error codes used:
 * <ul>
 *   <li>{@code AUTH-001} — Invalid credentials</li>
 *   <li>{@code AUTH-002} — Account locked</li>
 *   <li>{@code AUTH-003} — Username already exists</li>
 *   <li>{@code AUTH-004} — Weak password</li>
 * </ul>
 */
public class AuthenticationException extends SecureVaultException {

    public static final String INVALID_CREDENTIALS = "AUTH-001";
    public static final String ACCOUNT_LOCKED      = "AUTH-002";
    public static final String USER_EXISTS         = "AUTH-003";
    public static final String WEAK_PASSWORD       = "AUTH-004";

    public AuthenticationException(String message) {
        super(message, INVALID_CREDENTIALS, null);
    }

    public AuthenticationException(String message, String errorCode) {
        super(message, errorCode, null);
    }

    public AuthenticationException(String message, String errorCode, Throwable cause) {
        super(message, errorCode, cause);
    }
}
