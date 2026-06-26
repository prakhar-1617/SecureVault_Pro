package com.securevault.cache;

import com.securevault.config.ConfigurationManager;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Generic LRU (Least Recently Used) cache backed by {@link LinkedHashMap}
 * with access-order mode.
 *
 * <p><b>DSA Usage:</b> {@link LinkedHashMap} with {@code accessOrder=true}
 * maintains insertion order by default, but when access-ordered, it moves
 * accessed entries to the tail. The eldest entry (LRU) is always at the head.
 * Overriding {@link #removeEldestEntry} evicts it automatically when capacity
 * is exceeded — O(1) amortised for all operations.
 *
 * <p><b>What is cached?</b> File metadata keyed by <b>content hash</b>
 * (not filename). This means:
 * <ul>
 *   <li>{@code resume.pdf} and {@code resume_copy.pdf} with identical content → same cache entry</li>
 *   <li>Renaming a file doesn't invalidate the cache</li>
 *   <li>Modified content produces a different hash → cache miss → re-encrypt</li>
 * </ul>
 *
 * <p><b>Thread Safety:</b> {@link ReentrantReadWriteLock} allows concurrent reads
 * while serialising writes. This is more efficient than full synchronization for
 * read-heavy workloads.
 *
 * @param <K> cache key type (typically String — the SHA-256 file hash)
 * @param <V> cache value type (typically {@link CacheEntry} with metadata)
 * @author SecureVault Pro
 * @version 1.0.0
 */
public class LRUCache<K, V> {

    // ------------------------------------------------------------------ //
    //  Internal LinkedHashMap with LRU eviction
    // ------------------------------------------------------------------ //

    private final int capacity;

    private final LinkedHashMap<K, V> cache;

    // Read-write lock: multiple concurrent reads, exclusive writes
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final ReentrantReadWriteLock.ReadLock  readLock  = lock.readLock();
    private final ReentrantReadWriteLock.WriteLock writeLock = lock.writeLock();

    // Hit/miss tracking for interview demonstrations
    private long hits   = 0;
    private long misses = 0;

    // ------------------------------------------------------------------ //
    //  Constructor
    // ------------------------------------------------------------------ //

    /**
     * Creates an LRU cache with the given capacity.
     *
     * @param capacity maximum number of entries (LRU entry evicted when exceeded)
     */
    public LRUCache(int capacity) {
        this.capacity = capacity;
        this.cache = new LinkedHashMap<>(capacity, 0.75f, /* accessOrder= */ true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                // Evict the LRU entry when size exceeds capacity
                return size() > capacity;
            }
        };
    }

    /**
     * Creates an LRU cache using capacity from {@link ConfigurationManager}.
     */
    public LRUCache() {
        this(ConfigurationManager.getInstance().getCacheSize());
    }

    // ------------------------------------------------------------------ //
    //  Core operations
    // ------------------------------------------------------------------ //

    /**
     * Stores a key-value pair in the cache.
     *
     * <p>If capacity is exceeded, the LRU (least recently accessed) entry
     * is automatically evicted by {@link LinkedHashMap#removeEldestEntry}.
     *
     * @param key   cache key
     * @param value value to cache
     */
    public void put(K key, V value) {
        writeLock.lock();
        try {
            cache.put(key, value);
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Retrieves a value from the cache, recording a hit or miss.
     *
     * <p>A cache hit moves the entry to the most-recently-used position
     * (handled internally by {@link LinkedHashMap} in access-order mode).
     *
     * @param key the lookup key
     * @return the cached value, or {@code null} if not present (cache miss)
     */
    public V get(K key) {
        writeLock.lock();   // Write lock because LinkedHashMap.get() modifies order
        try {
            V value = cache.get(key);
            if (value != null) hits++;
            else               misses++;
            return value;
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Returns {@code true} if the key is present in the cache.
     */
    public boolean containsKey(K key) {
        readLock.lock();
        try {
            return cache.containsKey(key);
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Removes a specific entry from the cache.
     *
     * @param key the key to evict
     */
    public void evict(K key) {
        writeLock.lock();
        try {
            cache.remove(key);
        } finally {
            writeLock.unlock();
        }
    }

    /** Clears all entries. */
    public void clear() {
        writeLock.lock();
        try {
            cache.clear();
            hits   = 0;
            misses = 0;
        } finally {
            writeLock.unlock();
        }
    }

    // ------------------------------------------------------------------ //
    //  Metrics
    // ------------------------------------------------------------------ //

    public int  getSize()     { readLock.lock(); try { return cache.size(); } finally { readLock.unlock(); } }
    public int  getCapacity() { return capacity; }
    public long getHits()     { return hits;     }
    public long getMisses()   { return misses;   }

    /**
     * Returns the cache hit ratio as a percentage.
     *
     * @return hit ratio 0.0–100.0
     */
    public double getHitRatio() {
        long total = hits + misses;
        return total == 0 ? 0.0 : (hits * 100.0) / total;
    }

    @Override
    public String toString() {
        return String.format("LRUCache[size=%d/%d, hits=%d, misses=%d, ratio=%.1f%%]",
                getSize(), capacity, hits, misses, getHitRatio());
    }
}
