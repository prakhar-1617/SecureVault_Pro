package com.securevault.encryption;

import com.securevault.exceptions.EncryptionException;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * AES-256-GCM encryption strategy — the default and only recommended
 * algorithm for real file encryption in SecureVault Pro.
 *
 * <p><b>Why AES-GCM over AES-CBC?</b>
 * <ul>
 *   <li><b>Authenticated Encryption:</b> GCM mode provides both confidentiality
 *       AND authenticity. CBC encryption alone does not detect tampering.</li>
 *   <li><b>No padding oracle attacks:</b> GCM uses stream-cipher mode internally;
 *       no PKCS#7 padding is needed, eliminating padding oracle vulnerabilities.</li>
 *   <li><b>IV uniqueness:</b> Each call generates a fresh 12-byte IV via
 *       {@link SecureRandom}. Reusing an IV with GCM catastrophically breaks security.</li>
 * </ul>
 *
 * <p><b>Ciphertext format:</b> {@code [12-byte IV][16-byte GCM tag][ciphertext]}
 * The IV is prepended so it can be recovered during decryption without storing
 * it separately.
 *
 * <p><b>Interview talking points:</b>
 * <ul>
 *   <li>Why random IV? — Prevents identical plaintexts from producing identical ciphertexts</li>
 *   <li>Why GCM tag? — Detects tampering during decryption (authenticated encryption)</li>
 *   <li>Key storage? — In production: HSM or OS keystore. Here: in-memory after login.</li>
 * </ul>
 *
 * @author SecureVault Pro
 * @version 1.0.0
 */
public class AESStrategy implements EncryptionStrategy {

    private static final String  ALGORITHM        = "AES";
    private static final String  CIPHER_TRANSFORM = "AES/GCM/NoPadding";
    private static final int     KEY_SIZE_BITS    = 256;
    private static final int     IV_BYTES         = 12;     // 96-bit IV recommended for GCM
    private static final int     GCM_TAG_BITS     = 128;

    private final SecretKey  secretKey;
    private final SecureRandom random;

    /**
     * Creates an AES-256-GCM strategy with a freshly generated key.
     * Used when encrypting a new file for the first time.
     */
    public AESStrategy() {
        this.secretKey = generateKey();
        this.random    = new SecureRandom();
    }

    /**
     * Creates an AES-256-GCM strategy with an existing key (for decryption).
     *
     * @param keyBytes raw 32-byte AES key
     */
    public AESStrategy(byte[] keyBytes) {
        if (keyBytes.length != 32) {
            throw new EncryptionException(
                    "AES-256 requires a 32-byte key, got: " + keyBytes.length,
                    EncryptionException.ENCRYPTION_FAILED
            );
        }
        this.secretKey = new SecretKeySpec(keyBytes, ALGORITHM);
        this.random    = new SecureRandom();
    }

    // ------------------------------------------------------------------ //
    //  EncryptionStrategy implementation
    // ------------------------------------------------------------------ //

    /**
     * Encrypts {@code data} using AES-256-GCM with a fresh random IV.
     *
     * <p>Output format: {@code [12 bytes IV][ciphertext + 16 byte GCM tag]}
     *
     * @param data plaintext bytes
     * @return ciphertext with prepended IV
     */
    @Override
    public byte[] encrypt(byte[] data) {
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_BITS, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec);

            byte[] ciphertext = cipher.doFinal(data);

            // Prepend IV to ciphertext: [IV | ciphertext]
            byte[] result = new byte[IV_BYTES + ciphertext.length];
            System.arraycopy(iv, 0, result, 0, IV_BYTES);
            System.arraycopy(ciphertext, 0, result, IV_BYTES, ciphertext.length);
            return result;

        } catch (Exception e) {
            throw new EncryptionException(
                    "AES-GCM encryption failed: " + e.getMessage(),
                    EncryptionException.ENCRYPTION_FAILED, e
            );
        }
    }

    /**
     * Decrypts data produced by {@link #encrypt(byte[])}.
     *
     * <p>The GCM authentication tag is automatically verified during decryption.
     * If tampered, a {@code AEADBadTagException} is thrown and wrapped.
     *
     * @param data ciphertext with prepended IV
     * @return decrypted plaintext bytes
     */
    @Override
    public byte[] decrypt(byte[] data) {
        try {
            // Extract IV from the first 12 bytes
            byte[] iv         = Arrays.copyOfRange(data, 0, IV_BYTES);
            byte[] ciphertext = Arrays.copyOfRange(data, IV_BYTES, data.length);

            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_BITS, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec);

            return cipher.doFinal(ciphertext);

        } catch (Exception e) {
            throw new EncryptionException(
                    "AES-GCM decryption failed (possible tampering or wrong key): " + e.getMessage(),
                    EncryptionException.DECRYPTION_FAILED, e
            );
        }
    }

    @Override
    public String getAlgorithmName() {
        return "AES";
    }

    /**
     * Returns the raw AES key bytes (for secure storage alongside the file).
     *
     * @return 32-byte AES key
     */
    public byte[] getKeyBytes() {
        return secretKey.getEncoded();
    }

    // ------------------------------------------------------------------ //
    //  Key generation
    // ------------------------------------------------------------------ //

    private static SecretKey generateKey() {
        try {
            KeyGenerator keyGen = KeyGenerator.getInstance(ALGORITHM);
            keyGen.init(KEY_SIZE_BITS, new SecureRandom());
            return keyGen.generateKey();
        } catch (NoSuchAlgorithmException e) {
            throw new EncryptionException("AES not available on this JVM", EncryptionException.ALGORITHM_NOT_FOUND, e);
        }
    }
}
