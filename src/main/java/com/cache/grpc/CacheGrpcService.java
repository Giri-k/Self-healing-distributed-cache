package com.cache.grpc;

import com.cache.grpc.generated.*;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;

@GrpcService
public class CacheGrpcService extends CacheServiceGrpc.CacheServiceImplBase {

    @Override
    public void get(GetRequest request, StreamObserver<GetResponse> responseObserver) {
        responseObserver.onNext(
            GetResponse
                .newBuilder()
                .setValue("Hello, " + request.getKey())
                .build()
            );
        responseObserver.onCompleted();
    }

    @Override
    public void set(SetRequest request, StreamObserver<SetResponse> responseObserver) {
        responseObserver.onNext(
            SetResponse
                .newBuilder()
                .setSuccess(true)
                .build()
            );
        responseObserver.onCompleted();
    }

    @Override
    public void delete(DeleteRequest request, StreamObserver<DeleteResponse> responseObserver) {
        responseObserver.onNext(
            DeleteResponse
                .newBuilder()
                .setSuccess(true)
                .build()
            );
        responseObserver.onCompleted();
    }
}