package com.cache.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StorageEngineTest {

    private StorageEngine storageEngine;

    @BeforeEach
    void setUp() {
        storageEngine = new StorageEngine();
    }

    @Test
    void setAndGet() {
        storageEngine.set("k1", "v1", 0);
        CacheEntry entry = storageEngine.get("k1");
        assertNotNull(entry);
        assertEquals("v1", entry.getValue());
    }

    @Test
    void getMissingKey() {
        assertNull(storageEngine.get("missing"));
    }

    @Test
    void deleteKey() {
        storageEngine.set("k1", "v1", 0);
        assertTrue(storageEngine.delete("k1"));
        assertNull(storageEngine.get("k1"));
    }

    @Test
    void deleteMissingKey() {
        assertFalse(storageEngine.delete("nope"));
    }

    @Test
    void ttlExpiry() throws InterruptedException {
        storageEngine.set("temp", "gone-soon", 1);
        assertNotNull(storageEngine.get("temp"));
        Thread.sleep(1500);
        assertNull(storageEngine.get("temp"));
    }

    @Test
    void versionIncrements() {
        storageEngine.set("k1", "v1", 0);
        assertEquals(1, storageEngine.get("k1").getVersion());
        storageEngine.set("k1", "v2", 0);
        assertEquals(2, storageEngine.get("k1").getVersion());
        assertEquals("v2", storageEngine.get("k1").getValue());
    }
}
