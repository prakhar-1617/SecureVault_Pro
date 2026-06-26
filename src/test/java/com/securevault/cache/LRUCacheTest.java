package com.securevault.cache;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class LRUCacheTest {

    @Test
    public void testBasicPutAndGet() {
        LRUCache<String, String> cache = new LRUCache<>(3);

        cache.put("key1", "val1");
        cache.put("key2", "val2");

        assertEquals("val1", cache.get("key1"));
        assertEquals("val2", cache.get("key2"));
        assertNull(cache.get("key3"));

        // Stats tracking
        assertEquals(2, cache.getHits());
        assertEquals(1, cache.getMisses());
        assertEquals(2, cache.getSize());
    }

    @Test
    public void testLRUEviction() {
        LRUCache<String, String> cache = new LRUCache<>(3);

        cache.put("k1", "v1");
        cache.put("k2", "v2");
        cache.put("k3", "v3");

        // Cache is at capacity (3 items)
        assertEquals(3, cache.getSize());

        // Add 4th item: should evict k1 (the eldest)
        cache.put("k4", "v4");

        assertNull(cache.get("k1")); // evicted
        assertEquals("v2", cache.get("k2"));
        assertEquals("v3", cache.get("k3"));
        assertEquals("v4", cache.get("k4"));
    }

    @Test
    public void testLRUAccessUpdatesOrder() {
        LRUCache<String, String> cache = new LRUCache<>(3);

        cache.put("k1", "v1");
        cache.put("k2", "v2");
        cache.put("k3", "v3");

        // Access k1, making it the most recently used (MRU)
        assertEquals("v1", cache.get("k1"));

        // Add k4: should evict k2 (since k1 was accessed, and k2 is now the eldest)
        cache.put("k4", "v4");

        assertNotNull(cache.get("k1")); // saved by access
        assertNull(cache.get("k2"));    // evicted
        assertNotNull(cache.get("k3"));
        assertNotNull(cache.get("k4"));
    }

    @Test
    public void testHitRatio() {
        LRUCache<String, String> cache = new LRUCache<>(5);

        // 0 operations: hit ratio 0.0
        assertEquals(0.0, cache.getHitRatio());

        cache.put("a", "1");
        
        cache.get("a"); // Hit 1
        cache.get("b"); // Miss 1
        cache.get("a"); // Hit 2
        cache.get("c"); // Miss 2

        assertEquals(2, cache.getHits());
        assertEquals(2, cache.getMisses());
        assertEquals(50.0, cache.getHitRatio()); // 2 hits out of 4 requests = 50%
    }
}
