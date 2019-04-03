/*
 *  Copyright (c) 2019, Salesforce.com, Inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.rxgrpc;

import com.google.common.collect.Lists;
import com.salesforce.grpc.testing.contrib.NettyGrpcServerRule;
import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.flowables.ConnectableFlowable;
import io.reactivex.observers.TestObserver;
import org.junit.Rule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test to demonstrate splitting the output of an RxGrpc stream in RxJava.
 * See: https://github.com/salesforce/reactive-grpc/issues/131
 */
public class ShareIntegrationTest {
    @Rule
    public NettyGrpcServerRule serverRule = new NettyGrpcServerRule();

    @Test
    public void serverPublishShouldWork() {
        RxGreeterGrpc.GreeterImplBase svc = new RxGreeterGrpc.GreeterImplBase() {
            @Override
            public Single<HelloResponse> sayHelloReqStream(Flowable<HelloRequest> rxRequest) {
                return rxRequest
                    // a function that can use the multicasted source sequence as many times as needed, without causing
                    // multiple subscriptions to the source sequence. Subscribers to the given source will receive all
                    // notifications of the source from the time of the subscription forward.
                    .publish(shared -> {
                        Single<HelloRequest> first = shared.firstOrError();
                        Flowable<HelloRequest> rest = shared.skip(0);
                        return first
                            .flatMap(firstVal -> rest
                                .map(HelloRequest::getName)
                                .toList()
                                .map(names -> {
                                            ArrayList<String> strings = Lists.newArrayList(firstVal.getName());
                                            strings.addAll(names);
                                            Thread.sleep(1000);
                                            return HelloResponse.newBuilder().setMessage("Hello " + String.join(" and ", strings)).build();
                                        }
                                ).doOnError(System.out::println))
                            .toFlowable();
                    })
                    .singleOrError();
            }
        };

        serverRule.getServiceRegistry().addService(svc);
        RxGreeterGrpc.RxGreeterStub stub = RxGreeterGrpc.newRxStub(serverRule.getChannel());

        TestObserver<String> resp = Flowable.just("Alpha", "Bravo", "Charlie")
                .map(s -> HelloRequest.newBuilder().setName(s).build())
                .as(stub::sayHelloReqStream)
                .map(HelloResponse::getMessage)
                .test();

        resp.awaitTerminalEvent(5, TimeUnit.SECONDS);
        resp.assertComplete();
        resp.assertValue("Hello Alpha and Bravo and Charlie");
    }

    @Test
    public void clientPublishShouldWork() {
        RxGreeterGrpc.GreeterImplBase svc = new RxGreeterGrpc.GreeterImplBase() {
            @Override
            public Flowable<HelloResponse> sayHelloRespStream(Single<HelloRequest> request) {
                return request.flatMapObservable(x -> Observable.just("Alpha", "Bravo", "Charlie"))
                        .map(name -> HelloResponse.newBuilder().setMessage(name).build())
                        .toFlowable(BackpressureStrategy.BUFFER);
            }
        };

        serverRule.getServiceRegistry().addService(svc);
        RxGreeterGrpc.RxGreeterStub stub = RxGreeterGrpc.newRxStub(serverRule.getChannel());

        TestObserver<String> resp = stub.sayHelloRespStream(HelloRequest.getDefaultInstance())
            // a function that can use the multicasted source sequence as many times as needed, without causing
            // multiple subscriptions to the source sequence. Subscribers to the given source will receive all
            // notifications of the source from the time of the subscription forward.
            .publish(shared -> {
                Single<HelloResponse> first = shared.firstOrError();
                Flowable<HelloResponse> rest = shared.skip(0);
                return first
                    .flatMap(firstVal -> rest
                        .map(HelloResponse::getMessage)
                        .toList()
                        .map(names -> {
                            ArrayList<String> strings = Lists.newArrayList(firstVal.getMessage());
                            strings.addAll(names);
                            Thread.sleep(1000);
                            return HelloResponse.newBuilder().setMessage("Hello " + String.join(" and ", strings)).build();
                        })
                        .doOnError(System.out::println)
                    )
                    .map(HelloResponse::getMessage)
                    .toFlowable();
                })
            .singleOrError()
            .test();

        resp.awaitTerminalEvent(5, TimeUnit.SECONDS);
        resp.assertComplete();
        resp.assertValue("Hello Alpha and Bravo and Charlie");
    }

    @Test
    public void serverShareShouldWork() {
        AtomicReference<String> other = new AtomicReference<>();

        RxGreeterGrpc.GreeterImplBase svc = new RxGreeterGrpc.GreeterImplBase() {
            @Override
            public Single<HelloResponse> sayHelloReqStream(Flowable<HelloRequest> request) {
                Flowable<HelloRequest> share = request.share();

                // Let's make a side effect in a different stream!
                share
                    .map(HelloRequest::getName)
                    .reduce("", (l, r) -> l + "+" + r)
                    .subscribe(other::set);

                return share
                        .map(HelloRequest::getName)
                        .reduce("", (l, r) -> l + "&" + r)
                        .map(m -> HelloResponse.newBuilder().setMessage(m).build());
            }
        };

        serverRule.getServiceRegistry().addService(svc);
        RxGreeterGrpc.RxGreeterStub stub = RxGreeterGrpc.newRxStub(serverRule.getChannel());

        TestObserver<String> resp = Flowable.just("Alpha", "Bravo", "Charlie")
                .map(n -> HelloRequest.newBuilder().setName(n).build())
                .as(stub::sayHelloReqStream)
                .map(HelloResponse::getMessage)
                .test();

        resp.awaitTerminalEvent(1, TimeUnit.SECONDS);
        resp.assertComplete();
        resp.assertValue("&Alpha&Bravo&Charlie");

        assertThat(other.get()).isEqualTo("+Alpha+Bravo+Charlie");
    }

    @Test
    public void clientShareShouldWork() {
        RxGreeterGrpc.GreeterImplBase svc = new RxGreeterGrpc.GreeterImplBase() {
            @Override
            public Flowable<HelloResponse> sayHelloRespStream(Single<HelloRequest> request) {
                return request
                        // Always return Alpha, Bravo, Charlie
                        .flatMapPublisher(x -> {
                            System.out.println("Flatten : " + x);
                            return Flowable.just("Alpha", "Bravo", "Charlie");
                        })
                        .map(n -> HelloResponse.newBuilder().setMessage(n).build());
            }
        };

        serverRule.getServiceRegistry().addService(svc);
        RxGreeterGrpc.RxGreeterStub stub = RxGreeterGrpc.newRxStub(serverRule.getChannel());

        Flowable<HelloResponse> share = Single.just(HelloRequest.getDefaultInstance())
                .as(stub::sayHelloRespStream)
                .share();

        // Split the response stream!
        TestObserver<String> resp1 = share
            .map(HelloResponse::getMessage)
            .reduce("", (l, r) -> l + "+" + r)
            .test();

        TestObserver<String> resp2 = share
                .map(HelloResponse::getMessage)
                .reduce("", (l, r) -> l + "&" + r)
                .test();

        resp1.awaitTerminalEvent(1, TimeUnit.SECONDS);
        resp1.assertComplete();
        resp1.assertValue("+Alpha+Bravo+Charlie");

        resp2.awaitTerminalEvent(1, TimeUnit.SECONDS);
        resp2.assertComplete();
        resp2.assertValue("&Alpha&Bravo&Charlie");
    }
}
