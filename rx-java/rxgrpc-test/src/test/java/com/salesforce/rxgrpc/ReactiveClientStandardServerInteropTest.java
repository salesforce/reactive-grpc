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
import io.grpc.stub.StreamObserver;
import io.reactivex.Flowable;
import io.reactivex.Single;
import io.reactivex.observers.TestObserver;
import io.reactivex.subscribers.TestSubscriber;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

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
        RxGreeterGrpc.RxGreeterStub stub = RxGreeterGrpc.newRxStub(channel);
        Single<String> rxRequest = Single.just("World");
        Single<String> rxResponse = stub.sayHello(rxRequest.map(this::toRequest)).map(this::fromResponse);

        TestObserver<String> test = rxResponse.test();
        test.awaitTerminalEvent(1, TimeUnit.SECONDS);

        test.assertNoErrors();
        test.assertValue("Hello World");
    }

    @Test
    public void oneToMany() {
        RxGreeterGrpc.RxGreeterStub stub = RxGreeterGrpc.newRxStub(channel);
        Single<String> rxRequest = Single.just("World");
        Flowable<String> rxResponse = stub.sayHelloRespStream(rxRequest.map(this::toRequest)).map(this::fromResponse);

        TestSubscriber<String> test = rxResponse.test();
        test.awaitTerminalEvent(1, TimeUnit.SECONDS);

        test.assertNoErrors();
        test.assertValues("Hello World", "Hi World", "Greetings World");
    }

    @Test
    public void manyToOne() {
        RxGreeterGrpc.RxGreeterStub stub = RxGreeterGrpc.newRxStub(channel);
        Flowable<String> rxRequest = Flowable.just("A", "B", "C");
        Single<String> rxResponse = stub.sayHelloReqStream(rxRequest.map(this::toRequest)).map(this::fromResponse);

        TestObserver<String> test = rxResponse.test();
        test.awaitTerminalEvent(1, TimeUnit.SECONDS);

        test.assertNoErrors();
        test.assertValue("Hello A and B and C");
    }

    @Test
    public void manyToMany() {
        RxGreeterGrpc.RxGreeterStub stub = RxGreeterGrpc.newRxStub(channel);
        Flowable<String> rxRequest = Flowable.just("A", "B", "C", "D");
        Flowable<String> rxResponse = stub.sayHelloBothStream(rxRequest.map(this::toRequest)).map(this::fromResponse);

        TestSubscriber<String> test = rxResponse.test();
        test.awaitTerminalEvent(1, TimeUnit.SECONDS);

        test.assertNoErrors();
        test.assertValues("Hello A and B", "Hello C and D");
    }

    private HelloRequest toRequest(String name) {
        return HelloRequest.newBuilder().setName(name).build();
    }

    private String fromResponse(HelloResponse response) {
        return response.getMessage();
    }
}
