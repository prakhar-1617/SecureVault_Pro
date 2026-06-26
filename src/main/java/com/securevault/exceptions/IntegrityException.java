package com.securevault.exceptions;

/**
 * Thrown when SHA-256 integrity verification fails.
 *
 * <p>This indicates the file was tampered with or corrupted after encryption.
 */
public class IntegrityException extends SecureVaultException {

    public static final String CHECKSUM_MISMATCH = "INT-001";
    public static final String CHECKSUM_MISSING  = "INT-002";

    public IntegrityException(String message) {
        super(message, CHECKSUM_MISMATCH, null);
    }

    public IntegrityException(String message, String errorCode) {
        super(message, errorCode, null);
    }

    public IntegrityException(String message, String errorCode, Throwable cause) {
        super(message, errorCode, cause);
    }
}
