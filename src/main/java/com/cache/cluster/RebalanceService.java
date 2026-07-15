package com.cache.cluster;

import com.cache.config.NodeConfig;
import com.cache.storage.CacheEntry;
import com.cache.storage.StorageEngine;
import com.cache.grpc.generated.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class RebalanceService {

    private static final Logger log = LoggerFactory.getLogger(RebalanceService.class);

    private final ClusterService clusterService;
    private final StorageEngine storageEngine;
    private final NodeConfig config;

    public RebalanceService(@Lazy ClusterService clusterService,
                            StorageEngine storageEngine,
                            NodeConfig config) {
        this.clusterService = clusterService;
        this.storageEngine = storageEngine;
        this.config = config;
    }

    public void onNodeJoined(String nodeId) {
        Map<String, CacheEntry> allEntries = storageEngine.getAllEntries();
        int sent = 0;

        for (Map.Entry<String, CacheEntry> entry : allEntries.entrySet()) {
            String key = entry.getKey();
            CacheEntry cacheEntry = entry.getValue();

            if (cacheEntry.isExpired()) continue;

            String newOwner = clusterService.getRing().getNode(key);

            if (newOwner.equals(nodeId)) {
                sendKeyToNode(nodeId, key, cacheEntry);
                sent++;
            }
        }
        if (sent > 0) {
            log.info("Rebalanced {} keys to node {}", sent, nodeId);
        }
    }

    public void onNodeLeft(String nodeId) {
        log.info("Node {} left. Replicas will serve its keys until it returns.", nodeId);
    }

    private void sendKeyToNode(String nodeId, String key, CacheEntry entry) {
        CacheServiceGrpc.CacheServiceBlockingStub stub = clusterService.getStubs().get(nodeId);
        if (stub == null) return;

        stub.replicate(ReplicateRequest.newBuilder()
            .setKey(key)
            .setValue(entry.getValue())
            .setVersion(entry.getVersion())
            .setExpiresAt(entry.getExpiresAt())
            .build());
    }
}
