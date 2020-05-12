/*
 *  Copyright (c) 2019, Salesforce.com, Inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.reactorgrpc;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@SuppressWarnings("Duplicates")
public class ReactiveClientStandardServerInteropTest {
    private static Server server;
    private static ManagedChannel channel;

    @BeforeClass
    public static void setupServer() throws Exception {
        GreeterGrpc.GreeterImplBase svc = new GreeterGrpc.GreeterImplBase() {

            @Override
            public void sayHello(HelloRequest request, StreamObserver<HelloResponse> responseObserver) {
                responseObserver.onNext(HelloResponse.newBuilder().setMessage("Hello " + request.getName()).build());
                responseObserver.onCompleted();
            }

            @Override
            public void sayHelloRespStream(HelloRequest request, StreamObserver<HelloResponse> responseObserver) {
                responseObserver.onNext(HelloResponse.newBuilder().setMessage("Hello " + request.getName()).build());
                responseObserver.onNext(HelloResponse.newBuilder().setMessage("Hi " + request.getName()).build());
                responseObserver.onNext(HelloResponse.newBuilder().setMessage("Greetings " + request.getName()).build());
                responseObserver.onCompleted();
            }

            @Override
            public StreamObserver<HelloRequest> sayHelloReqStream(StreamObserver<HelloResponse> responseObserver) {
                return new StreamObserver<HelloRequest>() {
                    List<String> names = new ArrayList<>();

                    @Override
                    public void onNext(HelloRequest request) {
                        names.add(request.getName());
                    }

                    @Override
                    public void onError(Throwable t) {
                        responseObserver.onError(t);
                    }

                    @Override
                    public void onCompleted() {
                        String message = "Hello " + String.join(" and ", names);
                        responseObserver.onNext(HelloResponse.newBuilder().setMessage(message).build());
                        responseObserver.onCompleted();
                    }
                };
            }

            @Override
            public StreamObserver<HelloRequest> sayHelloBothStream(StreamObserver<HelloResponse> responseObserver) {
                return new StreamObserver<HelloRequest>() {
                    List<String> names = new ArrayList<>();

                    @Override
                    public void onNext(HelloRequest request) {
                        names.add(request.getName());
                    }

                    @Override
                    public void onError(Throwable t) {
                        responseObserver.onError(t);
                    }

                    @Override
                    public void onCompleted() {
                        // Will fail for odd number of names, but that's not what is being tested, so ¯\_(ツ)_/¯
                        for (int i = 0; i < names.size(); i += 2) {
                            String message = "Hello " + names.get(i) + " and " + names.get(i+1);
                            responseObserver.onNext(HelloResponse.newBuilder().setMessage(message).build());
                        }
                        responseObserver.onCompleted();
                    }
                };
            }
        };

        server = ServerBuilder.forPort(9000).addService(svc).build().start();
        channel = ManagedChannelBuilder.forAddress("localhost", server.getPort()).usePlaintext().build();
    }

    @Before
    public void init() {
        StepVerifier.setDefaultTimeout(Duration.ofSeconds(5));
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
    public void oneToOne() {
        ReactorGreeterGrpc.ReactorGreeterStub stub = ReactorGreeterGrpc.newReactorStub(channel);
        Mono<String> reactorRequest = Mono.just("World");
        Mono<String> reactorResponse = reactorRequest.map(this::toRequest).transform(stub::sayHello).map(this::fromResponse);

        StepVerifier.create(reactorResponse)
                .expectNext("Hello World")
                .verifyComplete();
    }

    @Test
    public void oneToMany() {
        ReactorGreeterGrpc.ReactorGreeterStub stub = ReactorGreeterGrpc.newReactorStub(channel);
        Mono<String> reactorRequest = Mono.just("World");
        Flux<String> reactorResponse = reactorRequest.map(this::toRequest).as(stub::sayHelloRespStream).map(this::fromResponse);

        StepVerifier.create(reactorResponse)
                .expectNext("Hello World", "Hi World", "Greetings World")
                .verifyComplete();
    }

    @Test
    public void manyToOne() {
        ReactorGreeterGrpc.ReactorGreeterStub stub = ReactorGreeterGrpc.newReactorStub(channel);
        Flux<String> reactorRequest = Flux.just("A", "B", "C");
        Mono<String> reactorResponse = reactorRequest.map(this::toRequest).as(stub::sayHelloReqStream).map(this::fromResponse);

        StepVerifier.create(reactorResponse)
                .expectNext("Hello A and B and C")
                .verifyComplete();
    }

    @Test
    public void manyToMany() {
        ReactorGreeterGrpc.ReactorGreeterStub stub = ReactorGreeterGrpc.newReactorStub(channel);
        Flux<String> reactorRequest = Flux.just("A", "B", "C", "D");
        Flux<String> reactorResponse = reactorRequest.map(this::toRequest).transform(stub::sayHelloBothStream).map(this::fromResponse);

        StepVerifier.create(reactorResponse)
                .expectNext("Hello A and B", "Hello C and D")
                .verifyComplete();
    }

    private HelloRequest toRequest(String name) {
        return HelloRequest.newBuilder().setName(name).build();
    }

    private String fromResponse(HelloResponse response) {
        return response.getMessage();
    }
}
