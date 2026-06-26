package com.securevault.encryption;

import com.securevault.exceptions.EncryptionException;

/**
 * Factory that creates {@link EncryptionStrategy} instances by name.
 *
 * <p><b>Design Pattern:</b> Factory — centralizes object creation, decoupling
 * callers from concrete classes. Supports the Open-Closed Principle: adding
 * a new algorithm (e.g., RSA) requires only a new {@code case} here — the
 * storage service and UI code remain unchanged.
 *
 * <p><b>Interview Q:</b> "Why not just {@code new AESStrategy()} everywhere?"
 * — The Factory allows algorithm selection at runtime (from UI dropdown or
 * config file), enables future extension without modifying callers, and
 * provides a single place to validate algorithm names.
 *
 * @author SecureVault Pro
 * @version 1.0.0
 */
public final class EncryptionFactory {

    private EncryptionFactory() {}

    /**
     * Creates an {@link EncryptionStrategy} for the given algorithm name.
     *
     * <p>Supported names (case-insensitive): AES, XOR, CAESAR
     *
     * @param algorithmName the algorithm identifier
     * @return a new strategy instance
     * @throws EncryptionException with {@code ENC-001} if the name is unknown
     */
    public static EncryptionStrategy createStrategy(String algorithmName) {
        if (algorithmName == null) {
            throw new EncryptionException("Algorithm name cannot be null", EncryptionException.ALGORITHM_NOT_FOUND);
        }
        return switch (algorithmName.toUpperCase().trim()) {
            case "AES"    -> new AESStrategy();
            case "XOR"    -> new XORStrategy();
            case "CAESAR" -> new CaesarStrategy();
            default       -> throw new EncryptionException(
                    "Unknown algorithm: '" + algorithmName + "'. Supported: AES, XOR, CAESAR",
                    EncryptionException.ALGORITHM_NOT_FOUND
            );
        };
    }

    /**
     * Creates an {@link AESStrategy} initialized with an existing raw key.
     * Used when decrypting a file whose key is retrieved from storage.
     *
     * @param keyBytes the 32-byte AES key
     * @return AES strategy configured for decryption
     */
    public static AESStrategy createAESWithKey(byte[] keyBytes) {
        return new AESStrategy(keyBytes);
    }

    /**
     * Returns the names of all supported algorithms.
     *
     * @return array of supported algorithm names
     */
    public static String[] supportedAlgorithms() {
        return new String[]{"AES", "XOR", "CAESAR"};
    }
}
