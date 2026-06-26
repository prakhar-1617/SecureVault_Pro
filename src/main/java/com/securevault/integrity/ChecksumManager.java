package com.securevault.integrity;

import com.securevault.database.DatabaseManager;
import com.securevault.exceptions.IntegrityException;
import com.securevault.util.HashUtil;

import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;

/**
 * Computes and verifies SHA-256 checksums for files stored in the vault.
 *
 * <p><b>Storage format:</b> Each file record in the {@code files} table stores:
 * <ul>
 *   <li>{@code checksum} — SHA-256 hex string of the <em>original</em> plaintext</li>
 *   <li>{@code algorithm} — the hash algorithm used (always "SHA-256")</li>
 *   <li>{@code upload_time} — when the checksum was recorded</li>
 * </ul>
 *
 * <p><b>Why hash the original, not the encrypted file?</b>
 * The checksum is used to verify that decryption produced the correct output.
 * Hashing the plaintext allows us to confirm end-to-end integrity:
 * {@code hash(decrypt(encrypted)) == stored_checksum}.
 * The {@link com.securevault.decorators.ChecksumDecorator} separately protects
 * the ciphertext from tampering.
 *
 * @author SecureVault Pro
 * @version 1.0.0
 */
public class ChecksumManager {

    private final DatabaseManager db;

    public ChecksumManager() {
        this.db = DatabaseManager.getInstance();
    }

    // ------------------------------------------------------------------ //
    //  Compute
    // ------------------------------------------------------------------ //

    /**
     * Computes the SHA-256 hash of the given file by streaming its contents.
     *
     * @param filePath path to the file
     * @return SHA-256 hex string (64 characters)
     */
    public String compute(Path filePath) {
        return HashUtil.hashFile(filePath);
    }

    /**
     * Computes the SHA-256 hash of a byte array (for in-memory data).
     *
     * @param data the data to hash
     * @return SHA-256 hex string
     */
    public String compute(byte[] data) {
        return HashUtil.hash(data);
    }

    // ------------------------------------------------------------------ //
    //  Verify
    // ------------------------------------------------------------------ //

    /**
     * Verifies that the SHA-256 hash of {@code filePath} matches {@code expectedChecksum}.
     *
     * @param filePath         path to the file to verify
     * @param expectedChecksum the stored SHA-256 hex string
     * @throws IntegrityException if the hashes do not match
     */
    public void verify(Path filePath, String expectedChecksum) {
        boolean valid = HashUtil.verifyFile(filePath, expectedChecksum);
        if (!valid) {
            throw new IntegrityException(
                    "Integrity check FAILED for: " + filePath.getFileName()
                    + " — file may have been corrupted or tampered with.",
                    IntegrityException.CHECKSUM_MISMATCH
            );
        }
    }

    /**
     * Verifies integrity of a decrypted byte array against the stored checksum.
     *
     * @param decryptedData    the decrypted plaintext bytes
     * @param expectedChecksum the SHA-256 hex string stored in the DB
     * @throws IntegrityException if verification fails
     */
    public void verify(byte[] decryptedData, String expectedChecksum) {
        boolean valid = HashUtil.verify(decryptedData, expectedChecksum);
        if (!valid) {
            throw new IntegrityException(
                    "Decrypted data integrity check FAILED — data may be corrupted.",
                    IntegrityException.CHECKSUM_MISMATCH
            );
        }
    }

    // ------------------------------------------------------------------ //
    //  DB lookups
    // ------------------------------------------------------------------ //

    /**
     * Retrieves the stored checksum for a file from the database.
     *
     * @param fileId the file's database ID
     * @return the stored SHA-256 checksum, or {@code null} if not found
     */
    public String getStoredChecksum(int fileId) {
        String sql = "SELECT checksum FROM files WHERE file_id = ?";
        try (PreparedStatement ps = db.prepareStatement(sql, fileId);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) return rs.getString("checksum");
        } catch (SQLException e) {
            throw new IntegrityException("Failed to retrieve checksum for file " + fileId, e.getMessage(), e);
        }
        return null;
    }
}
