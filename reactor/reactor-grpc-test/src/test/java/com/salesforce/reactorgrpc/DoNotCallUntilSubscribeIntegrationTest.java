/*
 *  Copyright (c) 2019, Salesforce.com, Inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.reactorgrpc;

import io.grpc.*;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("Duplicates")
@RunWith(Parameterized.class)
public class DoNotCallUntilSubscribeIntegrationTest {
    private Server server;
    private ManagedChannel channel;
    private WasCalledInterceptor interceptor;

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
                { new TestService(), false },
                { new FusedTestService(), true }
        });
    }

    private final ReactorGreeterGrpc.GreeterImplBase service;
    private final boolean                            expectFusion;

    public DoNotCallUntilSubscribeIntegrationTest(ReactorGreeterGrpc.GreeterImplBase service, boolean expectFusion) {
        this.service = service;
        this.expectFusion = expectFusion;
    }

    private static class WasCalledInterceptor implements ServerInterceptor {
        private boolean wasCalled = false;
        private boolean didRespond = false;

        public boolean wasCalled() {
            return wasCalled;
        }

        public boolean didRespond() {
            return didRespond;
        }

        @Override
        public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
            return new ForwardingServerCallListener.SimpleForwardingServerCallListener<ReqT>(
                    next.startCall(new ForwardingServerCall.SimpleForwardingServerCall<ReqT, RespT>(call) {
                        @Override
                        public void sendMessage(RespT message) {
                            didRespond = true;
                            super.sendMessage(message);
                        }
                    }, headers)) {
                @Override
                public void onMessage(ReqT message) {
                    wasCalled = true;
                    super.onMessage(message);
                }
            };
        }
    }

    @Before
    public void setupServer() throws Exception {
        interceptor = new WasCalledInterceptor();
        server = ServerBuilder.forPort(9000).addService(service).intercept(interceptor).build().start();
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
    public void oneToOne() throws Exception {
        ReactorGreeterGrpc.ReactorGreeterStub stub = ReactorGreeterGrpc.newReactorStub(channel);
        Mono<HelloRequest> req = Mono.just(HelloRequest.newBuilder().setName("reactorjava").build());
        Mono<HelloResponse> resp = req.transform(stub::sayHello);

        Thread.sleep(100);
        assertThat(interceptor.wasCalled()).isFalse();
        assertThat(interceptor.didRespond()).isFalse();
    }

    @Test
    public void oneToMany() throws Exception {
        ReactorGreeterGrpc.ReactorGreeterStub stub = ReactorGreeterGrpc.newReactorStub(channel);
        Mono<HelloRequest> req = Mono.just(HelloRequest.newBuilder().setName("reactorjava").build());
        Flux<HelloResponse> resp = req.as(stub::sayHelloRespStream);

        Thread.sleep(100);
        assertThat(interceptor.wasCalled()).isFalse();
        assertThat(interceptor.didRespond()).isFalse();
    }

    @Test
    public void manyToOne() throws Exception {
        ReactorGreeterGrpc.ReactorGreeterStub stub = ReactorGreeterGrpc.newReactorStub(channel);
        Flux<HelloRequest> req = Flux.just(
                HelloRequest.newBuilder().setName("a").build(),
                HelloRequest.newBuilder().setName("b").build(),
                HelloRequest.newBuilder().setName("c").build());

        if (!expectFusion) {
            req = req.hide();
        }

        Mono<HelloResponse> resp = req.as(stub::sayHelloReqStream);

        Thread.sleep(100);
        assertThat(interceptor.wasCalled()).isFalse();
        assertThat(interceptor.didRespond()).isFalse();
    }

    @Test
    public void manyToMany() throws Exception {
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

        Thread.sleep(100);
        assertThat(interceptor.wasCalled()).isFalse();
        assertThat(interceptor.didRespond()).isFalse();
    }

    static class TestService extends ReactorGreeterGrpc.GreeterImplBase {

        @Override
        public Mono<HelloResponse> sayHello(HelloRequest protoRequest) {
            return Mono.fromCallable(() -> greet("Hello", protoRequest));
        }

        @Override
        public Flux<HelloResponse> sayHelloRespStream(HelloRequest protoRequest) {
            return Flux.just(
                    greet("Hello", protoRequest),
                    greet("Hi", protoRequest),
                    greet("Greetings", protoRequest));
        }

        @Override
        public Mono<HelloResponse> sayHelloReqStream(Flux<HelloRequest> reactorRequest) {
            return reactorRequest
                    .hide()
                    .map(HelloRequest::getName)
                    .collectList()
                    .map(names -> greet("Hello", String.join(" and ", names)))
                    .hide();
        }

        @Override
        public Flux<HelloResponse> sayHelloBothStream(Flux<HelloRequest> reactorRequest) {
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

    static class FusedTestService extends ReactorGreeterGrpc.GreeterImplBase {

        @Override
        public Mono<HelloResponse> sayHello(Mono<HelloRequest> reactorRequest) {
            return reactorRequest.map(protoRequest -> greet("Hello", protoRequest));
        }

        @Override
        public Flux<HelloResponse> sayHelloRespStream(Mono<HelloRequest> reactorRequest) {
            return reactorRequest.flatMapMany(protoRequest -> Flux.just(
                    greet("Hello", protoRequest),
                    greet("Hi", protoRequest),
                    greet("Greetings", protoRequest)));
        }

        @Override
        public Mono<HelloResponse> sayHelloReqStream(Flux<HelloRequest> reactorRequest) {
            return reactorRequest
                    .map(HelloRequest::getName)
                    .collectList()
                    .map(names -> greet("Hello", String.join(" and ", names)));
        }

        @Override
        public Flux<HelloResponse> sayHelloBothStream(Flux<HelloRequest> reactorRequest) {
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
