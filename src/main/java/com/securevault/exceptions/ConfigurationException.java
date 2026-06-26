package com.securevault.exceptions;

/**
 * Thrown when the configuration file is missing, malformed,
 * or a required key is absent.
 */
public class ConfigurationException extends SecureVaultException {

    public static final String MISSING_KEY      = "CFG-001";
    public static final String INVALID_VALUE    = "CFG-002";
    public static final String FILE_NOT_FOUND   = "CFG-003";

    public ConfigurationException(String message) {
        super(message, MISSING_KEY, null);
    }

    public ConfigurationException(String message, String errorCode) {
        super(message, errorCode, null);
    }

    public ConfigurationException(String message, String errorCode, Throwable cause) {
        super(message, errorCode, cause);
    }
}
