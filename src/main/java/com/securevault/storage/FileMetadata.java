package com.securevault.storage;

/**
 * Immutable record representing file metadata stored in the database.
 * Used by {@link FileStorageService} for file management operations.
 *
 * @param fileId        unique file ID
 * @param userId        owning user ID
 * @param originalName  original plaintext file name
 * @param encryptedPath local path to the encrypted file bytes
 * @param checksum       SHA-256 integrity hash of original file
 * @param algorithm     encryption algorithm used
 * @param fileSize      size of original file in bytes
 * @param uploadTime    formatted string representation of the upload time
 *
 * @author SecureVault Pro
 * @version 1.0.0
 */
public record FileMetadata(
        int fileId,
        int userId,
        String originalName,
        String encryptedPath,
        String checksum,
        String algorithm,
        long fileSize,
        String uploadTime
) {}
