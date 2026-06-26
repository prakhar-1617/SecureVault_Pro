package com.securevault.decorators;

import com.securevault.encryption.AESStrategy;
import com.securevault.encryption.EncryptionStrategy;

/**
 * Decorator that wraps an inner strategy with AES-256-GCM encryption.
 *
 * <p>This allows AES to be inserted into a pipeline at a specific position,
 * for example after compression and before checksum appending.
 *
 * <p><b>Typical pipeline:</b>
 * <pre>
 *   CompressionDecorator → AESDecorator → ChecksumDecorator
 * </pre>
 *
 * @author SecureVault Pro
 * @version 1.0.0
 */
public class AESDecorator extends EncryptionDecorator {

    private final AESStrategy aesStrategy;

    /**
     * Creates an AES decorator with a freshly generated AES key.
     *
     * @param inner the next stage in the pipeline
     */
    public AESDecorator(EncryptionStrategy inner) {
        super(inner);
        this.aesStrategy = new AESStrategy();
    }

    /**
     * Creates an AES decorator using an existing key (for decryption).
     *
     * @param inner    the next stage in the pipeline
     * @param keyBytes existing 32-byte AES key
     */
    public AESDecorator(EncryptionStrategy inner, byte[] keyBytes) {
        super(inner);
        this.aesStrategy = new AESStrategy(keyBytes);
    }

    /** Runs inner pipeline first, then applies AES-GCM encryption on top. */
    @Override
    public byte[] encrypt(byte[] data) {
        byte[] innerResult = inner.encrypt(data);
        return aesStrategy.encrypt(innerResult);
    }

    /** Decrypts AES first, then passes result to inner pipeline for decryption. */
    @Override
    public byte[] decrypt(byte[] data) {
        byte[] aesDecrypted = aesStrategy.decrypt(data);
        return inner.decrypt(aesDecrypted);
    }

    @Override
    public String getAlgorithmName() {
        return inner.getAlgorithmName() + "+AES";
    }

    /** Exposes the AES key so it can be stored alongside the encrypted file. */
    public byte[] getKeyBytes() {
        return aesStrategy.getKeyBytes();
    }
}
