/*
 *  Copyright (c) 2017, salesforce.com, inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.reactorgrpc;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * This test verifies that the thread pools passed to gRPC are the same thread pools used by downstream reactive code.
 */
@SuppressWarnings("Duplicates")
public class ClientThreadIntegrationTest {
    private Server server;
    private ManagedChannel channel;

    private AtomicReference<String> serverThreadName = new AtomicReference<>();

    @Before
    public void setupServer() throws Exception {
        StepVerifier.setDefaultTimeout(Duration.ofSeconds(3));

        ReactorGreeterGrpc.GreeterImplBase svc = new ReactorGreeterGrpc.GreeterImplBase() {

            @Override
            public Mono<HelloResponse> sayHello(Mono<HelloRequest> reactorRequest) {
                serverThreadName.set(Thread.currentThread().getName());
                return reactorRequest.map(protoRequest -> greet("Hello", protoRequest));
            }

            @Override
            public Flux<HelloResponse> sayHelloBothStream(Flux<HelloRequest> reactorRequest) {
                serverThreadName.set(Thread.currentThread().getName());
                return reactorRequest
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
        ReactorGreeterGrpc.ReactorGreeterStub stub = ReactorGreeterGrpc.newReactorStub(channel);
        Mono<HelloRequest> req = Mono.just(HelloRequest.newBuilder().setName("reactorjava").build());
        Mono<HelloResponse> resp = stub.sayHello(req);

        AtomicReference<String> clientThreadName = new AtomicReference<>();

        StepVerifier
                .create(resp
                        .map(HelloResponse::getMessage)
                        .doOnSuccess(x -> clientThreadName.set(Thread.currentThread().getName())))
                .expectNext("Hello reactorjava")
                .verifyComplete();

        assertThat(clientThreadName.get()).isEqualTo("TheGrpcClient");
        assertThat(serverThreadName.get()).isEqualTo("TheGrpcServer");
    }

    @Test
    public void manyToMany() {
        ReactorGreeterGrpc.ReactorGreeterStub stub = ReactorGreeterGrpc.newReactorStub(channel);
        Flux<HelloRequest> req = Flux.just(
                HelloRequest.newBuilder().setName("a").build(),
                HelloRequest.newBuilder().setName("b").build(),
                HelloRequest.newBuilder().setName("c").build(),
                HelloRequest.newBuilder().setName("d").build(),
                HelloRequest.newBuilder().setName("e").build());

        Flux<HelloResponse> resp = stub.sayHelloBothStream(req);

        AtomicReference<String> clientThreadName = new AtomicReference<>();

        StepVerifier
                .create(resp
                        .map(HelloResponse::getMessage)
                        .doOnNext(x -> clientThreadName.set(Thread.currentThread().getName())))
                .expectNext("Hello a and b", "Hello c and d", "Hello e")
                .verifyComplete();

        assertThat(clientThreadName.get()).isEqualTo("TheGrpcClient");
        assertThat(serverThreadName.get()).isEqualTo("TheGrpcServer");
    }
}
