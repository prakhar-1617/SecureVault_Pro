package com.securevault.auth;

import com.securevault.config.ConfigurationManager;
import com.securevault.exceptions.AuthenticationException;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;

/**
 * Provides PBKDF2WithHmacSHA256 password hashing and verification.
 *
 * <p><b>Why PBKDF2?</b> Plain SHA-256 hashes are vulnerable to rainbow-table
 * and brute-force attacks because GPUs can compute billions per second.
 * PBKDF2 deliberately slows hashing via repeated iterations, making brute-force
 * attacks computationally infeasible.
 *
 * <p><b>Parameters (OWASP 2023):</b>
 * <ul>
 *   <li>Algorithm: PBKDF2WithHmacSHA256</li>
 *   <li>Iterations: 310,000 (configurable)</li>
 *   <li>Salt: 16 bytes, {@link SecureRandom}</li>
 *   <li>Key length: 256 bits</li>
 * </ul>
 *
 * <p><b>Interview talking point:</b>
 * Q: "Why not bcrypt or scrypt?" — Both are excellent; PBKDF2 is the FIPS 140-2
 * compliant choice and natively available in Java SE without third-party libs.
 *
 * @author SecureVault Pro
 * @version 1.0.0
 */
public final class PasswordHasher {

    private static final String  ALGORITHM      = "PBKDF2WithHmacSHA256";
    private static final int     SALT_BYTES      = 16;
    private final int            iterations;
    private final int            keyLengthBits;
    private final SecureRandom   secureRandom;

    /**
     * Creates a hasher using values from {@link ConfigurationManager}.
     */
    public PasswordHasher() {
        ConfigurationManager cfg = ConfigurationManager.getInstance();
        this.iterations    = cfg.getPbkdf2Iterations();
        this.keyLengthBits = cfg.getPbkdf2KeyLength();
        this.secureRandom  = new SecureRandom();
    }

    // ------------------------------------------------------------------ //
    //  Public API
    // ------------------------------------------------------------------ //

    /**
     * Generates a cryptographically random 16-byte salt, Base64-encoded.
     *
     * <p>{@link SecureRandom} uses the OS entropy source (e.g., {@code /dev/urandom}
     * on Linux, CryptGenRandom on Windows), making it suitable for security use.
     *
     * @return Base64-encoded salt string
     */
    public String generateSalt() {
        byte[] salt = new byte[SALT_BYTES];
        secureRandom.nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }

    /**
     * Hashes a password with the given salt using PBKDF2WithHmacSHA256.
     *
     * @param password plaintext password (char[] to allow zeroing after use)
     * @param saltBase64 Base64-encoded salt string from {@link #generateSalt()}
     * @return Base64-encoded hash string suitable for DB storage
     * @throws AuthenticationException if the PBKDF2 algorithm is unavailable
     */
    public String hash(char[] password, String saltBase64) {
        byte[] salt = Base64.getDecoder().decode(saltBase64);
        try {
            PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, keyLengthBits);
            SecretKeyFactory factory = SecretKeyFactory.getInstance(ALGORITHM);
            byte[] hashBytes = factory.generateSecret(spec).getEncoded();
            spec.clearPassword();   // Zero out password from memory
            return Base64.getEncoder().encodeToString(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new AuthenticationException(
                    "PBKDF2WithHmacSHA256 not available on this JVM",
                    AuthenticationException.INVALID_CREDENTIALS, e
            );
        } catch (InvalidKeySpecException e) {
            throw new AuthenticationException(
                    "Invalid key spec: " + e.getMessage(),
                    AuthenticationException.INVALID_CREDENTIALS, e
            );
        }
    }

    /**
     * Overload accepting a String password (converts to char[] internally).
     *
     * @param password plaintext password
     * @param saltBase64 Base64-encoded salt
     * @return Base64-encoded hash
     */
    public String hash(String password, String saltBase64) {
        return hash(password.toCharArray(), saltBase64);
    }

    /**
     * Verifies a plaintext password against a stored hash.
     *
     * <p>Uses {@link #constantTimeEquals} to prevent timing side-channel attacks.
     *
     * @param password      plaintext password to check
     * @param saltBase64    the stored salt
     * @param expectedHash  the stored PBKDF2 hash
     * @return {@code true} if the password is correct
     */
    public boolean verify(String password, String saltBase64, String expectedHash) {
        String actualHash = hash(password, saltBase64);
        return constantTimeEquals(actualHash, expectedHash);
    }

    /**
     * Validates password strength requirements:
     * <ul>
     *   <li>At least 8 characters</li>
     *   <li>At least one uppercase letter</li>
     *   <li>At least one digit</li>
     *   <li>At least one special character</li>
     * </ul>
     *
     * @param password the candidate password
     * @throws AuthenticationException with {@code AUTH-004} if requirements are not met
     */
    public void validateStrength(String password) {
        if (password == null || password.length() < 8) {
            throw new AuthenticationException(
                    "Password must be at least 8 characters long.",
                    AuthenticationException.WEAK_PASSWORD
            );
        }
        boolean hasUpper   = password.chars().anyMatch(Character::isUpperCase);
        boolean hasDigit   = password.chars().anyMatch(Character::isDigit);
        boolean hasSpecial = password.chars().anyMatch(c -> "!@#$%^&*()_+-=[]{}|;:,.<>?".indexOf(c) >= 0);

        if (!hasUpper || !hasDigit || !hasSpecial) {
            throw new AuthenticationException(
                    "Password must contain at least one uppercase letter, one digit, and one special character.",
                    AuthenticationException.WEAK_PASSWORD
            );
        }
    }

    // ------------------------------------------------------------------ //
    //  Private helpers
    // ------------------------------------------------------------------ //

    /**
     * Compares two strings in constant time to prevent timing attacks.
     *
     * <p><b>Interview talking point:</b> Naive {@code equals()} short-circuits
     * at the first mismatched character, leaking how many characters matched.
     * An attacker measuring response times can incrementally determine the hash.
     */
    private boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) return false;
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}
