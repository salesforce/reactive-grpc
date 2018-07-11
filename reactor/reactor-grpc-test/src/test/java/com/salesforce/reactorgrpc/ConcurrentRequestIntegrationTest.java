/*
 *  Copyright (c) 2017, salesforce.com, inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.reactorgrpc;

import com.google.common.collect.Lists;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings({"Duplicates", "unchecked"})
public class ConcurrentRequestIntegrationTest {
    private static Server server;
    private static ManagedChannel channel;

    @BeforeClass
    public static void setupServer() throws Exception {
        ReactorGreeterGrpc.GreeterImplBase svc = new ReactorGreeterGrpc.GreeterImplBase() {

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
        };

        server = ServerBuilder.forPort(0).addService(svc).build().start();
        channel = ManagedChannelBuilder.forAddress("localhost", server.getPort()).usePlaintext().build();
    }

    @AfterClass
    public static void stopServer() throws InterruptedException {
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
        Mono<HelloResponse> resp1 = req1.compose(stub::sayHello);

        // One to Many
        Mono<HelloRequest> req2 = Mono.just(HelloRequest.newBuilder().setName("reactorjava").build());
        Flux<HelloResponse> resp2 = req2.as(stub::sayHelloRespStream);

        // Many to One
        Flux<HelloRequest> req3 = Flux.just(
                HelloRequest.newBuilder().setName("a").build(),
                HelloRequest.newBuilder().setName("b").build(),
                HelloRequest.newBuilder().setName("c").build());

        Mono<HelloResponse> resp3 = req3.as(stub::sayHelloReqStream);

        // Many to Many
        Flux<HelloRequest> req4 = Flux.just(
                HelloRequest.newBuilder().setName("a").build(),
                HelloRequest.newBuilder().setName("b").build(),
                HelloRequest.newBuilder().setName("c").build(),
                HelloRequest.newBuilder().setName("d").build(),
                HelloRequest.newBuilder().setName("e").build());

        Flux<HelloResponse> resp4 = req4.compose(stub::sayHelloBothStream);

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
                StepVerifier.create(resp3.map(HelloResponse::getMessage))
                        .expectNext("Hello a and b and c")
                        .verifyComplete();
                return true;
            });

            // Many to Many
            ListenableFuture<Boolean> manyToMany = executorService.submit(() -> {
                StepVerifier.create(resp4.map(HelloResponse::getMessage))
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
}
