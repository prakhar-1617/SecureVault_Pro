package com.securevault.config;

import com.securevault.exceptions.ConfigurationException;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Singleton that reads {@code config.properties} exactly once at startup
 * and provides typed getters to all application modules.
 *
 * <p><b>Design Pattern:</b> Singleton — Thread-safe using double-checked locking.
 * The {@code volatile} keyword ensures that the JVM does not reorder instructions,
 * preventing a partially-constructed object from being returned to another thread.
 *
 * <p><b>Why Singleton?</b> Configuration is a shared, read-only resource. Making
 * it a Singleton avoids repeated file I/O and guarantees a single source of truth.
 *
 * <p><b>Interview talking point:</b>
 * "Why not a static class?" — A Singleton can be subclassed, mocked in tests,
 * and lazily initialized, which static classes cannot.
 *
 * @author SecureVault Pro
 * @version 1.0.0
 */
public final class ConfigurationManager {

    // ------------------------------------------------------------------ //
    //  Singleton boilerplate — double-checked locking
    // ------------------------------------------------------------------ //

    /** The single instance. volatile prevents instruction reordering. */
    private static volatile ConfigurationManager instance;

    /** Loaded properties. Populated once in the constructor. */
    private final Properties properties;

    /** Path to the properties file on the classpath. */
    private static final String CONFIG_FILE = "config.properties";

    /**
     * Private constructor. Reads and parses {@code config.properties}.
     *
     * @throws ConfigurationException if the file is missing or unreadable
     */
    private ConfigurationManager() {
        properties = new Properties();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(CONFIG_FILE)) {
            if (in == null) {
                throw new ConfigurationException(
                        "config.properties not found on classpath",
                        ConfigurationException.FILE_NOT_FOUND
                );
            }
            properties.load(in);
        } catch (IOException e) {
            throw new ConfigurationException(
                    "Failed to load config.properties: " + e.getMessage(),
                    ConfigurationException.FILE_NOT_FOUND,
                    e
            );
        }
    }

    /**
     * Returns the singleton instance, creating it on first call.
     *
     * <p>Thread-safe via double-checked locking: the {@code synchronized}
     * block is entered only when the instance hasn't been created yet,
     * avoiding synchronization overhead on every subsequent call.
     *
     * @return the single {@code ConfigurationManager} instance
     */
    public static ConfigurationManager getInstance() {
        if (instance == null) {                 // First check (no lock)
            synchronized (ConfigurationManager.class) {
                if (instance == null) {         // Second check (with lock)
                    instance = new ConfigurationManager();
                }
            }
        }
        return instance;
    }

    // ------------------------------------------------------------------ //
    //  Typed getters
    // ------------------------------------------------------------------ //

    /**
     * Returns a property value as a String.
     *
     * @param key          the property key
     * @param defaultValue value to return when the key is absent
     * @return the property value or {@code defaultValue}
     */
    public String getString(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }

    /**
     * Returns a required property value as a String.
     *
     * @param key the property key
     * @return the property value
     * @throws ConfigurationException if the key is absent
     */
    public String getRequiredString(String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new ConfigurationException(
                    "Required configuration key missing: " + key,
                    ConfigurationException.MISSING_KEY
            );
        }
        return value;
    }

    /**
     * Returns a property value as an integer.
     *
     * @param key          the property key
     * @param defaultValue fallback if key is absent or value is non-numeric
     * @return parsed integer or {@code defaultValue}
     */
    public int getInt(String key, int defaultValue) {
        String value = properties.getProperty(key);
        if (value == null) return defaultValue;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Returns a property value as a long.
     *
     * @param key          the property key
     * @param defaultValue fallback value
     * @return parsed long or {@code defaultValue}
     */
    public long getLong(String key, long defaultValue) {
        String value = properties.getProperty(key);
        if (value == null) return defaultValue;
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Returns a property value as a boolean.
     * Accepts "true"/"false" (case-insensitive).
     *
     * @param key          the property key
     * @param defaultValue fallback value
     * @return parsed boolean or {@code defaultValue}
     */
    public boolean getBoolean(String key, boolean defaultValue) {
        String value = properties.getProperty(key);
        if (value == null) return defaultValue;
        return Boolean.parseBoolean(value.trim());
    }

    // ------------------------------------------------------------------ //
    //  Convenience accessors for frequently-used settings
    // ------------------------------------------------------------------ //

    /** @return thread pool size (default 4) */
    public int getThreadPoolSize() {
        return getInt("thread.pool.size", 4);
    }

    /** @return LRU cache capacity (default 50) */
    public int getCacheSize() {
        return getInt("cache.size", 50);
    }

    /** @return default encryption algorithm name (default "AES") */
    public String getDefaultAlgorithm() {
        return getString("default.algorithm", "AES");
    }

    /** @return stream buffer size in bytes (default 4096) */
    public int getBufferSize() {
        return getInt("buffer.size", 4096);
    }

    /** @return NIO threshold in bytes (default 10 MB) */
    public long getNioThresholdBytes() {
        return getLong("nio.threshold.bytes", 10_485_760L);
    }

    /** @return JDBC connection URL */
    public String getDbUrl() {
        return getRequiredString("db.url");
    }

    /** @return database username */
    public String getDbUser() {
        return getString("db.user", "root");
    }

    /** @return database password */
    public String getDbPassword() {
        return getString("db.password", "");
    }

    /** @return PBKDF2 iteration count (default 310,000) */
    public int getPbkdf2Iterations() {
        return getInt("pbkdf2.iterations", 310_000);
    }

    /** @return PBKDF2 derived key length in bits (default 256) */
    public int getPbkdf2KeyLength() {
        return getInt("pbkdf2.key.length", 256);
    }

    /** @return maximum failed login attempts before lockout (default 5) */
    public int getMaxFailedAttempts() {
        return getInt("auth.max.failed.attempts", 5);
    }

    /** @return audit log flush interval in seconds (default 10) */
    public int getAuditFlushIntervalSeconds() {
        return getInt("audit.flush.interval.seconds", 10);
    }

    /** @return path to encrypted files directory */
    public String getEncryptedFilesDir() {
        return getString("encrypted.files.dir", "encrypted_files");
    }
}
