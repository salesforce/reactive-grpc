/*
 *  Copyright (c) 2017, salesforce.com, inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.rxgrpc;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.reactivex.Flowable;
import io.reactivex.Single;
import io.reactivex.observers.TestObserver;
import io.reactivex.subscribers.TestSubscriber;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@SuppressWarnings("Duplicates")
public class EndToEndIntegrationTest {
    private static Server server;
    private static ManagedChannel channel;

    @BeforeClass
    public static void setupServer() throws Exception {
        GreeterGrpc.GreeterImplBase svc = new RxGreeterGrpc.GreeterImplBase() {

            @Override
            public Single<HelloResponse> sayHello(Single<HelloRequest> rxRequest) {
                return rxRequest.map(protoRequest -> greet("Hello", protoRequest));
            }

            @Override
            public Flowable<HelloResponse> sayHelloRespStream(Single<HelloRequest> rxRequest) {
                return rxRequest.flatMapPublisher(protoRequest -> Flowable.just(
                        greet("Hello", protoRequest),
                        greet("Hi", protoRequest),
                        greet("Greetings", protoRequest)));
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

        server = ServerBuilder.forPort(0).addService(svc).build().start();
        channel = ManagedChannelBuilder.forAddress("localhost", server.getPort()).usePlaintext(true).build();
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
    public void oneToOne() throws IOException {
        RxGreeterGrpc.RxGreeterStub stub = RxGreeterGrpc.newRxStub(channel);
        Single<HelloRequest> req = Single.just(HelloRequest.newBuilder().setName("rxjava").build());
        Single<HelloResponse> resp = stub.sayHello(req);

        TestObserver<String> testObserver = resp.map(HelloResponse::getMessage).test();
        testObserver.awaitTerminalEvent(3, TimeUnit.SECONDS);
        testObserver.assertValue("Hello rxjava");
    }

    @Test
    public void oneToMany() throws IOException {
        RxGreeterGrpc.RxGreeterStub stub = RxGreeterGrpc.newRxStub(channel);
        Single<HelloRequest> req = Single.just(HelloRequest.newBuilder().setName("rxjava").build());
        Flowable<HelloResponse> resp = stub.sayHelloRespStream(req);

        TestSubscriber<String> testSubscriber = resp.map(HelloResponse::getMessage).test();
        testSubscriber.awaitTerminalEvent(3, TimeUnit.SECONDS);
        testSubscriber.assertValues("Hello rxjava", "Hi rxjava", "Greetings rxjava");
    }

    @Test
    public void manyToOne() throws Exception {
        RxGreeterGrpc.RxGreeterStub stub = RxGreeterGrpc.newRxStub(channel);
        Flowable<HelloRequest> req = Flowable.just(
                HelloRequest.newBuilder().setName("a").build(),
                HelloRequest.newBuilder().setName("b").build(),
                HelloRequest.newBuilder().setName("c").build());

        Single<HelloResponse> resp = stub.sayHelloReqStream(req);

        TestObserver<String> testObserver = resp.map(HelloResponse::getMessage).test();
        testObserver.awaitTerminalEvent(3, TimeUnit.SECONDS);
        testObserver.assertValue("Hello a and b and c");
    }

    @Test
    public void manyToMany() throws Exception {
        RxGreeterGrpc.RxGreeterStub stub = RxGreeterGrpc.newRxStub(channel);
        Flowable<HelloRequest> req = Flowable.just(
                HelloRequest.newBuilder().setName("a").build(),
                HelloRequest.newBuilder().setName("b").build(),
                HelloRequest.newBuilder().setName("c").build(),
                HelloRequest.newBuilder().setName("d").build(),
                HelloRequest.newBuilder().setName("e").build());

        Flowable<HelloResponse> resp = stub.sayHelloBothStream(req);

        TestSubscriber<String> testSubscriber = resp.map(HelloResponse::getMessage).test();
        testSubscriber.awaitTerminalEvent(3, TimeUnit.SECONDS);
        testSubscriber.assertValues("Hello a and b", "Hello c and d", "Hello e");
        testSubscriber.assertComplete();
    }
}
