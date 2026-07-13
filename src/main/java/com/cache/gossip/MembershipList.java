package com.cache.gossip;

import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ThreadLocalRandom;
import lombok.Getter;
import lombok.Setter;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Map;

public class MembershipList {

    private static final Logger log = LoggerFactory.getLogger(MembershipList.class);

    public enum NodeState {
        ALIVE, SUSPECTED, DEAD
    }

    @Getter
    @Setter
    @AllArgsConstructor
    public static class NodeStatus {
        private NodeState state;
        private long lastUpdated;
        private int incarnation;
    }

    private final ConcurrentHashMap<String, NodeStatus> members = new ConcurrentHashMap<>();
    private final String selfId;

    public MembershipList(String selfId) {
        this.selfId = selfId;
    }

    public void addMember(String nodeId) {
        members.put(nodeId, new NodeStatus(NodeState.ALIVE, System.currentTimeMillis(), 0));
    }

    public NodeState getState(String nodeId) {
        NodeStatus status = members.get(nodeId);
        if (status == null) return null;
        return status.getState();
    }

    public void markSuspected(String nodeId) {
        NodeStatus status = members.get(nodeId);
        if(status != null && status.getState()==NodeState.ALIVE){
            log.warn("Node {} suspected", nodeId);
            status.setState(NodeState.SUSPECTED);
            status.setLastUpdated(System.currentTimeMillis());
        }
        
    }

    public void markDead(String nodeId) {
        NodeStatus status = members.get(nodeId);
        if (status != null) {
            status.setState(NodeState.DEAD);
            status.setLastUpdated(System.currentTimeMillis());
            log.warn("Node {} confirmed dead", nodeId);
        }
    }

    public void markAlive(String nodeId) {
        NodeStatus status = members.get(nodeId);
        if (status != null && status.getState() != NodeState.ALIVE) {
            status.setState(NodeState.ALIVE);
            status.setLastUpdated(System.currentTimeMillis());
            log.info("Node {} is alive again", nodeId);
        }
    }

    public List<String> getAliveMembers() {
        List<String> alive = new ArrayList<>();
        for(Map.Entry<String, NodeStatus> entry : members.entrySet()){
            if(!entry.getKey().equals(selfId) && entry.getValue().getState() == NodeState.ALIVE)
                alive.add(entry.getKey());
        }
        return alive;
    }

    public List<String> getSuspectedMembers() {
        List<String> suspect = new ArrayList<>();
        for(Map.Entry<String, NodeStatus> entry : members.entrySet()){
            if(entry.getValue().getState() == NodeState.SUSPECTED)
                suspect.add(entry.getKey());
        }
        return suspect;
    }

    public String getRandomAliveNode() {
        List<String> alive = getAliveMembers();
        if(alive.isEmpty())
            return null;
        return alive.get(ThreadLocalRandom.current().nextInt(alive.size()));
        
    }

    public NodeStatus getStatus(String nodeId) {
        return members.get(nodeId);
    }
}
