/*
 *  Copyright (c) 2017, salesforce.com, inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.rxgrpc;

import com.salesforce.grpc.contrib.LambdaStreamObserver;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import io.reactivex.Flowable;
import io.reactivex.Single;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.core.IsEqual.equalTo;

@SuppressWarnings("Duplicates")
public class StandardClientReactiveServerInteropTest {
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
    public void oneToOne() {
        AtomicBoolean called = new AtomicBoolean(false);
        GreeterGrpc.GreeterStub stub = GreeterGrpc.newStub(channel);

        HelloRequest request = HelloRequest.newBuilder().setName("World").build();
        stub.sayHello(request, new LambdaStreamObserver<>(
                response -> {
                    assertThat(response.getMessage()).isEqualTo("Hello World");
                    called.set(true);
                }
        ));

        await().atMost(1, TimeUnit.SECONDS).untilTrue(called);
    }

    @Test
    public void oneToMany() {
        AtomicInteger called = new AtomicInteger(0);
        GreeterGrpc.GreeterStub stub = GreeterGrpc.newStub(channel);

        HelloRequest request = HelloRequest.newBuilder().setName("World").build();
        stub.sayHelloRespStream(request, new LambdaStreamObserver<>(
                response -> {
                    assertThat(response.getMessage()).isIn("Hello World", "Hi World", "Greetings World");
                    called.incrementAndGet();
                }
        ));

        await().atMost(1, TimeUnit.SECONDS).untilAtomic(called, equalTo(3));
    }

    @Test
    public void manyToOne() {
        AtomicBoolean called = new AtomicBoolean(false);
        GreeterGrpc.GreeterStub stub = GreeterGrpc.newStub(channel);

        StreamObserver<HelloRequest> requestStream = stub.sayHelloReqStream(new LambdaStreamObserver<>(
                response -> {
                    assertThat(response.getMessage()).isEqualTo("Hello A and B and C");
                    called.set(true);
                }
        ));

        requestStream.onNext(HelloRequest.newBuilder().setName("A").build());
        requestStream.onNext(HelloRequest.newBuilder().setName("B").build());
        requestStream.onNext(HelloRequest.newBuilder().setName("C").build());
        requestStream.onCompleted();

        await().atMost(1, TimeUnit.SECONDS).untilTrue(called);
    }

    @Test
    public void manyToMany() {
        AtomicInteger called = new AtomicInteger(0);
        GreeterGrpc.GreeterStub stub = GreeterGrpc.newStub(channel);

        StreamObserver<HelloRequest> requestStream = stub.sayHelloBothStream(new LambdaStreamObserver<>(
                response -> {
                    assertThat(response.getMessage()).isIn("Hello A and B", "Hello C and D");
                    called.incrementAndGet();
                }
        ));

        requestStream.onNext(HelloRequest.newBuilder().setName("A").build());
        requestStream.onNext(HelloRequest.newBuilder().setName("B").build());
        requestStream.onNext(HelloRequest.newBuilder().setName("C").build());
        requestStream.onNext(HelloRequest.newBuilder().setName("D").build());
        requestStream.onCompleted();

        await().atMost(1, TimeUnit.SECONDS).untilAtomic(called, equalTo(2));
    }
}
