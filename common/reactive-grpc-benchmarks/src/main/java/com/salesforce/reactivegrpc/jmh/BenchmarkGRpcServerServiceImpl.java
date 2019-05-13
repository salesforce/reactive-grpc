/*
 *  Copyright (c) 2019, Salesforce.com, Inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */
package com.salesforce.reactivegrpc.jmh;

import java.util.Arrays;
import java.util.List;

import com.salesforce.reactivegrpc.jmh.proto.BenchmarkServiceGrpc;
import com.salesforce.reactivegrpc.jmh.proto.Messages;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;

/**
 * Baseline gRPC benchmarking service.
 */
public class BenchmarkGRpcServerServiceImpl
    extends BenchmarkServiceGrpc.BenchmarkServiceImplBase {

    private final Messages.SimpleResponse       response;
    private final List<Messages.SimpleResponse> responses;

    public BenchmarkGRpcServerServiceImpl(int times) {
        Messages.SimpleResponse[] array = new Messages.SimpleResponse[times];
        Arrays.fill(array, Messages.SimpleResponse.getDefaultInstance());

        this.responses = Arrays.asList(array);
        this.response = Messages.SimpleResponse.getDefaultInstance();
    }

    @Override
    public void unaryCall(Messages.SimpleRequest request,
        StreamObserver<Messages.SimpleResponse> responseObserver) {
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void streamingFromServer(Messages.SimpleRequest request, StreamObserver<Messages.SimpleResponse> responseObserver) {
        final ServerCallStreamObserver<Messages.SimpleResponse> callStreamObserver = (ServerCallStreamObserver<Messages.SimpleResponse>) responseObserver;

        for (Messages.SimpleResponse response : responses) {
            if (callStreamObserver.isCancelled()) {
                return;
            }

            callStreamObserver.onNext(response);
        }

        if (!callStreamObserver.isCancelled()) {
            callStreamObserver.onCompleted();
        }
    }

    @Override
    public StreamObserver<Messages.SimpleRequest> streamingFromClient(StreamObserver<Messages.SimpleResponse> responseObserver) {
        return new StreamObserver<Messages.SimpleRequest>() {
            @Override
            public void onNext(Messages.SimpleRequest value) {

            }

            @Override
            public void onError(Throwable t) {
                responseObserver.onError(t);
            }

            @Override
            public void onCompleted() {
                responseObserver.onNext(response);
                responseObserver.onCompleted();
            }
        };
    }

    @Override
    public StreamObserver<Messages.SimpleRequest> streamingBothWays(StreamObserver<Messages.SimpleResponse> responseObserver) {
        final ServerCallStreamObserver<Messages.SimpleResponse> callStreamObserver = (ServerCallStreamObserver<Messages.SimpleResponse>) responseObserver;

        callStreamObserver.setOnReadyHandler(() -> {
            for (Messages.SimpleResponse response : responses) {
                if (callStreamObserver.isCancelled()) {
                    return;
                }

                callStreamObserver.onNext(response);
            }

            if (!callStreamObserver.isCancelled()) {
                callStreamObserver.onCompleted();
            }
        });

        return new StreamObserver<Messages.SimpleRequest>() {
            @Override
            public void onNext(Messages.SimpleRequest value) {

            }

            @Override
            public void onError(Throwable t) {

            }

            @Override
            public void onCompleted() {

            }
        };
    }
}
