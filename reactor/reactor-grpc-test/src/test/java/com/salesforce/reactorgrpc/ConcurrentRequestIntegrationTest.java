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
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.google.common.collect.Lists;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
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

@SuppressWarnings({"Duplicates", "unchecked"})
@RunWith(Parameterized.class)
public class ConcurrentRequestIntegrationTest {
    private static Server server;
    private static ManagedChannel channel;

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
                { new TestService(), false },
                { new FusedTestService(), true }
        });
    }

    private final ReactorGreeterGrpc.GreeterImplBase service;
    private final boolean                            expectFusion;

    public ConcurrentRequestIntegrationTest(ReactorGreeterGrpc.GreeterImplBase service, boolean expectFusion) {
        this.service = service;
        this.expectFusion = expectFusion;
    }

    @Before
    public void setupServer() throws Exception {
        server = ServerBuilder.forPort(9000).addService(service).build().start();
        channel = ManagedChannelBuilder.forAddress("localhost", server.getPort()).usePlaintext().build();
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
    public void fourKindsOfRequestAtOnce() throws Exception {
        StepVerifier.setDefaultTimeout(Duration.ofSeconds(3));

        ReactorGreeterGrpc.ReactorGreeterStub stub = ReactorGreeterGrpc.newReactorStub(channel);

        // == MAKE REQUESTS ==
        // One to One
        Mono<HelloRequest> req1 = Mono.just(HelloRequest.newBuilder().setName("reactorjava").build());
        Mono<HelloResponse> resp1 = req1.transform(stub::sayHello);

        // One to Many
        Mono<HelloRequest> req2 = Mono.just(HelloRequest.newBuilder().setName("reactorjava").build());
        Flux<HelloResponse> resp2 = req2.as(stub::sayHelloRespStream);

        // Many to One
        Flux<HelloRequest> req3 = Flux.just(
                HelloRequest.newBuilder().setName("a").build(),
                HelloRequest.newBuilder().setName("b").build(),
                HelloRequest.newBuilder().setName("c").build());

        if (!expectFusion) {
            req3 = req3.hide();
        }

        Mono<HelloResponse> resp3 = req3.as(stub::sayHelloReqStream);

        // Many to Many
        Flux<HelloRequest> req4 = Flux.just(
                HelloRequest.newBuilder().setName("a").build(),
                HelloRequest.newBuilder().setName("b").build(),
                HelloRequest.newBuilder().setName("c").build(),
                HelloRequest.newBuilder().setName("d").build(),
                HelloRequest.newBuilder().setName("e").build());

        if (!expectFusion) {
            req4 = req4.hide();
        }

        Flux<HelloResponse> resp4 = req4.transform(stub::sayHelloBothStream);

        // == VERIFY RESPONSES ==
        ListeningExecutorService executorService = MoreExecutors.listeningDecorator(Executors.newCachedThreadPool());

        // Run all four verifications in parallel
        try {
            // One to One
            ListenableFuture<Boolean> oneToOne = executorService.submit(() -> {
                StepVerifier.create(resp1.map(HelloResponse::getMessage))
                        .expectNext("Hello reactorjava")
                        .verifyComplete();
                return true;
            });

            // One to Many
            ListenableFuture<Boolean> oneToMany = executorService.submit(() -> {
                StepVerifier.create(resp2.map(HelloResponse::getMessage))
                        .expectNext("Hello reactorjava", "Hi reactorjava", "Greetings reactorjava")
                        .verifyComplete();
                return true;
            });

            // Many to One
            ListenableFuture<Boolean> manyToOne = executorService.submit(() -> {
                StepVerifier.Step<String> stepVerifier = StepVerifier.create(resp3.map(HelloResponse::getMessage));

                if (expectFusion) {
                    stepVerifier = ((StepVerifier.FirstStep<String>) stepVerifier).expectFusion();
                }

                stepVerifier
                        .expectNext("Hello a and b and c")
                        .verifyComplete();
                return true;
            });

            // Many to Many
            ListenableFuture<Boolean> manyToMany = executorService.submit(() -> {
                StepVerifier.Step<String> stepVerifier = StepVerifier.create(resp4.map(HelloResponse::getMessage));

                if (expectFusion) {
                    stepVerifier = ((StepVerifier.FirstStep<String>) stepVerifier).expectFusion();
                }

                stepVerifier
                        .expectNext("Hello a and b", "Hello c and d", "Hello e")
                        .verifyComplete();
                return true;
            });

            ListenableFuture<List<Boolean>> allFutures = Futures.allAsList(Lists.newArrayList(oneToOne, oneToMany, manyToOne, manyToMany));
            // Block for response
            List<Boolean> results = allFutures.get(3, TimeUnit.SECONDS);
            assertThat(results).containsExactly(true, true, true, true);

        } finally {
            executorService.shutdown();
        }
    }

    static class TestService extends ReactorGreeterGrpc.GreeterImplBase {

        @Override
        public Mono<HelloResponse> sayHello(Mono<HelloRequest> reactorRequest) {
            return reactorRequest
                    .hide()
                    .doOnSuccess(System.out::println)
                    .map(protoRequest -> greet("Hello", protoRequest));
        }

        @Override
        public Flux<HelloResponse> sayHelloRespStream(Mono<HelloRequest> reactorRequest) {
            return reactorRequest
                    .hide()
                    .doOnSuccess(System.out::println)
                    .flatMapMany(protoRequest -> Flux.just(
                            greet("Hello", protoRequest),
                            greet("Hi", protoRequest),
                            greet("Greetings", protoRequest)))
                    .hide();
        }

        @Override
        public Mono<HelloResponse> sayHelloReqStream(Flux<HelloRequest> reactorRequest) {
            return reactorRequest
                    .hide()
                    .doOnNext(System.out::println)
                    .map(HelloRequest::getName)
                    .collectList()
                    .map(names -> greet("Hello", String.join(" and ", names)))
                    .hide();
        }

        @Override
        public Flux<HelloResponse> sayHelloBothStream(Flux<HelloRequest> reactorRequest) {
            return reactorRequest
                    .hide()
                    .doOnNext(System.out::println)
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

    static class FusedTestService extends ReactorGreeterGrpc.GreeterImplBase {

        @Override
        public Mono<HelloResponse> sayHello(Mono<HelloRequest> reactorRequest) {
            return reactorRequest
                    .doOnSuccess(System.out::println)
                    .map(protoRequest -> greet("Hello", protoRequest));
        }

        @Override
        public Flux<HelloResponse> sayHelloRespStream(Mono<HelloRequest> reactorRequest) {
            return reactorRequest
                    .doOnSuccess(System.out::println)
                    .flatMapMany(protoRequest -> Flux.just(
                            greet("Hello", protoRequest),
                            greet("Hi", protoRequest),
                            greet("Greetings", protoRequest)));
        }

        @Override
        public Mono<HelloResponse> sayHelloReqStream(Flux<HelloRequest> reactorRequest) {
            return reactorRequest
                    .doOnNext(System.out::println)
                    .map(HelloRequest::getName)
                    .collectList()
                    .map(names -> greet("Hello", String.join(" and ", names)));
        }

        @Override
        public Flux<HelloResponse> sayHelloBothStream(Flux<HelloRequest> reactorRequest) {
            return reactorRequest
                    .doOnNext(System.out::println)
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
