package com.securevault.encryption;

/**
 * Strategy interface for encryption algorithms.
 *
 * <p><b>Design Pattern:</b> Strategy — defines a family of algorithms,
 * encapsulates each one, and makes them interchangeable. The client
 * ({@link com.securevault.storage.FileStorageService}) works with this
 * interface and never knows which concrete algorithm is running.
 *
 * <p><b>Open-Closed Principle:</b> To add RSA, ChaCha20, or any future
 * algorithm, create a new class implementing this interface. Zero
 * changes to existing code required.
 *
 * <p><b>Interview Q:</b> "What if tomorrow a client wants RSA?"
 * Create {@code RSAStrategy implements EncryptionStrategy}. Done.
 * Register it in {@link EncryptionFactory}. Existing code is untouched.
 *
 * @author SecureVault Pro
 * @version 1.0.0
 */
public interface EncryptionStrategy {

    /**
     * Encrypts the given plaintext bytes.
     *
     * @param data the plaintext data
     * @return the encrypted (ciphertext) bytes
     */
    byte[] encrypt(byte[] data);

    /**
     * Decrypts the given ciphertext bytes.
     *
     * @param data the ciphertext data
     * @return the decrypted (plaintext) bytes
     */
    byte[] decrypt(byte[] data);

    /**
     * Returns the short algorithm name used for display and DB storage.
     *
     * @return algorithm name, e.g., "AES", "XOR", "CAESAR"
     */
    String getAlgorithmName();
}
