package com.cache.storage;

import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
public class StorageEngine {
    private final ConcurrentHashMap<String, CacheEntry> store;
    private final ScheduledExecutorService cleanupExecutor;

    public StorageEngine() {
        store = new ConcurrentHashMap<>();
        cleanupExecutor = Executors.newSingleThreadScheduledExecutor();
    }

    public CacheEntry get(String key) {
        CacheEntry entry = store.get(key);
        if (entry == null) return null;
        if (entry.isExpired()) {
            store.remove(key);
            return null;
        }
        return entry;
    }

    public void set(String key, String value, long ttlSeconds) {
        long expiresAt = ttlSeconds > 0 ? System.currentTimeMillis() + (ttlSeconds * 1000) : 0;

        CacheEntry existing = store.get(key);
        long version = (existing != null) ? existing.getVersion() + 1 : 1;

        CacheEntry entry = new CacheEntry(value, version, expiresAt);
        store.put(key, entry);
    }

    public boolean delete(String key) {
        return store.remove(key) != null;
    }

    @PostConstruct
    public void startCleanup() {
        cleanupExecutor.scheduleAtFixedRate(
            () -> store.entrySet().removeIf(e -> e.getValue().isExpired()),
            10, 10, TimeUnit.SECONDS);
    }

    @PreDestroy
    public void shutdown() {
        cleanupExecutor.shutdown();
    }
}
