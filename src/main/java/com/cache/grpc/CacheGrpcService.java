package com.cache.grpc;

import com.cache.grpc.generated.*;
import com.cache.storage.CacheEntry;
//import com.cache.storage.StorageEngine;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import com.cache.cluster.ClusterService;

@GrpcService
public class CacheGrpcService extends CacheServiceGrpc.CacheServiceImplBase {

    // private final StorageEngine storageEngine;

    private final ClusterService clusterService;

    public CacheGrpcService(ClusterService clusterService) {
        this.clusterService = clusterService;
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
}
