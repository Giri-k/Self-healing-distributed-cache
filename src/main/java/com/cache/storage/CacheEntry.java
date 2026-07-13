package com.cache.storage;

import lombok.Getter;
import lombok.AllArgsConstructor;

@Getter
@AllArgsConstructor
public class CacheEntry {
    private final String value;
    private final long version;
    private final long expiresAt;

    public boolean isExpired() {
        return expiresAt > 0 && System.currentTimeMillis() > expiresAt;
    }
}
