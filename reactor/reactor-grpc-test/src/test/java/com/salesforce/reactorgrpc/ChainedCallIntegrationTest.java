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

@SuppressWarnings("Duplicates")
@RunWith(Parameterized.class)
public class ChainedCallIntegrationTest {
    private Server server;
    private ManagedChannel channel;


    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
                { new TestGreeterService(), false },
                { new FusedTestGreeterService(), true }
        });
    }

    private final ReactorGreeterGrpc.GreeterImplBase service;
    private final boolean                            expectFusion;


    public ChainedCallIntegrationTest(ReactorGreeterGrpc.GreeterImplBase service, boolean expectFusion) {
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
        server.shutdownNow();
        channel.shutdownNow();

        server = null;
        channel = null;
    }

    @Test
    public void servicesCanCallOtherServices() throws InterruptedException {
        ReactorGreeterGrpc.ReactorGreeterStub stub = ReactorGreeterGrpc.newReactorStub(channel);

        Mono<HelloRequest> initial = Mono.just(request("X"));

        if (!expectFusion) {
            initial = initial.hide();
        }

        Mono<HelloResponse> sayHelloResult = initial
                // one -> one
                .transform(stub::sayHello);

        if (!expectFusion) {
            sayHelloResult = sayHelloResult.hide();
        }

        Flux<HelloResponse> sayHelloRespStream = sayHelloResult
                .map(ChainedCallIntegrationTest::bridge)
                .doOnSuccess(System.out::println)
                // one -> many
                .as(stub::sayHelloRespStream);

        if (!expectFusion) {
            sayHelloRespStream = sayHelloRespStream.hide();
        }

        Flux<HelloResponse> sayHelloBothStream = sayHelloRespStream
                .map(ChainedCallIntegrationTest::bridge)
                .doOnNext(System.out::println)
                // many -> many
                .transform(stub::sayHelloBothStream);

        if (!expectFusion) {
            sayHelloBothStream = sayHelloBothStream.hide();
        }

        Mono<HelloResponse> sayHelloReqStream = sayHelloBothStream
                .map(ChainedCallIntegrationTest::bridge)
                .doOnNext(System.out::println)
                // many -> one
                .as(stub::sayHelloReqStream);

        if (!expectFusion) {
            sayHelloReqStream = sayHelloReqStream.hide();
        }

        Mono<HelloResponse> sayHello =
                sayHelloReqStream.map(ChainedCallIntegrationTest::bridge)
                                 .doOnSuccess(System.out::println)
                                 // one -> one
                                 .transform(stub::sayHello);

        if (!expectFusion) {
            sayHello = sayHello.hide();
        }

        Mono<String> chain = sayHello
                                 .map(HelloResponse::getMessage)
                                 .doOnSuccess(System.out::println);

        StepVerifier.create(chain)
                    .expectNext("[<{[X]}> :: </[X]/> :: <\\[X]\\> :: <([X])>]")
                    .expectComplete()
                    .verify(Duration.ofSeconds(2));
    }

    private static HelloRequest bridge(HelloResponse response) {
        return request(response.getMessage());
    }

    private static HelloRequest request(String text) {
        return HelloRequest.newBuilder().setName(text).build();
    }

    private static HelloResponse response(String text) {
        return HelloResponse.newBuilder().setMessage(text).build();
    }

    static class TestGreeterService extends ReactorGreeterGrpc.GreeterImplBase {

        @Override
        public Mono<HelloResponse> sayHello(Mono<HelloRequest> reactorRequest) {
            return reactorRequest
                    .hide()
                    .map(protoRequest -> response("[" + protoRequest.getName() + "]"));
        }

        @Override
        public Flux<HelloResponse> sayHelloRespStream(Mono<HelloRequest> reactorRequest) {
            return reactorRequest
                    .hide()
                    .map(HelloRequest::getName)
                    .flatMapMany(name -> Flux.just(
                            response("{" + name + "}"),
                            response("/" + name + "/"),
                            response("\\" + name + "\\"),
                            response("(" + name + ")"))
                    )
                    .hide();
        }

        @Override
        public Mono<HelloResponse> sayHelloReqStream(Flux<HelloRequest> reactorRequest) {
            return reactorRequest
                    .hide()
                    .map(HelloRequest::getName)
                    .reduce((l, r) -> l + " :: " + r)
                    .map(ChainedCallIntegrationTest::response)
                    .hide();
        }

        @Override
        public Flux<HelloResponse> sayHelloBothStream(Flux<HelloRequest> reactorRequest) {
            return reactorRequest
                    .hide()
                    .map(HelloRequest::getName)
                    .map(name -> "<" + name + ">")
                    .map(ChainedCallIntegrationTest::response)
                    .hide();
        }
    }

    static class FusedTestGreeterService extends ReactorGreeterGrpc.GreeterImplBase {

        @Override
        public Mono<HelloResponse> sayHello(Mono<HelloRequest> reactorRequest) {
            return reactorRequest.map(protoRequest -> response("[" + protoRequest.getName() + "]"));
        }

        @Override
        public Flux<HelloResponse> sayHelloRespStream(Mono<HelloRequest> reactorRequest) {
            return reactorRequest
                    .map(HelloRequest::getName)
                    .flatMapMany(name -> Flux.just(
                            response("{" + name + "}"),
                            response("/" + name + "/"),
                            response("\\" + name + "\\"),
                            response("(" + name + ")"))
                    );
        }

        @Override
        public Mono<HelloResponse> sayHelloReqStream(Flux<HelloRequest> reactorRequest) {
            return reactorRequest
                    .map(HelloRequest::getName)
                    .reduce((l, r) -> l + " :: " + r)
                    .map(ChainedCallIntegrationTest::response);
        }

        @Override
        public Flux<HelloResponse> sayHelloBothStream(Flux<HelloRequest> reactorRequest) {
            return reactorRequest
                    .map(HelloRequest::getName)
                    .map(name -> "<" + name + ">")
                    .map(ChainedCallIntegrationTest::response);
        }
    }
}
