package com.securevault.auth;

import com.securevault.exceptions.AuthenticationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class PasswordHasherTest {

    private PasswordHasher hasher;

    @BeforeEach
    public void setUp() {
        // Uses config.properties iterations (e.g. 310,000) or fallback defaults
        hasher = new PasswordHasher();
    }

    @Test
    public void testHashAndVerify() {
        String password = "StrongPassword123!";
        String salt = hasher.generateSalt();
        assertNotNull(salt);
        assertFalse(salt.isEmpty());

        String hash = hasher.hash(password, salt);
        assertNotNull(hash);
        assertFalse(hash.isEmpty());

        // Verify correct password
        assertTrue(hasher.verify(password, salt, hash));

        // Verify incorrect password
        assertFalse(hasher.verify("WrongPassword123!", salt, hash));
    }

    @Test
    public void testKeyDerivation() {
        char[] password = "StrongPassword123!".toCharArray();
        String salt = hasher.generateSalt();

        byte[] key1 = hasher.deriveKey(password, salt);
        byte[] key2 = hasher.deriveKey("StrongPassword123!".toCharArray(), salt);

        assertNotNull(key1);
        assertEquals(32, key1.length); // 256 bits = 32 bytes
        assertArrayEquals(key1, key2);

        // Different password should derive different key
        byte[] key3 = hasher.deriveKey("DifferentPassword123!".toCharArray(), salt);
        assertFalse(java.util.Arrays.equals(key1, key3));
    }

    @Test
    public void testPasswordStrengthValidation() {
        // Valid password
        assertDoesNotThrow(() -> hasher.validateStrength("StrongPassword123!"));

        // Too short
        AuthenticationException ex1 = assertThrows(AuthenticationException.class, 
                () -> hasher.validateStrength("Short1!"));
        assertTrue(ex1.getMessage().contains("at least 8 characters"));

        // No uppercase
        AuthenticationException ex2 = assertThrows(AuthenticationException.class, 
                () -> hasher.validateStrength("nouppercase123!"));
        assertTrue(ex2.getMessage().contains("uppercase"));

        // No digit/special
        AuthenticationException ex3 = assertThrows(AuthenticationException.class, 
                () -> hasher.validateStrength("NoSpecialOrDigit"));
        assertTrue(ex3.getMessage().contains("uppercase letter, one digit, and one special character"));
    }
}
