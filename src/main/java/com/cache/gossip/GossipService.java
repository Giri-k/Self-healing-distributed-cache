package com.cache.gossip;

import com.cache.cluster.ClusterService;
import lombok.Getter;
import com.cache.config.NodeConfig;
import com.cache.grpc.generated.*;
import com.cache.gossip.MembershipList.NodeStatus;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import com.cache.cluster.RebalanceService;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
public class GossipService {

    private static final Logger log = LoggerFactory.getLogger(GossipService.class);

    @Getter
    private final MembershipList membershipList;
    private final ClusterService clusterService;
    private final NodeConfig config;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final RebalanceService rebalanceService;

    public GossipService(NodeConfig config, ClusterService clusterService, RebalanceService rebalanceService) {
        this.config = config;
        this.clusterService = clusterService;
        this.membershipList = new MembershipList(config.getNodeId());
        this.rebalanceService = rebalanceService;
    }

    @PostConstruct
    public void start() {
        for (String peerId : config.getPeers().keySet()) {
            membershipList.addMember(peerId);
        }
        log.info("Gossip started for node {}, tracking {} peers",
            config.getNodeId(), config.getPeers().size());

        scheduler.scheduleAtFixedRate(this::gossipRound,
            3000, config.getPingIntervalMs(), TimeUnit.MILLISECONDS);

        scheduler.scheduleAtFixedRate(this::checkSuspectedNodes,
            4000, 1000, TimeUnit.MILLISECONDS);
    }

    private void gossipRound() {
        String target = membershipList.getRandomAliveNode();
        if(target == null)
            return;
        
        boolean alive = directPing(target);

        if(alive){
            handleAliveResponse(target);
            return;
        }
        log.debug("Direct ping to {} failed, trying indirect", target);
        boolean indirectAlive = indirectPing(target);

        if(indirectAlive){
            handleAliveResponse(target);
        }
        else{
            membershipList.markSuspected(target);
        }
    }

    private void handleAliveResponse(String nodeId) {
        MembershipList.NodeState prevState = membershipList.getState(nodeId);
        membershipList.markAlive(nodeId);

        if (prevState == MembershipList.NodeState.DEAD) {
            onNodeAlive(nodeId);
        }
    }

    private boolean directPing(String nodeId) {
        try {
            CacheServiceGrpc.CacheServiceBlockingStub stub =
                clusterService.getStubs().get(nodeId);
            if (stub == null) return false;

            PingResponse resp = stub
                .withDeadlineAfter(config.getPingTimeoutMs(), TimeUnit.MILLISECONDS)
                .ping(PingRequest.newBuilder()
                    .setSenderId(config.getNodeId())
                    .build());
            return resp.getAlive();
        } catch (Exception e) {
            return false;
        }
    }

    private boolean indirectPing(String targetId) {
        List<String> helpers = membershipList.getAliveMembers();
        helpers.remove(targetId);

        for (String helperId : helpers) {
            try {
                CacheServiceGrpc.CacheServiceBlockingStub stub =
                    clusterService.getStubs().get(helperId);
                if (stub == null) continue;

                IndirectPingResponse resp = stub
                    .withDeadlineAfter(config.getPingTimeoutMs() * 2, TimeUnit.MILLISECONDS)
                    .indirectPing(IndirectPingRequest.newBuilder()
                        .setTargetId(targetId)
                        .setTargetAddress(config.getPeers().get(targetId))
                        .build());

                if (resp.getAlive()) return true;
            } catch (Exception e) {
                log.debug("Helper {} could not reach {}", helperId, targetId);
            }
        }
        return false;
    }

    private void checkSuspectedNodes() {
        for (String nodeId : membershipList.getSuspectedMembers()) {
            NodeStatus status = membershipList.getStatus(nodeId);
            if (status == null) continue;

            long elapsed = System.currentTimeMillis() - status.getLastUpdated();
            if (elapsed > config.getSuspectTimeoutMs()) {
                membershipList.markDead(nodeId);
                onNodeDead(nodeId);
            }
        }
    }

    private void onNodeDead(String nodeId) {
        log.warn("Removing dead node {} from ring", nodeId);
        clusterService.removeNode(nodeId);
        rebalanceService.onNodeLeft(nodeId);
    }

    private void onNodeAlive(String nodeId) {
        log.info("Node {} rejoined, adding to ring", nodeId);
        clusterService.addNode(nodeId);
        rebalanceService.onNodeJoined(nodeId);
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdown();
    }
}
