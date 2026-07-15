package com.cache.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.Map;
import java.util.HashMap;

@Component
public class StorageEngine {
    private static final Logger log = LoggerFactory.getLogger(StorageEngine.class);

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

    public void setReplica(String key, String value, long version, long expiresAt){
        CacheEntry existing = store.get(key);
        if(existing==null || version>=existing.getVersion())
            store.put(key, new CacheEntry(value, version, expiresAt));
    }

    public Map<String, CacheEntry> getAllEntries() {
        return new HashMap<>(store);
    }


    @PostConstruct
    public void startCleanup() {
        log.info("Starting background expiry sweep (every 10s)");
        cleanupExecutor.scheduleAtFixedRate(() -> {
            int before = store.size();
            store.entrySet().removeIf(e -> e.getValue().isExpired());
            int expired = before - store.size();
            if (expired > 0) {
                log.info("Sweep removed {} expired keys, {} remaining", expired, store.size());
            }
        }, 10, 10, TimeUnit.SECONDS);
    }

    @PreDestroy
    public void shutdown() {
        cleanupExecutor.shutdown();
    }
}
