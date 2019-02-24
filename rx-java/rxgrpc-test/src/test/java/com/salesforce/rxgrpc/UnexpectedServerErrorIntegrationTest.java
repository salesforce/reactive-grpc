/*
 *  Copyright (c) 2019, Salesforce.com, Inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.rxgrpc;

import io.grpc.*;
import io.reactivex.Flowable;
import io.reactivex.Single;
import io.reactivex.observers.TestObserver;
import io.reactivex.subscribers.TestSubscriber;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

@SuppressWarnings("unchecked")
public class UnexpectedServerErrorIntegrationTest {
    @Rule
    public UnhandledRxJavaErrorRule errorRule = new UnhandledRxJavaErrorRule().autoVerifyNoError();

    private static Server server;
    private static ManagedChannel channel;

    @BeforeClass
    public static void setupServer() throws Exception {
        RxGreeterGrpc.GreeterImplBase svc = new RxGreeterGrpc.GreeterImplBase() {
            @Override
            public Single<HelloResponse> sayHello(Single<HelloRequest> rxRequest) {
                return rxRequest.map(this::kaboom);
            }

            @Override
            public Flowable<HelloResponse> sayHelloRespStream(Single<HelloRequest> rxRequest) {
                return rxRequest.map(this::kaboom).toFlowable();
            }

            @Override
            public Single<HelloResponse> sayHelloReqStream(Flowable<HelloRequest> rxRequest) {
                return rxRequest.map(this::kaboom).firstOrError();
            }

            @Override
            public Flowable<HelloResponse> sayHelloBothStream(Flowable<HelloRequest> rxRequest) {
                return rxRequest.map(this::kaboom);
            }

            private HelloResponse kaboom(HelloRequest request) throws Exception{
                throw Status.INTERNAL.withDescription("Kaboom!").asException();
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
    public void oneToOne() {
        RxGreeterGrpc.RxGreeterStub stub = RxGreeterGrpc.newRxStub(channel);
        Single<HelloResponse> resp = Single.just(HelloRequest.getDefaultInstance()).compose(stub::sayHello);
        TestObserver<HelloResponse> test = resp.test();

        test.awaitTerminalEvent(3, TimeUnit.SECONDS);
        test.assertError(t -> t instanceof StatusRuntimeException);
        test.assertError(t -> ((StatusRuntimeException)t).getStatus().getCode() == Status.Code.INTERNAL);
    }

    @Test
    public void oneToMany() {
        RxGreeterGrpc.RxGreeterStub stub = RxGreeterGrpc.newRxStub(channel);
        Flowable<HelloResponse> resp = Single.just(HelloRequest.getDefaultInstance()).as(stub::sayHelloRespStream);
        TestSubscriber<HelloResponse> test = resp
                .doOnNext(System.out::println)
                .doOnError(throwable -> System.out.println(throwable.getMessage()))
                .doOnComplete(() -> System.out.println("Completed"))
                .doOnCancel(() -> System.out.println("Client canceled"))
                .test();

        test.awaitTerminalEvent(3, TimeUnit.SECONDS);
        test.assertError(t -> t instanceof StatusRuntimeException);
        test.assertError(t -> ((StatusRuntimeException)t).getStatus().getCode() == Status.Code.INTERNAL);
    }

    @Test
    public void manyToOne() {
        RxGreeterGrpc.RxGreeterStub stub = RxGreeterGrpc.newRxStub(channel);
        Flowable<HelloRequest> req = Flowable.just(HelloRequest.getDefaultInstance());
        Single<HelloResponse> resp = req.as(stub::sayHelloReqStream);
        TestObserver<HelloResponse> test = resp.test();

        test.awaitTerminalEvent(3, TimeUnit.SECONDS);
        test.assertError(t -> t instanceof StatusRuntimeException);
        // Flowable requests get canceled when unexpected errors happen
        test.assertError(t -> ((StatusRuntimeException)t).getStatus().getCode() == Status.Code.INTERNAL);
    }

    @Test
    public void manyToMany() {
        RxGreeterGrpc.RxGreeterStub stub = RxGreeterGrpc.newRxStub(channel);
        Flowable<HelloRequest> req = Flowable.just(HelloRequest.getDefaultInstance());
        Flowable<HelloResponse> resp = req.compose(stub::sayHelloBothStream);
        TestSubscriber<HelloResponse> test = resp.test();

        test.awaitTerminalEvent(3, TimeUnit.SECONDS);
        test.assertError(t -> t instanceof StatusRuntimeException);
        test.assertError(t -> ((StatusRuntimeException)t).getStatus().getCode() == Status.Code.INTERNAL);
    }
}
