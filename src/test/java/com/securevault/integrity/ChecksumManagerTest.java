package com.securevault.integrity;

import com.securevault.exceptions.IntegrityException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

public class ChecksumManagerTest {

    private ChecksumManager checksumManager;

    @BeforeEach
    public void setUp() {
        checksumManager = new ChecksumManager();
    }

    @Test
    public void testComputeAndVerify() {
        byte[] data = "Integrity test message payload".getBytes(StandardCharsets.UTF_8);

        String checksum = checksumManager.compute(data);
        assertNotNull(checksum);
        assertEquals(64, checksum.length()); // SHA-256 is 64 hex characters

        // Verify correct checksum
        assertDoesNotThrow(() -> checksumManager.verify(data, checksum));

        // Verify incorrect checksum throws IntegrityException
        assertThrows(IntegrityException.class, () -> checksumManager.verify(data, "invalid_checksum_string_123456789"));
        
        // Verify tampered data throws IntegrityException
        byte[] tamperedData = "integrity test message payload".getBytes(StandardCharsets.UTF_8); // changed first character casing
        assertThrows(IntegrityException.class, () -> checksumManager.verify(tamperedData, checksum));
    }
}
