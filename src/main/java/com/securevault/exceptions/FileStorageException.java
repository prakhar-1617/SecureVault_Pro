package com.securevault.exceptions;

/**
 * Thrown when file upload, download, or storage operations fail.
 */
public class FileStorageException extends SecureVaultException {

    public static final String FILE_NOT_FOUND   = "FS-001";
    public static final String READ_FAILED      = "FS-002";
    public static final String WRITE_FAILED     = "FS-003";
    public static final String DELETE_FAILED    = "FS-004";

    public FileStorageException(String message) {
        super(message, WRITE_FAILED, null);
    }

    public FileStorageException(String message, String errorCode) {
        super(message, errorCode, null);
    }

    public FileStorageException(String message, String errorCode, Throwable cause) {
        super(message, errorCode, cause);
    }
}
