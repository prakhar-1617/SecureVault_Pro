package com.securevault.encryption;

import com.securevault.exceptions.EncryptionException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

public class AESStrategyTest {

    @Test
    public void testEncryptAndDecrypt() {
        byte[] key = new byte[32]; // 256-bit key (all zeros for test)
        AESStrategy aes = new AESStrategy(key);

        String plaintext = "Secret Message payload to encrypt!";
        byte[] originalBytes = plaintext.getBytes(StandardCharsets.UTF_8);

        byte[] ciphertext = aes.encrypt(originalBytes);
        assertNotNull(ciphertext);
        // Ciphertext should contain: [12-byte IV][ciphertext][16-byte GCM tag], so it must be larger than originalBytes
        assertTrue(ciphertext.length > originalBytes.length);

        byte[] decryptedBytes = aes.decrypt(ciphertext);
        assertArrayEquals(originalBytes, decryptedBytes);
        assertEquals(plaintext, new String(decryptedBytes, StandardCharsets.UTF_8));
    }

    @Test
    public void testRandomIVUniqueness() {
        byte[] key = new byte[32];
        AESStrategy aes = new AESStrategy(key);

        byte[] data = "Duplicate Plaintext".getBytes(StandardCharsets.UTF_8);

        byte[] ciphertext1 = aes.encrypt(data);
        byte[] ciphertext2 = aes.encrypt(data);

        // Ciphertexts must be different even for the same plaintext due to random IV GCM mode
        assertFalse(Arrays.equals(ciphertext1, ciphertext2));
    }

    @Test
    public void testDecryptionFailureWithTampering() {
        byte[] key = new byte[32];
        AESStrategy aes = new AESStrategy(key);

        byte[] data = "Important Data".getBytes(StandardCharsets.UTF_8);
        byte[] ciphertext = aes.encrypt(data);

        // Tamper with the ciphertext (alter the very last byte)
        ciphertext[ciphertext.length - 1] ^= 0x01;

        // Decrypting tampered GCM ciphertext should fail with EncryptionException
        assertThrows(EncryptionException.class, () -> aes.decrypt(ciphertext));
    }

    @Test
    public void testInvalidKeySize() {
        byte[] invalidKey = new byte[16]; // 128-bit key (invalid for our AES-256 requirement)
        assertThrows(EncryptionException.class, () -> new AESStrategy(invalidKey));
    }
}
