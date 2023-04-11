/*
 *  Copyright (c) 2019, Salesforce.com, Inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.rx3grpc;

import io.grpc.*;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import io.reactivex.rxjava3.subscribers.TestSubscriber;
import org.junit.*;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("Duplicates")
public class DoNotCallUntilSubscribeIntegrationTest {
    @Rule
    public UnhandledRxJavaErrorRule errorRule = new UnhandledRxJavaErrorRule().autoVerifyNoError();

    private Server server;
    private ManagedChannel channel;
    private WasCalledInterceptor interceptor;

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
        Rx3GreeterGrpc.GreeterImplBase svc = new Rx3GreeterGrpc.GreeterImplBase() {

            @Override
            public Single<HelloResponse> sayHello(HelloRequest protoRequest) {
                return Single.fromCallable(() -> greet("Hello", protoRequest));
            }

            @Override
            public Flowable<HelloResponse> sayHelloRespStream(HelloRequest protoRequest) {
                return Flowable.just(
                        greet("Hello", protoRequest),
                        greet("Hi", protoRequest),
                        greet("Greetings", protoRequest));
            }

            @Override
            public Single<HelloResponse> sayHelloReqStream(Flowable<HelloRequest> rxRequest) {
                return rxRequest
                        .map(HelloRequest::getName)
                        .toList()
                        .map(names -> greet("Hello", String.join(" and ", names)));
            }

            @Override
            public Flowable<HelloResponse> sayHelloBothStream(Flowable<HelloRequest> rxRequest) {
                return rxRequest
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

        interceptor = new WasCalledInterceptor();
        server = ServerBuilder.forPort(9000).addService(svc).intercept(interceptor).build().start();
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
    public void oneToOne() throws InterruptedException {
        Rx3GreeterGrpc.RxGreeterStub stub = Rx3GreeterGrpc.newRxStub(channel);
        Single<HelloRequest> req = Single.just(HelloRequest.newBuilder().setName("rxjava").build());
        Single<HelloResponse> resp = req.compose(stub::sayHello);

        Thread.sleep(100);
        assertThat(interceptor.wasCalled()).isFalse();
        assertThat(interceptor.didRespond()).isFalse();
    }

    @Test
    public void oneToMany() throws InterruptedException {
        Rx3GreeterGrpc.RxGreeterStub stub = Rx3GreeterGrpc.newRxStub(channel);
        Single<HelloRequest> req = Single.just(HelloRequest.newBuilder().setName("rxjava").build());
        Flowable<HelloResponse> resp = req.to(stub::sayHelloRespStream);

        Thread.sleep(100);
        assertThat(interceptor.wasCalled()).isFalse();
        assertThat(interceptor.didRespond()).isFalse();
    }

    @Test
    public void manyToOne() throws InterruptedException {
        Rx3GreeterGrpc.RxGreeterStub stub = Rx3GreeterGrpc.newRxStub(channel);
        Flowable<HelloRequest> req = Flowable.just(
                HelloRequest.newBuilder().setName("a").build(),
                HelloRequest.newBuilder().setName("b").build(),
                HelloRequest.newBuilder().setName("c").build());

        Single<HelloResponse> resp = req.to(stub::sayHelloReqStream);

        Thread.sleep(100);
        assertThat(interceptor.wasCalled()).isFalse();
        assertThat(interceptor.didRespond()).isFalse();
    }

    @Test
    public void manyToMany() throws InterruptedException {
        Rx3GreeterGrpc.RxGreeterStub stub = Rx3GreeterGrpc.newRxStub(channel);
        Flowable<HelloRequest> req = Flowable.just(
                HelloRequest.newBuilder().setName("a").build(),
                HelloRequest.newBuilder().setName("b").build(),
                HelloRequest.newBuilder().setName("c").build(),
                HelloRequest.newBuilder().setName("d").build(),
                HelloRequest.newBuilder().setName("e").build());

        Flowable<HelloResponse> resp = req.compose(stub::sayHelloBothStream);

        Thread.sleep(100);
        assertThat(interceptor.wasCalled()).isFalse();
        assertThat(interceptor.didRespond()).isFalse();
    }
}
