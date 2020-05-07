/*
 *  Copyright (c) 2019, Salesforce.com, Inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.reactorgrpc;

import java.time.Duration;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@SuppressWarnings("unchecked")
public class UnexpectedServerErrorIntegrationTest {
    private static Server server;
    private static ManagedChannel channel;

    @BeforeClass
    public static void setupServer() throws Exception {
        ReactorGreeterGrpc.GreeterImplBase svc = new ReactorGreeterGrpc.GreeterImplBase() {
            @Override
            public Mono<HelloResponse> sayHello(Mono<HelloRequest> reactorRequest) {
                return reactorRequest.map(this::map);
            }

            @Override
            public Flux<HelloResponse> sayHelloRespStream(Mono<HelloRequest> reactorRequest) {
                return reactorRequest.map(this::map).flux();
            }

            @Override
            public Mono<HelloResponse> sayHelloReqStream(Flux<HelloRequest> reactorRequest) {
                return reactorRequest.map(this::map).single();
            }

            @Override
            public Flux<HelloResponse> sayHelloBothStream(Flux<HelloRequest> reactorRequest) {
                return reactorRequest.map(this::map);
            }

            private HelloResponse map(HelloRequest request) {
                throw Status.INTERNAL.withDescription("Kaboom!").asRuntimeException();
            }
        };

        server = ServerBuilder.forPort(9000).addService(svc).build().start();
        channel = ManagedChannelBuilder.forAddress("localhost", server.getPort()).usePlaintext().build();
    }

    @Before
    public void init() {
        StepVerifier.setDefaultTimeout(Duration.ofSeconds(3));
    }

    @AfterClass
    public static void stopServer() {
        server.shutdown();
        channel.shutdown();

        server = null;
        channel = null;
    }

    @Test
    public void oneToOne() {
        ReactorGreeterGrpc.ReactorGreeterStub stub = ReactorGreeterGrpc.newReactorStub(channel);
        Mono<HelloResponse> resp = Mono.just(HelloRequest.getDefaultInstance()).transform(stub::sayHello);

        StepVerifier.create(resp)
                .verifyErrorMatches(t -> t instanceof StatusRuntimeException && ((StatusRuntimeException)t).getStatus().getCode() == Status.Code.INTERNAL);
    }

    @Test
    public void oneToMany() {
        ReactorGreeterGrpc.ReactorGreeterStub stub = ReactorGreeterGrpc.newReactorStub(channel);
        Flux<HelloResponse> resp = Mono.just(HelloRequest.getDefaultInstance()).as(stub::sayHelloRespStream);
        Flux<HelloResponse> test = resp
                .doOnNext(System.out::println)
                .doOnError(throwable -> System.out.println(throwable.getMessage()))
                .doOnComplete(() -> System.out.println("Completed"))
                .doOnCancel(() -> System.out.println("Client canceled"));

        StepVerifier.create(resp)
                .verifyErrorMatches(t -> t instanceof StatusRuntimeException && ((StatusRuntimeException)t).getStatus().getCode() == Status.Code.INTERNAL);
    }

    @Test
    public void manyToOne() {
        ReactorGreeterGrpc.ReactorGreeterStub stub = ReactorGreeterGrpc.newReactorStub(channel);
        Flux<HelloRequest> req = Flux.just(HelloRequest.getDefaultInstance());
        Mono<HelloResponse> resp = req.as(stub::sayHelloReqStream);

        StepVerifier.create(resp)
                .verifyErrorMatches(t -> t instanceof StatusRuntimeException && ((StatusRuntimeException)t).getStatus().getCode() == Status.Code.INTERNAL);
    }

    @Test
    public void manyToMany() {
        ReactorGreeterGrpc.ReactorGreeterStub stub = ReactorGreeterGrpc.newReactorStub(channel);
        Flux<HelloRequest> req = Flux.just(HelloRequest.getDefaultInstance());
        Flux<HelloResponse> resp = req.transform(stub::sayHelloBothStream);

        StepVerifier.create(resp)
                .verifyErrorMatches(t -> t instanceof StatusRuntimeException && ((StatusRuntimeException)t).getStatus().getCode() == Status.Code.INTERNAL);
    }

}
