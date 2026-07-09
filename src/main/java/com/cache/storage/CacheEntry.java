package com.cache.storage;

public class CacheEntry {
    private final String value;
    private final long version;
    private final long expiresAt;

    public CacheEntry(String value, long version, long expiresAt) {
        this.value = value;
        this.version = version;
        this.expiresAt = expiresAt;
    }

    public String getValue() { return value; }
    public long getVersion() { return version; }
    public long getExpiresAt() { return expiresAt; }

    public boolean isExpired() {
        return expiresAt > 0 && System.currentTimeMillis() > expiresAt;
    }
}
