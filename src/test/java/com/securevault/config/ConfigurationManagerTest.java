package com.securevault.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ConfigurationManagerTest {

    private ConfigurationManager config;

    @BeforeEach
    public void setUp() {
        config = ConfigurationManager.getInstance();
    }

    @Test
    public void testGetProperties() {
        assertNotNull(config.getDbUrl());
        assertNotNull(config.getDbUser());
        
        // Assert defaults or loaded values from config.properties
        assertTrue(config.getThreadPoolSize() > 0);
        assertTrue(config.getCacheSize() > 0);
        assertTrue(config.getBufferSize() > 0);
        assertTrue(config.getNioThresholdBytes() > 0);
        
        String defaultAlgo = config.getDefaultAlgorithm();
        assertTrue("AES".equals(defaultAlgo) || "XOR".equals(defaultAlgo) || "CAESAR".equals(defaultAlgo));
    }

    @Test
    public void testSetPropertyInMemory() {
        String testKey = "test.property.temp.key";
        String testValue = "temp_value_123";

        // Initially should be null or default
        String initial = config.getString(testKey, "default_val");
        assertEquals("default_val", initial);

        // Update value
        config.setProperty(testKey, testValue);

        // Should retrieve new value
        assertEquals(testValue, config.getString(testKey, "default_val"));
    }
}
