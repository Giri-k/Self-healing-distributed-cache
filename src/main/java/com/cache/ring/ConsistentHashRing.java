package com.cache.ring;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.TreeMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class ConsistentHashRing {

    private final TreeMap<Integer, String> ring;
    private final int virtualNodes;

    public ConsistentHashRing(int virtualNodes) {
        this.ring = new TreeMap<>();
        this.virtualNodes = virtualNodes;
    }

    public void addNode(String nodeId) {
        // TODO: loop 0 to virtualNodes-1, hash(nodeId + "-vn" + i), put in ring
        for(int i=0; i<virtualNodes; i++){
            ring.put(hash(nodeId+"-vn"+i), nodeId);
        }
    }

    public void removeNode(String nodeId) {
        // TODO: loop 0 to virtualNodes-1, hash(nodeId + "-vn" + i), remove from ring
        for(int i=0; i<virtualNodes; i++)
            ring.remove(hash(nodeId+"-vn"+i));
    }

    public String getNode(String key) {
        // TODO: hash the key, use ring.ceilingEntry(hash)
        //       if null, wrap around with ring.firstEntry()
        //       return the node ID
        if(getSize()==0)
            return null;
        
        Map.Entry<Integer, String> entry = ring.ceilingEntry(hash(key));
        if(entry==null)
            return ring.firstEntry().getValue();
        return entry.getValue();
    }

    public List<String> getNodes(String key, int count) {
        // TODO: collect 'count' distinct physical nodes
        //       starting from getNode(key), walking clockwise
        List<String> nodes = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        if(ring.isEmpty())
            return nodes;
        int keyHash = hash(key);

        //walk clockwise
        for(String nodeId : ring.tailMap(keyHash, true).values()){
            if(seen.add(nodeId)){
                nodes.add(nodeId);
                if(nodes.size() == count)
                    return nodes;
            }
        }

        // from beginning
        for(String nodeId : ring.values()){
            if(seen.add(nodeId)){
                nodes.add(nodeId);
                if(nodes.size()==count)
                    return nodes;
            }
        }

        return nodes;
        
    }

    public int getSize() {
        return new HashSet<>(ring.values()).size();
    }

    private int hash(String key) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest(key.getBytes());
            return ((digest[0] & 0xFF) << 24)
                 | ((digest[1] & 0xFF) << 16)
                 | ((digest[2] & 0xFF) << 8)
                 | (digest[3] & 0xFF);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
