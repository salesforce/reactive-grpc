/*
 *  Copyright (c) 2019, Salesforce.com, Inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.rxgrpc;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.reactivex.Flowable;
import io.reactivex.Single;
import io.reactivex.observers.TestObserver;
import io.reactivex.subscribers.TestSubscriber;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * This test verifies that the thread pools passed to gRPC are the same thread pools used by downstream reactive code.
 */
@SuppressWarnings("Duplicates")
public class ClientThreadIntegrationTest {
    @Rule
    public UnhandledRxJavaErrorRule errorRule = new UnhandledRxJavaErrorRule().autoVerifyNoError();

    private Server server;
    private ManagedChannel channel;

    private AtomicReference<String> serverThreadName = new AtomicReference<>();

    @Before
    public void setupServer() throws Exception {
        RxGreeterGrpc.GreeterImplBase svc = new RxGreeterGrpc.GreeterImplBase() {

            @Override
            public Single<HelloResponse> sayHello(Single<HelloRequest> rxRequest) {
                serverThreadName.set(Thread.currentThread().getName());
                return rxRequest.map(protoRequest -> greet("Hello", protoRequest));
            }

            @Override
            public Flowable<HelloResponse> sayHelloBothStream(Flowable<HelloRequest> rxRequest) {
                serverThreadName.set(Thread.currentThread().getName());
                return rxRequest
                        .map(HelloRequest::getName)
                        .buffer(2)
                        .map(names -> greet("Hello", String.join(" and ", names)));
            }

            private HelloResponse greet(String greeting, HelloRequest request) {
                return greet(greeting, request.getName());
            }

            private HelloResponse greet(String greeting, String name) {
                return HelloResponse.newBuilder().setMessage(greeting + " " + name).build();
            }
        };

        server = ServerBuilder
                .forPort(0)
                .addService(svc)
                .executor(Executors.newSingleThreadExecutor(
                        new ThreadFactoryBuilder().setNameFormat("TheGrpcServer").build()))
                .build()
                .start();
        channel = ManagedChannelBuilder
                .forAddress("localhost", server.getPort())
                .usePlaintext()
                .executor(Executors.newSingleThreadExecutor(
                        new ThreadFactoryBuilder().setNameFormat("TheGrpcClient").build()))
                .build();
    }

    @After
    public void stopServer() throws InterruptedException {
        server.shutdown();
        server.awaitTermination();
        channel.shutdown();

        server = null;
        channel = null;
    }

    @Test
    public void oneToOne() {
        RxGreeterGrpc.RxGreeterStub stub = RxGreeterGrpc.newRxStub(channel);
        Single<HelloRequest> req = Single.just(HelloRequest.newBuilder().setName("rxjava").build());
        Single<HelloResponse> resp = req.compose(stub::sayHello);

        AtomicReference<String> clientThreadName = new AtomicReference<>();

        TestObserver<String> testObserver = resp
                .map(HelloResponse::getMessage)
                .doOnSuccess(x -> clientThreadName.set(Thread.currentThread().getName()))
                .test();
        testObserver.awaitTerminalEvent(3, TimeUnit.SECONDS);

        assertThat(clientThreadName.get()).isEqualTo("TheGrpcClient");
        assertThat(serverThreadName.get()).isEqualTo("TheGrpcServer");
    }

    @Test
    public void manyToMany() {
        RxGreeterGrpc.RxGreeterStub stub = RxGreeterGrpc.newRxStub(channel);
        Flowable<HelloRequest> req = Flowable.just(
                HelloRequest.newBuilder().setName("a").build(),
                HelloRequest.newBuilder().setName("b").build(),
                HelloRequest.newBuilder().setName("c").build(),
                HelloRequest.newBuilder().setName("d").build(),
                HelloRequest.newBuilder().setName("e").build());

        AtomicReference<String> clientThreadName = new AtomicReference<>();

        Flowable<HelloResponse> resp = req.compose(stub::sayHelloBothStream);

        TestSubscriber<String> testSubscriber = resp
                .map(HelloResponse::getMessage)
                .doOnNext(x -> clientThreadName.set(Thread.currentThread().getName()))
                .test();
        testSubscriber.awaitTerminalEvent(3, TimeUnit.SECONDS);
        testSubscriber.assertComplete();

        assertThat(clientThreadName.get()).isEqualTo("TheGrpcClient");
        assertThat(serverThreadName.get()).isEqualTo("TheGrpcServer");
    }
}
