package com.cache.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.HashMap;

@Component
@Getter
public class NodeConfig {

    @Value("${cache.node-id}")
    private String nodeId;

    @Value("${cache.peers:}")
    private String peersRaw;

    @Value("${cache.virtual-nodes:150}")
    private int virtualNodes;

    @Value("${cache.replication-factor:2}")
    private int replicationFactor;

    @Value("${cache.ping-interval-ms:1000}")
    private long pingIntervalMs;

    @Value("${cache.ping-timeout-ms:500}")
    private long pingTimeoutMs;

    @Value("${cache.suspect-timeout-ms:5000}")
    private long suspectTimeoutMs;

    public Map<String, String> getPeers(){
        Map<String, String> peers = new HashMap<>();
        if(peersRaw == null || peersRaw.isEmpty())
            return peers;
        for(String peer : peersRaw.split(",")){
            String[] parts = peer.split(":", 2);
            peers.put(parts[0], parts[1]);
        }
        return peers;
    }
}
