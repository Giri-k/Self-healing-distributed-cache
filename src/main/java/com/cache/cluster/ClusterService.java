package com.cache.cluster;

import com.cache.ring.ConsistentHashRing;
import com.cache.config.NodeConfig;
import com.cache.storage.StorageEngine;
import com.cache.storage.CacheEntry;
import com.cache.grpc.generated.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.HashMap;
import java.util.List;

@Component
public class ClusterService {

    private static final Logger log = LoggerFactory.getLogger(ClusterService.class);

    private final ConsistentHashRing ring;
    private final NodeConfig config;
    private final StorageEngine storageEngine;
    private final Map<String, CacheServiceGrpc.CacheServiceBlockingStub> stubs = new HashMap<>();

    public ClusterService(NodeConfig config, StorageEngine storageEngine) {
        this.config = config;
        this.storageEngine = storageEngine;
        this.ring = new ConsistentHashRing(config.getVirtualNodes());
    }

    @PostConstruct
    public void init() {
        ring.addNode(config.getNodeId());
        log.info("Added self to ring: {}", config.getNodeId());

        for (Map.Entry<String, String> peer : config.getPeers().entrySet()) {
            String peerId = peer.getKey();
            String address = peer.getValue();

            ring.addNode(peerId);

            ManagedChannel channel = ManagedChannelBuilder.forTarget(address)
                .usePlaintext()
                .build();
            stubs.put(peerId, CacheServiceGrpc.newBlockingStub(channel));
            log.info("Connected to peer: {} at {}", peerId, address);
        }

        log.info("Cluster initialized with {} nodes", ring.getSize());
    }

    public CacheEntry get(String key) {
        String owner = ring.getNode(key);
        if (owner.equals(config.getNodeId())) {
            log.debug("GET key={} handled locally", key);
            return storageEngine.get(key);
        } else {
            log.debug("GET key={} forwarding to {}", key, owner);
            GetResponse resp = stubs.get(owner).get(
                GetRequest.newBuilder().setKey(key).build()
            );

            if(resp.getFound())
                return new CacheEntry(resp.getValue(), 0, 0);

            return null;
        }
    }

    public void set(String key, String value, long ttlSeconds) {
        List<String> owners = ring.getNodes(key, config.getReplicationFactor());
        String primary = owners.get(0);
        
        if (primary.equals(config.getNodeId())) {
            log.debug("SET key={} handled locally", key);
            storageEngine.set(key, value, ttlSeconds);
            CacheEntry entry = storageEngine.get(key);
            for(int i=1; i<owners.size(); i++){
                String replicateId = owners.get(i);
                replicateToNode(replicateId, key, entry);
            }
        } else {
            log.debug("SET key={} forwarding to {}", key, primary);
            stubs.get(primary).set(
                SetRequest.newBuilder().setKey(key).setValue(value).setTtlSeconds(ttlSeconds).build()
            );
        }
    }

    public boolean delete(String key) {
        List<String> owners = ring.getNodes(key, config.getReplicationFactor());
        String primary = owners.get(0);
        if (primary.equals(config.getNodeId())) {
            log.debug("DELETE key={} handled locally", key);
            boolean deleted = storageEngine.delete(key);

            for(int i=1; i<owners.size(); i++){
                String replicateId = owners.get(i);
                CacheServiceGrpc.CacheServiceBlockingStub stub = stubs.get(replicateId);
                if (stub != null) {
                    stub.delete(DeleteRequest.newBuilder()
                        .setKey(key).build());
                }
            }
            return deleted;
        } else {
            log.debug("DELETE key={} forwarding to {}", key, primary);
            DeleteResponse resp = stubs.get(primary).delete(
                DeleteRequest.newBuilder().setKey(key).build()
            );
            return resp.getSuccess();
        }
    }

    private void replicateToNode(String nodeId, String key, CacheEntry entry){
        CacheServiceGrpc.CacheServiceBlockingStub stub = stubs.get(nodeId);
        if(stub != null){
            stub.replicate(
                ReplicateRequest.newBuilder()
                    .setKey(key)
                    .setValue(entry.getValue())
                    .setVersion(entry.getVersion())
                    .setExpiresAt(entry.getExpiresAt())
                    .build()
            );
        }
    }


}
