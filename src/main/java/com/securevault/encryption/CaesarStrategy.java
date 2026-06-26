package com.securevault.encryption;

/**
 * Caesar cipher encryption strategy — byte-level shift cipher for
 * Strategy Pattern demonstration only.
 *
 * <p><b>⚠ Security Warning:</b> Caesar cipher is trivially breakable
 * (only 256 possible shifts for byte values). Included for educational
 * purposes and to demonstrate the Strategy Pattern.
 *
 * <p><b>How it works:</b> Each byte is shifted by {@code shift} positions
 * in the 0–255 byte space (wraps around using modulo 256 arithmetic).
 *
 * @author SecureVault Pro
 * @version 1.0.0
 */
public class CaesarStrategy implements EncryptionStrategy {

    private static final int DEFAULT_SHIFT = 13;  // ROT13 for bytes
    private final int shift;

    public CaesarStrategy() {
        this.shift = DEFAULT_SHIFT;
    }

    public CaesarStrategy(int shift) {
        this.shift = shift & 0xFF;  // Keep in [0, 255]
    }

    @Override
    public byte[] encrypt(byte[] data) {
        byte[] result = new byte[data.length];
        for (int i = 0; i < data.length; i++) {
            result[i] = (byte) ((data[i] & 0xFF) + shift);  // wrap-around via byte cast
        }
        return result;
    }

    @Override
    public byte[] decrypt(byte[] data) {
        byte[] result = new byte[data.length];
        for (int i = 0; i < data.length; i++) {
            result[i] = (byte) ((data[i] & 0xFF) - shift);
        }
        return result;
    }

    @Override
    public String getAlgorithmName() {
        return "CAESAR";
    }
}
