package com.cache.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.HashMap;

@Component
public class NodeConfig {

    @Value("${cache.node-id}")
    private String nodeId;

    @Value("${cache.peers:}")
    private String peersRaw;

    @Value("${cache.virtual-nodes:150}")
    private int virtualNodes;

    public String getNodeId(){
        return nodeId;
    }

    public int getVirtualNodes(){
        return virtualNodes;
    }

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