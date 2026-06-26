package com.securevault.util;

import com.securevault.exceptions.IntegrityException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Utility class providing SHA-256 hashing operations used by
 * {@link com.securevault.integrity.ChecksumManager} and the LRU cache key generation.
 *
 * <p><b>Design Decision:</b> Static utility methods — no state means no need
 * for an instance. Methods are {@code public static} for convenient access.
 *
 * <p><b>Interview talking point:</b> SHA-256 produces a 256-bit (32-byte)
 * deterministic digest. It is collision-resistant — no two known inputs
 * produce the same hash. This makes it ideal for integrity verification
 * but NOT for password storage (use PBKDF2 for passwords — see
 * {@link com.securevault.auth.PasswordHasher}).
 *
 * @author SecureVault Pro
 * @version 1.0.0
 */
public final class HashUtil {

    private static final String SHA_256 = "SHA-256";

    /** Prevent instantiation — pure utility class. */
    private HashUtil() {
        throw new UnsupportedOperationException("HashUtil is a utility class");
    }

    // ------------------------------------------------------------------ //
    //  Core hashing methods
    // ------------------------------------------------------------------ //

    /**
     * Computes the SHA-256 hash of a byte array.
     *
     * @param data the data to hash
     * @return lowercase hex-encoded SHA-256 digest (64 characters)
     * @throws IntegrityException if SHA-256 is unavailable (should never happen on JVM)
     */
    public static String hash(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance(SHA_256);
            byte[] hashBytes = digest.digest(data);
            return HexFormat.of().formatHex(hashBytes);   // Java 17+ — no external lib needed
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandated by the Java SE spec — this branch is unreachable
            throw new IntegrityException("SHA-256 not available: " + e.getMessage(),
                    IntegrityException.CHECKSUM_MISMATCH, e);
        }
    }

    /**
     * Computes the SHA-256 hash of a file by streaming its contents.
     *
     * <p>Uses a 8 KB read buffer to avoid loading the entire file into memory,
     * making it suitable for large files.
     *
     * @param filePath path to the file
     * @return lowercase hex-encoded SHA-256 digest
     * @throws IntegrityException if the file cannot be read
     */
    public static String hashFile(Path filePath) {
        try {
            MessageDigest digest = MessageDigest.getInstance(SHA_256);
            try (InputStream is = Files.newInputStream(filePath)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    digest.update(buffer, 0, bytesRead);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IntegrityException("SHA-256 not available", IntegrityException.CHECKSUM_MISMATCH, e);
        } catch (IOException e) {
            throw new IntegrityException(
                    "Failed to read file for hashing: " + filePath,
                    IntegrityException.CHECKSUM_MISMATCH,
                    e
            );
        }
    }

    /**
     * Verifies that the hash of {@code data} matches the expected hex string.
     *
     * <p>Uses a constant-time comparison to prevent timing attacks.
     *
     * @param data         the data to verify
     * @param expectedHash the expected SHA-256 hex string
     * @return {@code true} if hashes match, {@code false} otherwise
     */
    public static boolean verify(byte[] data, String expectedHash) {
        String actualHash = hash(data);
        return constantTimeEquals(actualHash, expectedHash);
    }

    /**
     * Verifies that the hash of a file matches the expected hex string.
     *
     * @param filePath     path to the file
     * @param expectedHash the expected SHA-256 hex string
     * @return {@code true} if hashes match, {@code false} otherwise
     */
    public static boolean verifyFile(Path filePath, String expectedHash) {
        String actualHash = hashFile(filePath);
        return constantTimeEquals(actualHash, expectedHash);
    }

    // ------------------------------------------------------------------ //
    //  Private helpers
    // ------------------------------------------------------------------ //

    /**
     * Compares two strings in constant time to prevent timing attacks.
     *
     * <p><b>Interview talking point:</b> Naive string comparison short-circuits
     * on the first mismatch, leaking timing information that an attacker can
     * exploit (a timing side-channel). Constant-time comparison always processes
     * every character.
     *
     * @param a first string
     * @param b second string
     * @return {@code true} if both strings are identical
     */
    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        if (a.length() != b.length()) return false;
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}
