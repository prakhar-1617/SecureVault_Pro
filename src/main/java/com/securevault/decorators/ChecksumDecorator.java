package com.securevault.decorators;

import com.securevault.encryption.EncryptionStrategy;
import com.securevault.exceptions.IntegrityException;
import com.securevault.util.HashUtil;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Decorator that appends a SHA-256 checksum to encrypted data and
 * verifies it on decryption.
 *
 * <p><b>Encryption output format:</b>
 * {@code [ciphertext (N bytes)][checksum (64 ASCII bytes = 64 bytes)]}
 *
 * <p><b>Why append checksum after encryption?</b>
 * The checksum protects the ciphertext (not the plaintext). If the
 * ciphertext is tampered with on disk, decryption fails fast with a
 * clear integrity error — even before AES-GCM has a chance to fail.
 *
 * <p>Note: AES-GCM already provides authenticated encryption via its
 * GCM tag. This {@code ChecksumDecorator} adds an outer layer, useful
 * when the pipeline uses XOR or Caesar (which don't authenticate).
 *
 * @author SecureVault Pro
 * @version 1.0.0
 */
public class ChecksumDecorator extends EncryptionDecorator {

    /** SHA-256 hex string length — always 64 ASCII characters. */
    private static final int CHECKSUM_LENGTH = 64;

    public ChecksumDecorator(EncryptionStrategy inner) {
        super(inner);
    }

    /**
     * Encrypts via the inner strategy, then appends a 64-byte SHA-256 checksum.
     */
    @Override
    public byte[] encrypt(byte[] data) {
        byte[] encrypted = inner.encrypt(data);
        String checksum  = HashUtil.hash(encrypted);

        // Append checksum bytes to end of ciphertext
        byte[] checksumBytes = checksum.getBytes(StandardCharsets.US_ASCII);
        byte[] result = new byte[encrypted.length + CHECKSUM_LENGTH];
        System.arraycopy(encrypted,     0, result, 0,                encrypted.length);
        System.arraycopy(checksumBytes, 0, result, encrypted.length, CHECKSUM_LENGTH);
        return result;
    }

    /**
     * Strips the appended checksum, verifies it, then decrypts via the inner strategy.
     *
     * @throws IntegrityException if the checksum does not match (tampering detected)
     */
    @Override
    public byte[] decrypt(byte[] data) {
        if (data.length < CHECKSUM_LENGTH) {
            throw new IntegrityException("Data too short to contain checksum", IntegrityException.CHECKSUM_MISSING);
        }

        // Split: ciphertext | checksum
        int    cipherLen      = data.length - CHECKSUM_LENGTH;
        byte[] ciphertext     = Arrays.copyOfRange(data, 0, cipherLen);
        byte[] storedChecksum = Arrays.copyOfRange(data, cipherLen, data.length);
        String storedHex      = new String(storedChecksum, StandardCharsets.US_ASCII);

        // Verify
        if (!HashUtil.verify(ciphertext, storedHex)) {
            throw new IntegrityException(
                    "Checksum mismatch — file may have been tampered with!",
                    IntegrityException.CHECKSUM_MISMATCH
            );
        }

        return inner.decrypt(ciphertext);
    }

    @Override
    public String getAlgorithmName() {
        return inner.getAlgorithmName() + "+CHECKSUM";
    }
}
