/*
 *  Copyright (c) 2019, Salesforce.com, Inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.reactorgrpc;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * This test verifies that the thread pools passed to gRPC are the same thread pools used by downstream reactive code.
 */
@SuppressWarnings("Duplicates")
@RunWith(Parameterized.class)
public class ClientThreadIntegrationTest {
    private Server server;
    private ManagedChannel channel;

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
                { new TestService(), false },
                { new FusedTestService(), true }
        });
    }

    private final AbstractGreeterService service;
    private final boolean                expectFusion;

    public ClientThreadIntegrationTest(AbstractGreeterService service, boolean expectFusion) {
        this.service = service;
        this.expectFusion = expectFusion;
    }

    @Before
    public void setupServer() throws Exception {
        StepVerifier.setDefaultTimeout(Duration.ofSeconds(3));

        server = ServerBuilder
                .forPort(0)
                .addService(service)
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

        if (!expectFusion) {
            req = req.hide();
        }

        Mono<HelloResponse> resp = req.transform(stub::sayHello);

        AtomicReference<String> clientThreadName = new AtomicReference<>();

        StepVerifier.Step<String> stepVerifier = StepVerifier.create(
            resp
                    .map(HelloResponse::getMessage)
                    .doOnSuccess(x -> clientThreadName.set(Thread.currentThread().getName()))
        );

        stepVerifier
                .expectNext("Hello reactorjava")
                .verifyComplete();

        assertThat(clientThreadName.get()).isEqualTo("TheGrpcClient");
        assertThat(service.serverThreadName.get()).isEqualTo("TheGrpcServer");
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

        if (!expectFusion) {
            req = req.hide();
        }

        Flux<HelloResponse> resp = req.transform(stub::sayHelloBothStream);

        AtomicReference<String> clientThreadName = new AtomicReference<>();

        StepVerifier.Step<String> stepVerifier = StepVerifier.create(
            resp
                .map(HelloResponse::getMessage)
                .doOnNext(x -> clientThreadName.set(Thread.currentThread().getName()))
        );

        if (expectFusion) {
            stepVerifier = ((StepVerifier.FirstStep<String>) stepVerifier).expectFusion();
        }

        stepVerifier
                .expectNext("Hello a and b", "Hello c and d", "Hello e")
                .verifyComplete();

        assertThat(clientThreadName.get()).isEqualTo("TheGrpcClient");
        assertThat(service.serverThreadName.get()).isEqualTo("TheGrpcServer");
    }

    static abstract class AbstractGreeterService extends ReactorGreeterGrpc.GreeterImplBase {

        AtomicReference<String> serverThreadName = new AtomicReference<>();
    }

    static class TestService extends AbstractGreeterService {

        @Override
        public Mono<HelloResponse> sayHello(Mono<HelloRequest> reactorRequest) {
            serverThreadName.set(Thread.currentThread().getName());
            return reactorRequest.map(protoRequest -> greet("Hello", protoRequest));
        }

        @Override
        public Flux<HelloResponse> sayHelloBothStream(Flux<HelloRequest> reactorRequest) {
            serverThreadName.set(Thread.currentThread().getName());
            return reactorRequest
                    .hide()
                    .map(HelloRequest::getName)
                    .buffer(2)
                    .map(names -> greet("Hello", String.join(" and ", names)))
                    .hide();
        }

        private HelloResponse greet(String greeting, HelloRequest request) {
            return greet(greeting, request.getName());
        }

        private HelloResponse greet(String greeting, String name) {
            return HelloResponse.newBuilder().setMessage(greeting + " " + name).build();
        }
    }

    static class FusedTestService extends AbstractGreeterService {

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
    }
}
