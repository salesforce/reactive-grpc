/*
 *  Copyright (c) 2017, salesforce.com, inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.rxgrpc;

import com.google.common.collect.Lists;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
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

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("Duplicates")
public class ConcurrentRequestIntegrationTest {
    private static Server server;
    private static ManagedChannel channel;

    @BeforeClass
    public static void setupServer() throws Exception {
        GreeterGrpc.GreeterImplBase svc = new RxGreeterGrpc.GreeterImplBase() {

            @Override
            public Single<HelloResponse> sayHello(Single<HelloRequest> rxRequest) {
                return rxRequest
                        .doOnSuccess(System.out::println)
                        .map(protoRequest -> greet("Hello", protoRequest));
            }

            @Override
            public Flowable<HelloResponse> sayHelloRespStream(Single<HelloRequest> rxRequest) {
                return rxRequest
                        .doOnSuccess(System.out::println)
                        .flatMapPublisher(protoRequest -> Flowable.just(
                            greet("Hello", protoRequest),
                            greet("Hi", protoRequest),
                            greet("Greetings", protoRequest)));
            }

            @Override
            public Single<HelloResponse> sayHelloReqStream(Flowable<HelloRequest> rxRequest) {
                return rxRequest
                        .doOnNext(System.out::println)
                        .map(HelloRequest::getName)
                        .toList()
                        .map(names -> greet("Hello", String.join(" and ", names)));
            }

            @Override
            public Flowable<HelloResponse> sayHelloBothStream(Flowable<HelloRequest> rxRequest) {
                return rxRequest
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
    public void fourKindsOfRequestAtOnce() throws Exception {
        RxGreeterGrpc.RxGreeterStub stub = RxGreeterGrpc.newRxStub(channel);

        // == MAKE REQUESTS ==
        // One to One
        Single<HelloRequest> req1 = Single.just(HelloRequest.newBuilder().setName("rxjava").build());
        Single<HelloResponse> resp1 = stub.sayHello(req1);

        // One to Many
        Single<HelloRequest> req2 = Single.just(HelloRequest.newBuilder().setName("rxjava").build());
        Flowable<HelloResponse> resp2 = stub.sayHelloRespStream(req2);

        // Many to One
        Flowable<HelloRequest> req3 = Flowable.just(
                HelloRequest.newBuilder().setName("a").build(),
                HelloRequest.newBuilder().setName("b").build(),
                HelloRequest.newBuilder().setName("c").build());

        Single<HelloResponse> resp3 = stub.sayHelloReqStream(req3);

        // Many to Many
        Flowable<HelloRequest> req4 = Flowable.just(
                HelloRequest.newBuilder().setName("a").build(),
                HelloRequest.newBuilder().setName("b").build(),
                HelloRequest.newBuilder().setName("c").build(),
                HelloRequest.newBuilder().setName("d").build(),
                HelloRequest.newBuilder().setName("e").build());

        Flowable<HelloResponse> resp4 = stub.sayHelloBothStream(req4);

        // == VERIFY RESPONSES ==
        ListeningExecutorService executorService = MoreExecutors.listeningDecorator(Executors.newCachedThreadPool());

        // Run all four verifications in parallel
        try {
            // One to One
            ListenableFuture<Boolean> oneToOne = executorService.submit(() -> {
                TestObserver<String> testObserver1 = resp1.map(HelloResponse::getMessage).test();
                testObserver1.awaitTerminalEvent(1, TimeUnit.SECONDS);
                testObserver1.assertValue("Hello rxjava");
                return true;
            });

            // One to Many
            ListenableFuture<Boolean> oneToMany = executorService.submit(() -> {
                TestSubscriber<String> testSubscriber1 = resp2.map(HelloResponse::getMessage).test();
                testSubscriber1.awaitTerminalEvent(1, TimeUnit.SECONDS);
                testSubscriber1.assertValues("Hello rxjava", "Hi rxjava", "Greetings rxjava");
                return true;
            });

            // Many to One
            ListenableFuture<Boolean> manyToOne = executorService.submit(() -> {
                TestObserver<String> testObserver2 = resp3.map(HelloResponse::getMessage).test();
                testObserver2.awaitTerminalEvent(1, TimeUnit.SECONDS);
                testObserver2.assertValue("Hello a and b and c");
                return true;
            });

            // Many to Many
            ListenableFuture<Boolean> manyToMany = executorService.submit(() -> {
                TestSubscriber<String> testSubscriber2 = resp4.map(HelloResponse::getMessage).test();
                testSubscriber2.awaitTerminalEvent(1, TimeUnit.SECONDS);
                testSubscriber2.assertValues("Hello a and b", "Hello c and d", "Hello e");
                testSubscriber2.assertComplete();
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
