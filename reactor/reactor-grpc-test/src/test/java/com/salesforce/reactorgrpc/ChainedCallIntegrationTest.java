/*
 *  Copyright (c) 2017, salesforce.com, inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.reactorgrpc;

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

@SuppressWarnings("Duplicates")
public class ChainedCallIntegrationTest {
    private Server server;
    private ManagedChannel channel;

    @Before
    public void setupServer() throws Exception {
        ReactorGreeterGrpc.GreeterImplBase svc = new ReactorGreeterGrpc.GreeterImplBase() {

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
        };

        server = ServerBuilder.forPort(0).addService(svc).build().start();
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

        Mono<String> chain = Mono.just(request("X"))
                // one -> one
                .compose(stub::sayHello)
                .map(ChainedCallIntegrationTest::bridge)
                .doOnSuccess(System.out::println)
                // one -> many
                .as(stub::sayHelloRespStream)
                .map(ChainedCallIntegrationTest::bridge)
                .doOnNext(System.out::println)
                // many -> many
                .compose(stub::sayHelloBothStream)
                .map(ChainedCallIntegrationTest::bridge)
                .doOnNext(System.out::println)
                // many -> one
                .as(stub::sayHelloReqStream)
                .map(ChainedCallIntegrationTest::bridge)
                .doOnSuccess(System.out::println)
                // one -> one
                .compose(stub::sayHello)
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
}
