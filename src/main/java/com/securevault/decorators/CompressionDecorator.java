package com.securevault.decorators;

import com.securevault.encryption.EncryptionStrategy;
import com.securevault.exceptions.EncryptionException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

/**
 * Decorator that compresses data before encryption (on encrypt) and
 * decompresses after decryption (on decrypt).
 *
 * <p><b>Why compress before encrypting?</b>
 * <ol>
 *   <li><b>Size reduction:</b> Smaller ciphertext means faster transmission.</li>
 *   <li><b>Increased entropy:</b> Compressed data has higher entropy (more random-looking),
 *       which makes the encrypted output slightly harder to analyze.</li>
 *   <li><b>Order matters:</b> Compressing AFTER encryption is useless — encryption
 *       produces high-entropy data that compressors cannot reduce.</li>
 * </ol>
 *
 * <p>Uses Java's built-in {@link java.util.zip.Deflater} (ZLIB format) — no third-party libs.
 *
 * @author SecureVault Pro
 * @version 1.0.0
 */
public class CompressionDecorator extends EncryptionDecorator {

    public CompressionDecorator(EncryptionStrategy inner) {
        super(inner);
    }

    /**
     * Compresses {@code data}, then delegates to the inner strategy for encryption.
     */
    @Override
    public byte[] encrypt(byte[] data) {
        byte[] compressed = compress(data);
        return inner.encrypt(compressed);
    }

    /**
     * Decrypts via the inner strategy, then decompresses.
     */
    @Override
    public byte[] decrypt(byte[] data) {
        byte[] decrypted = inner.decrypt(data);
        return decompress(decrypted);
    }

    @Override
    public String getAlgorithmName() {
        return "COMPRESS+" + inner.getAlgorithmName();
    }

    // ------------------------------------------------------------------ //
    //  ZLIB compression helpers
    // ------------------------------------------------------------------ //

    private byte[] compress(byte[] data) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             DeflaterOutputStream deflater = new DeflaterOutputStream(baos)) {
            deflater.write(data);
            deflater.finish();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new EncryptionException("Compression failed: " + e.getMessage(),
                    EncryptionException.ENCRYPTION_FAILED, e);
        }
    }

    private byte[] decompress(byte[] data) {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(data);
             InflaterInputStream inflater = new InflaterInputStream(bais);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int n;
            while ((n = inflater.read(buffer)) != -1) {
                baos.write(buffer, 0, n);
            }
            return baos.toByteArray();
        } catch (IOException e) {
            throw new EncryptionException("Decompression failed: " + e.getMessage(),
                    EncryptionException.DECRYPTION_FAILED, e);
        }
    }
}
