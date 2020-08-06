/*
 *  Copyright (c) 2019, Salesforce.com, Inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.rxgrpc;

import java.util.concurrent.TimeUnit;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import io.reactivex.rxjava3.subscribers.TestSubscriber;

@SuppressWarnings("unchecked")
public class ServerErrorIntegrationTest {
    @Rule
    public UnhandledRxJavaErrorRule errorRule = new UnhandledRxJavaErrorRule().autoVerifyNoError();

    private static Server server;
    private static ManagedChannel channel;

    @BeforeClass
    public static void setupServer() throws Exception {
        RxGreeterGrpc.GreeterImplBase svc = new RxGreeterGrpc.GreeterImplBase() {
            @Override
            public Single<HelloResponse> sayHello(Single<HelloRequest> rxRequest) {
                return Single.error(new StatusRuntimeException(Status.INTERNAL));
            }

            @Override
            public Flowable<HelloResponse> sayHelloRespStream(Single<HelloRequest> rxRequest) {
                return Flowable.error(new StatusRuntimeException(Status.INTERNAL));
            }

            @Override
            public Single<HelloResponse> sayHelloReqStream(Flowable<HelloRequest> rxRequest) {
                return Single.error(new StatusRuntimeException(Status.INTERNAL));
            }

            @Override
            public Flowable<HelloResponse> sayHelloBothStream(Flowable<HelloRequest> rxRequest) {
                return Flowable.error(new StatusRuntimeException(Status.INTERNAL));
            }
        };

        server = ServerBuilder.forPort(9000).addService(svc).build().start();
        channel = ManagedChannelBuilder.forAddress("localhost", server.getPort()).usePlaintext().build();
    }

    @AfterClass
    public static void stopServer() {
        server.shutdown();
        channel.shutdown();

        server = null;
        channel = null;
    }

    @Test
    public void oneToOne() throws InterruptedException {
        RxGreeterGrpc.RxGreeterStub stub = RxGreeterGrpc.newRxStub(channel);
        Single<HelloResponse> resp = Single.just(HelloRequest.getDefaultInstance()).compose(stub::sayHello);
        TestObserver<HelloResponse> test = resp.test();

        test.await(3, TimeUnit.SECONDS);
        test.assertError(t -> t instanceof StatusRuntimeException);
        test.assertError(t -> ((StatusRuntimeException)t).getStatus() == Status.INTERNAL);
    }

    @Test
    public void oneToMany() throws InterruptedException {
        RxGreeterGrpc.RxGreeterStub stub = RxGreeterGrpc.newRxStub(channel);
        Flowable<HelloResponse> resp = Single.just(HelloRequest.getDefaultInstance()).to(stub::sayHelloRespStream);
        TestSubscriber<HelloResponse> test = resp
                .doOnNext(System.out::println)
                .doOnError(throwable -> System.out.println(throwable.getMessage()))
                .doOnComplete(() -> System.out.println("Completed"))
                .doOnCancel(() -> System.out.println("Client canceled"))
                .test();

        test.await(3, TimeUnit.SECONDS);
        test.assertError(t -> t instanceof StatusRuntimeException);
        test.assertError(t -> ((StatusRuntimeException)t).getStatus() == Status.INTERNAL);
    }

    @Test
    public void manyToOne() throws InterruptedException {
        RxGreeterGrpc.RxGreeterStub stub = RxGreeterGrpc.newRxStub(channel);
        Single<HelloResponse> resp = Flowable.just(HelloRequest.getDefaultInstance()).to(stub::sayHelloReqStream);
        TestObserver<HelloResponse> test = resp.test();

        test.await(3, TimeUnit.SECONDS);
        test.assertError(t -> t instanceof StatusRuntimeException);
        test.assertError(t -> ((StatusRuntimeException)t).getStatus() == Status.INTERNAL);
    }

    @Test
    public void manyToMany() throws InterruptedException {
        RxGreeterGrpc.RxGreeterStub stub = RxGreeterGrpc.newRxStub(channel);
        Flowable<HelloResponse> resp = Flowable.just(HelloRequest.getDefaultInstance()).compose(stub::sayHelloBothStream);
        TestSubscriber<HelloResponse> test = resp.test();

        test.await(3, TimeUnit.SECONDS);
        test.assertError(t -> t instanceof StatusRuntimeException);
        test.assertError(t -> ((StatusRuntimeException)t).getStatus() == Status.INTERNAL);
    }
}
