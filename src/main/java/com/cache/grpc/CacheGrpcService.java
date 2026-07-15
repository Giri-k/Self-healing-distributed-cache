package com.cache.grpc;

import com.cache.grpc.generated.*;
import com.cache.storage.CacheEntry;
import com.cache.storage.StorageEngine;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import com.cache.cluster.ClusterService;
import com.cache.config.NodeConfig;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.util.concurrent.TimeUnit;

import java.util.Map;

@GrpcService
public class CacheGrpcService extends CacheServiceGrpc.CacheServiceImplBase {

    private final StorageEngine storageEngine;

    private final ClusterService clusterService;

    private final NodeConfig config;

    public CacheGrpcService(ClusterService clusterService, StorageEngine storageEngine, NodeConfig config) {
        this.clusterService = clusterService;
        this.storageEngine = storageEngine;
        this.config = config;
    }

    @Override
    public void get(GetRequest request, StreamObserver<GetResponse> responseObserver) {
        CacheEntry entry = clusterService.get(request.getKey());

        GetResponse response;
        if (entry == null) {
            response = GetResponse.newBuilder()
                .setFound(false)
                .build();
        } else {
            response = GetResponse.newBuilder()
                .setValue(entry.getValue())
                .setFound(true)
                .build();
        }

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void set(SetRequest request, StreamObserver<SetResponse> responseObserver) {
        clusterService.set(request.getKey(), request.getValue(), request.getTtlSeconds());

        SetResponse response = SetResponse.newBuilder()
            .setSuccess(true)
            .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void delete(DeleteRequest request, StreamObserver<DeleteResponse> responseObserver) {
        boolean deleted = clusterService.delete(request.getKey());

        DeleteResponse response = DeleteResponse.newBuilder()
            .setSuccess(deleted)
            .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void replicate(ReplicateRequest request, StreamObserver<ReplicateResponse> responseObserver){

        storageEngine.setReplica(
            request.getKey(),
            request.getValue(),
            request.getVersion(),
            request.getExpiresAt()
        );

        ReplicateResponse response = ReplicateResponse.newBuilder()
            .setSuccess(true)
            .build();
        
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void ping(PingRequest request,
                     StreamObserver<PingResponse> responseObserver) {
        PingResponse response = PingResponse.newBuilder()
            .setNodeId(config.getNodeId())
            .setAlive(true)
            .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void indirectPing(IndirectPingRequest request, StreamObserver<IndirectPingResponse> responseObserver) {

        boolean alive = false;
        try {
            // Create a temporary channel to the target
            ManagedChannel channel = ManagedChannelBuilder
                .forTarget(request.getTargetAddress())
                .usePlaintext()
                .build();
            CacheServiceGrpc.CacheServiceBlockingStub stub =
                CacheServiceGrpc.newBlockingStub(channel);

            PingResponse resp = stub
                .withDeadlineAfter(500, TimeUnit.MILLISECONDS)
                .ping(PingRequest.newBuilder()
                    .setSenderId(config.getNodeId())
                    .build());
            alive = resp.getAlive();
            channel.shutdown();
        } catch (Exception e) {
            alive = false;
        }

        IndirectPingResponse response = IndirectPingResponse
            .newBuilder()
            .setAlive(alive)
            .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void streamKeys(StreamKeysRequest request, StreamObserver<StreamKeysResponse> responseObserver) {

        Map<String, CacheEntry> allEntries = storageEngine.getAllEntries();

        for (Map.Entry<String, CacheEntry> entry : allEntries.entrySet()) {
            StreamKeysResponse response = StreamKeysResponse
                .newBuilder()
                .setKey(entry.getKey())
                .setValue(entry.getValue().getValue())
                .setVersion(entry.getValue().getVersion())
                .setExpiresAt(entry.getValue().getExpiresAt())
                .build();

            responseObserver.onNext(response);
        }

        responseObserver.onCompleted();
    }


}
