package com.securevault.decorators;

import com.securevault.encryption.EncryptionStrategy;

/**
 * Decorator that reverses byte array order (demo / educational purposes).
 *
 * <p>Included to show the Decorator Pattern can chain arbitrary transformations.
 * This is NOT a security enhancement — it is purely demonstrative.
 *
 * @author SecureVault Pro
 * @version 1.0.0
 */
public class ReverseDecorator extends EncryptionDecorator {

    public ReverseDecorator(EncryptionStrategy inner) {
        super(inner);
    }

    @Override
    public byte[] encrypt(byte[] data) {
        return inner.encrypt(reverse(data));
    }

    @Override
    public byte[] decrypt(byte[] data) {
        return reverse(inner.decrypt(data));
    }

    @Override
    public String getAlgorithmName() {
        return "REVERSE+" + inner.getAlgorithmName();
    }

    private byte[] reverse(byte[] data) {
        byte[] result = new byte[data.length];
        for (int i = 0; i < data.length; i++) {
            result[i] = data[data.length - 1 - i];
        }
        return result;
    }
}
