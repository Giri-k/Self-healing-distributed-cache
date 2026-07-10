package com.cache.cluster;

import com.cache.ring.ConsistentHashRing;
import com.cache.config.NodeConfig;
import com.cache.storage.StorageEngine;
import com.cache.storage.CacheEntry;
import com.cache.grpc.generated.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.HashMap;

@Component
public class ClusterService {

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
        // Add this node to the ring
        ring.addNode(config.getNodeId());

        // Add all peers to the ring and create gRPC client stubs
        for (Map.Entry<String, String> peer : config.getPeers().entrySet()) {
            String peerId = peer.getKey();
            String address = peer.getValue();

            ring.addNode(peerId);

            // create a ManagedChannel to 'address'
            // create a CacheServiceBlockingStub from that channel
            // put the stub in the stubs map with peerId as key
            ManagedChannel channel = ManagedChannelBuilder.forTarget(address)
                .usePlaintext()
                .build();
            stubs.put(peerId, CacheServiceGrpc.newBlockingStub(channel));
        }
    }

    public CacheEntry get(String key) {
        String owner = ring.getNode(key);
        if (owner.equals(config.getNodeId())) {
            return storageEngine.get(key);
        } else {
            // TODO: forward via gRPC stub
            GetResponse resp = stubs.get(owner).get(
                GetRequest.newBuilder().setKey(key).build()
            );

            if(resp.getFound())
                return new CacheEntry(resp.getValue(), 0, 0);

            return null;
        }
    }

    public void set(String key, String value, long ttlSeconds) {
        String owner = ring.getNode(key);
        if (owner.equals(config.getNodeId())) {
            // handle locally
            storageEngine.set(key, value, ttlSeconds);
        } else {
            //  forward via gRPC stub
            stubs.get(owner).set(
                SetRequest.newBuilder().setKey(key).setValue(value).setTtlSeconds(ttlSeconds).build()
            );
        }
    }

    public boolean delete(String key) {
        String owner = ring.getNode(key);
        if (owner.equals(config.getNodeId())) {
            // TODO: handle locally
            return storageEngine.delete(key);
        } else {
            // TODO: forward via gRPC stub
            DeleteResponse resp = stubs.get(owner).delete(
                DeleteRequest.newBuilder().setKey(key).build()
            );
            return resp.getSuccess();
        }
    }
}
