package com.securevault.encryption;

/**
 * XOR cipher encryption strategy — for Strategy Pattern demonstration only.
 *
 * <p><b>⚠ Security Warning:</b> XOR cipher is NOT cryptographically secure.
 * It is included solely to demonstrate the Strategy Pattern — the same
 * client code ({@link com.securevault.storage.FileStorageService}) works
 * identically with AES or XOR.
 *
 * <p><b>Interview answer:</b> "XOR and Caesar are here to show that the
 * Strategy Pattern makes algorithms truly interchangeable. All real file
 * encryption uses AES-256-GCM."
 *
 * <p><b>How XOR works:</b> Each byte {@code data[i]} is XOR-ed with
 * {@code key[i % key.length]}. The same operation both encrypts and decrypts
 * (XOR is its own inverse).
 *
 * @author SecureVault Pro
 * @version 1.0.0
 */
public class XORStrategy implements EncryptionStrategy {

    private static final byte[] DEFAULT_KEY = "SecureVaultXOR!".getBytes();
    private final byte[] key;

    /** Creates an XOR strategy with the default demonstration key. */
    public XORStrategy() {
        this.key = DEFAULT_KEY;
    }

    /** Creates an XOR strategy with a custom key. */
    public XORStrategy(byte[] key) {
        if (key == null || key.length == 0) {
            throw new IllegalArgumentException("XOR key must not be empty");
        }
        this.key = key;
    }

    @Override
    public byte[] encrypt(byte[] data) {
        return xor(data);
    }

    @Override
    public byte[] decrypt(byte[] data) {
        return xor(data);   // XOR is symmetric: encrypt == decrypt
    }

    @Override
    public String getAlgorithmName() {
        return "XOR";
    }

    private byte[] xor(byte[] data) {
        byte[] result = new byte[data.length];
        for (int i = 0; i < data.length; i++) {
            result[i] = (byte) (data[i] ^ key[i % key.length]);
        }
        return result;
    }
}
