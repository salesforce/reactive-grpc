/*
 *  Copyright (c) 2017, salesforce.com, inc.
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

/**
 * Test to demonstrate splitting the output of an RxGrpc stream in RxJava.
 * See: https://github.com/salesforce/reactive-grpc/issues/131
 */
public class ShareIntegrationTest {
    @Rule
    public NettyGrpcServerRule serverRule = new NettyGrpcServerRule();

    @Test
    public void serverShareShouldWork() {
        RxGreeterGrpc.GreeterImplBase svc = new RxGreeterGrpc.GreeterImplBase() {
            @Override
            public Single<HelloResponse> sayHelloReqStream(Flowable<HelloRequest> rxRequest) {
//                ConnectableFlowable<HelloRequest> shared = rxRequest
//                        .doOnSubscribe(x -> System.out.println("SUBSCRIBE"))
//                        .publish();

                Flowable<HelloRequest> shared = rxRequest
                        .doOnSubscribe(x -> System.out.println("SUBSCRIBE"))
                        .share();

                Single<HelloRequest> first = shared.firstOrError();
                Flowable<HelloRequest> rest = shared.skip(0);
                Single<HelloResponse> resp = first
                    .flatMap(firstVal -> rest
                        .map(HelloRequest::getName)
                        .toList()
                        .map(names -> {
                            ArrayList<String> strings = Lists.newArrayList(firstVal.getName());
                            strings.addAll(names);
                            Thread.sleep(1000);
                            return HelloResponse.newBuilder().setMessage("Hello " + String.join(" and ", strings)).build();
                        }
                    ).doOnError(System.out::println)
                );

//                shared.connect();
                return resp;
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
    public void clientShareShouldWork() {
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

//        ConnectableFlowable<HelloResponse> shared = stub.sayHelloRespStream(HelloRequest.getDefaultInstance())
//                .doOnSubscribe(x -> System.out.println("SUBSCRIBE"))
//                .publish();

        Flowable<HelloResponse> shared = stub.sayHelloRespStream(HelloRequest.getDefaultInstance())
                .doOnSubscribe(x -> System.out.println("SUBSCRIBE"))
                .share();

        Single<HelloResponse> first = shared.firstOrError();
        Flowable<HelloResponse> rest = shared.skip(0);
        TestObserver<String> resp = first
                .flatMap(firstVal -> rest
                        .map(HelloResponse::getMessage)
                        .toList()
                        .map(names -> {
                                    ArrayList<String> strings = Lists.newArrayList(firstVal.getMessage());
                                    strings.addAll(names);
                                    Thread.sleep(1000);
                                    return HelloResponse.newBuilder().setMessage("Hello " + String.join(" and ", strings)).build();
                                }
                        ).doOnError(System.out::println)
                )
                .map(HelloResponse::getMessage)
                .test();

//        shared.connect();

        resp.awaitTerminalEvent(5, TimeUnit.SECONDS);
        resp.assertComplete();
        resp.assertValue("Hello Alpha and Bravo and Charlie");
    }
}
