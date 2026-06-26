package com.securevault.exceptions;

/**
 * Base exception for all SecureVault Pro application exceptions.
 *
 * <p>Design Decision: Using a custom exception hierarchy allows callers to
 * catch specific error types (e.g., {@link AuthenticationException}) without
 * catching unrelated runtime errors. This follows the principle of
 * "throw early, catch late" and makes the API self-documenting.
 *
 * <p>Interview talking point: Custom exceptions improve debuggability,
 * enable fine-grained error handling, and communicate intent to the reader.
 *
 * @author SecureVault Pro
 * @version 1.0.0
 */
public class SecureVaultException extends RuntimeException {

    private final String errorCode;

    /**
     * Constructs a new SecureVaultException with a message.
     *
     * @param message Human-readable description of the error
     */
    public SecureVaultException(String message) {
        super(message);
        this.errorCode = "SVP-000";
    }

    /**
     * Constructs a new SecureVaultException with a message and root cause.
     *
     * @param message Human-readable description of the error
     * @param cause   The underlying exception that triggered this one
     */
    public SecureVaultException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "SVP-000";
    }

    /**
     * Constructs a new SecureVaultException with a message, error code, and root cause.
     *
     * @param message   Human-readable description of the error
     * @param errorCode Short machine-readable code (e.g., "AUTH-001")
     * @param cause     The underlying exception
     */
    public SecureVaultException(String message, String errorCode, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    /**
     * Returns the short error code for programmatic handling.
     *
     * @return error code string
     */
    public String getErrorCode() {
        return errorCode;
    }
}
