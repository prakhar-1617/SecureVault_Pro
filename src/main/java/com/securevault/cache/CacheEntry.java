package com.securevault.cache;

/**
 * Value object stored in {@link LRUCache}.
 *
 * <p>Caches file <em>metadata</em> (not the encrypted bytes themselves)
 * to avoid loading large byte arrays into memory.
 *
 * <p><b>Key:</b> SHA-256 hash of the original file content.
 * Two files with identical content share the same cache entry.
 *
 * @author SecureVault Pro
 * @version 1.0.0
 */
public record CacheEntry(
        String  encryptedPath,    // Path to the encrypted file on disk
        String  algorithm,        // Encryption algorithm used
        String  checksum,         // SHA-256 of the original file
        long    fileSize,         // Original file size in bytes
        long    cachedAtMs        // System.currentTimeMillis() when cached
) {
    /**
     * Returns the age of this cache entry in milliseconds.
     */
    public long ageMs() {
        return System.currentTimeMillis() - cachedAtMs;
    }

    @Override
    public String toString() {
        return String.format("CacheEntry[algo=%s, path=%s, size=%d bytes, age=%dms]",
                algorithm, encryptedPath, fileSize, ageMs());
    }
}
