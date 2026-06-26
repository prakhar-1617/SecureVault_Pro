package com.securevault.exceptions;

/**
 * Thrown when encryption or decryption fails.
 *
 * <p>Covers AES key generation failures, IV corruption, padding errors,
 * unknown algorithm names, or pipeline construction failures.
 *
 * <p>Error codes:
 * <ul>
 *   <li>{@code ENC-001} — Algorithm not found</li>
 *   <li>{@code ENC-002} — Encryption failed</li>
 *   <li>{@code ENC-003} — Decryption failed (e.g., wrong key / corrupted data)</li>
 *   <li>{@code ENC-004} — Pipeline configuration invalid</li>
 * </ul>
 */
public class EncryptionException extends SecureVaultException {

    public static final String ALGORITHM_NOT_FOUND   = "ENC-001";
    public static final String ENCRYPTION_FAILED     = "ENC-002";
    public static final String DECRYPTION_FAILED     = "ENC-003";
    public static final String INVALID_PIPELINE      = "ENC-004";

    public EncryptionException(String message) {
        super(message, ENCRYPTION_FAILED, null);
    }

    public EncryptionException(String message, String errorCode) {
        super(message, errorCode, null);
    }

    public EncryptionException(String message, String errorCode, Throwable cause) {
        super(message, errorCode, cause);
    }
}
