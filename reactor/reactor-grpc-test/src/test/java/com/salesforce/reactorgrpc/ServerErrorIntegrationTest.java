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
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@SuppressWarnings("unchecked")
@RunWith(Parameterized.class)
public class ServerErrorIntegrationTest {
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

    public ServerErrorIntegrationTest(ReactorGreeterGrpc.GreeterImplBase service, boolean expectFusion) {
        this.service = service;
        this.expectFusion = expectFusion;
    }

    @Before
    public void setupServer() throws Exception {
        StepVerifier.setDefaultTimeout(Duration.ofSeconds(3));
        server = ServerBuilder.forPort(9000).addService(service).build().start();
        channel = ManagedChannelBuilder.forAddress("localhost", server.getPort()).usePlaintext().build();
    }

    @After
    public void stopServer() {
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
                .verifyErrorMatches(t -> t instanceof StatusRuntimeException && ((StatusRuntimeException)t).getStatus() == Status.INTERNAL);
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

        StepVerifier.create(test)
                .verifyErrorMatches(t -> t instanceof StatusRuntimeException && ((StatusRuntimeException)t).getStatus() == Status.INTERNAL);
    }

    @Test
    public void manyToOne() {
        ReactorGreeterGrpc.ReactorGreeterStub stub = ReactorGreeterGrpc.newReactorStub(channel);
        Flux<HelloRequest> requestFlux = Flux.just(HelloRequest.getDefaultInstance());

        if (!expectFusion) {
            requestFlux = requestFlux.hide();
        }

        Mono<HelloResponse> resp = requestFlux.as(stub::sayHelloReqStream);

        StepVerifier.Step<HelloResponse> stepVerifier = StepVerifier.create(resp);

        if (expectFusion) {
            stepVerifier = ((StepVerifier.FirstStep<HelloResponse>) stepVerifier).expectFusion();
        }

        stepVerifier
                .verifyErrorMatches(t -> t instanceof StatusRuntimeException && ((StatusRuntimeException)t).getStatus() == Status.INTERNAL);
    }

    @Test
    public void manyToMany() {
        ReactorGreeterGrpc.ReactorGreeterStub stub = ReactorGreeterGrpc.newReactorStub(channel);
        Flux<HelloRequest> requestFlux = Flux.just(HelloRequest.getDefaultInstance());

        if (!expectFusion) {
            requestFlux = requestFlux.hide();
        }

        Flux<HelloResponse> resp = requestFlux.transform(stub::sayHelloBothStream);

        StepVerifier.Step<HelloResponse> stepVerifier = StepVerifier.create(resp);

        if (expectFusion) {
            stepVerifier = ((StepVerifier.FirstStep<HelloResponse>) stepVerifier).expectFusion();
        }

        stepVerifier
                .verifyErrorMatches(t -> t instanceof StatusRuntimeException && ((StatusRuntimeException)t).getStatus() == Status.INTERNAL);
    }

    static class TestService extends ReactorGreeterGrpc.GreeterImplBase {
        @Override
        public Mono<HelloResponse> sayHello(Mono<HelloRequest> reactorRequest) {
            return Mono.<HelloResponse>error(new StatusRuntimeException(Status.INTERNAL)).hide();
        }

        @Override
        public Flux<HelloResponse> sayHelloRespStream(Mono<HelloRequest> reactorRequest) {
            return Flux.<HelloResponse>error(new StatusRuntimeException(Status.INTERNAL)).hide();
        }

        @Override
        public Mono<HelloResponse> sayHelloReqStream(Flux<HelloRequest> reactorRequest) {
            return Mono.<HelloResponse>error(new StatusRuntimeException(Status.INTERNAL)).hide();
        }

        @Override
        public Flux<HelloResponse> sayHelloBothStream(Flux<HelloRequest> reactorRequest) {
            return Flux.<HelloResponse>error(new StatusRuntimeException(Status.INTERNAL)).hide();
        }
    }

    static class FusedTestService extends ReactorGreeterGrpc.GreeterImplBase {
        @Override
        public Mono<HelloResponse> sayHello(Mono<HelloRequest> reactorRequest) {
            return Mono.error(new StatusRuntimeException(Status.INTERNAL));
        }

        @Override
        public Flux<HelloResponse> sayHelloRespStream(Mono<HelloRequest> reactorRequest) {
            return Flux.error(new StatusRuntimeException(Status.INTERNAL));
        }

        @Override
        public Mono<HelloResponse> sayHelloReqStream(Flux<HelloRequest> reactorRequest) {
            return Mono.error(new StatusRuntimeException(Status.INTERNAL));
        }

        @Override
        public Flux<HelloResponse> sayHelloBothStream(Flux<HelloRequest> reactorRequest) {
            return Flux.error(new StatusRuntimeException(Status.INTERNAL));
        }
    }

}
