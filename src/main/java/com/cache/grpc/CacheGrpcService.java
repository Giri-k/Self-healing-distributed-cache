package com.cache.grpc;

import com.cache.grpc.generated.*;
import com.cache.storage.CacheEntry;
import com.cache.storage.StorageEngine;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;

@GrpcService
public class CacheGrpcService extends CacheServiceGrpc.CacheServiceImplBase {

    private final StorageEngine storageEngine;

    public CacheGrpcService(StorageEngine storageEngine) {
        this.storageEngine = storageEngine;
    }

    @Override
    public void get(GetRequest request, StreamObserver<GetResponse> responseObserver) {
        CacheEntry entry = storageEngine.get(request.getKey());

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
        storageEngine.set(request.getKey(), request.getValue(), request.getTtlSeconds());

        SetResponse response = SetResponse.newBuilder()
            .setSuccess(true)
            .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void delete(DeleteRequest request, StreamObserver<DeleteResponse> responseObserver) {
        boolean deleted = storageEngine.delete(request.getKey());

        DeleteResponse response = DeleteResponse.newBuilder()
            .setSuccess(deleted)
            .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}
