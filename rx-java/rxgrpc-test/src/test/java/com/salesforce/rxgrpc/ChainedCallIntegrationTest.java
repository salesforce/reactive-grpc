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
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

@SuppressWarnings("Duplicates")
public class ChainedCallIntegrationTest {
    private Server server;
    private ManagedChannel channel;

    @Before
    public void setupServer() throws Exception {
        GreeterGrpc.GreeterImplBase svc = new RxGreeterGrpc.GreeterImplBase() {

            @Override
            public Single<HelloResponse> sayHello(Single<HelloRequest> rxRequest) {
                return rxRequest.map(protoRequest -> response("[" + protoRequest.getName() + "]"));
            }

            @Override
            public Flowable<HelloResponse> sayHelloRespStream(Single<HelloRequest> rxRequest) {
                return rxRequest
                        .map(HelloRequest::getName)
                        .flatMapPublisher(name -> Flowable.just(
                            response("{" + name + "}"),
                            response("/" + name + "/"),
                            response("\\" + name + "\\"),
                            response("(" + name + ")"))
                        );
            }

            @Override
            public Single<HelloResponse> sayHelloReqStream(Flowable<HelloRequest> rxRequest) {
                return rxRequest
                        .map(HelloRequest::getName)
                        .reduce((l, r) -> l + " :: " + r)
                        .toSingle("EMPTY")
                        .map(ChainedCallIntegrationTest::response);
            }

            @Override
            public Flowable<HelloResponse> sayHelloBothStream(Flowable<HelloRequest> rxRequest) {
                return rxRequest
                        .map(HelloRequest::getName)
                        .map(name -> "<" + name + ">")
                        .map(ChainedCallIntegrationTest::response);
            }
        };

        server = ServerBuilder.forPort(0).addService(svc).build().start();
        channel = ManagedChannelBuilder.forAddress("localhost", server.getPort()).usePlaintext(true).build();
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
    public void servicesCanCallOtherServices() throws InterruptedException {
        RxGreeterGrpc.RxGreeterStub stub = RxGreeterGrpc.newRxStub(channel);

        Single<HelloRequest> input = Single.just(request("X"));
        Single<HelloRequest> one = stub.sayHello(input)
                .map(ChainedCallIntegrationTest::bridge)
                .doOnSuccess(System.out::println);
        Flowable<HelloRequest> two = stub.sayHelloRespStream(one)
                .map(ChainedCallIntegrationTest::bridge)
                .doOnNext(System.out::println);
        Flowable<HelloRequest> three = stub.sayHelloBothStream(two)
                .map(ChainedCallIntegrationTest::bridge)
                .doOnNext(System.out::println);
        Single<HelloRequest> four = stub.sayHelloReqStream(three)
                .map(ChainedCallIntegrationTest::bridge)
                .doOnSuccess(System.out::println);
        Single<String> five = stub.sayHello(four)
                .map(HelloResponse::getMessage)
                .doOnSuccess(System.out::println);

        TestObserver<String> test = five.test();

        test.awaitTerminalEvent(2, TimeUnit.SECONDS);
        test.assertComplete();
        test.assertValue("[<{[X]}> :: </[X]/> :: <\\[X]\\> :: <([X])>]");
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
