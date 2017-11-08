/*
 *  Copyright (c) 2017, salesforce.com, inc.
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
import org.junit.Test;

import java.util.concurrent.TimeUnit;

@SuppressWarnings("ALL")
public class UnexpectedServerErrorIntegrationTest {
    private static Server server;
    private static ManagedChannel channel;

    @BeforeClass
    public static void setupServer() throws Exception {
        GreeterGrpc.GreeterImplBase svc = new RxGreeterGrpc.GreeterImplBase() {
            @Override
            public Single<HelloResponse> sayHello(Single<HelloRequest> rxRequest) {
                return rxRequest.map(this::map);
            }

            @Override
            public Flowable<HelloResponse> sayHelloRespStream(Single<HelloRequest> rxRequest) {
                return rxRequest.map(this::map).toFlowable();
            }

            @Override
            public Single<HelloResponse> sayHelloReqStream(Flowable<HelloRequest> rxRequest) {
                return rxRequest.map(this::map).firstOrError();
            }

            @Override
            public Flowable<HelloResponse> sayHelloBothStream(Flowable<HelloRequest> rxRequest) {
                return rxRequest.map(this::map);
            }

            private HelloResponse map(HelloRequest request) throws Exception{
                throw Status.INTERNAL.withDescription("Kaboom!").asException();
            }
        };

        server = ServerBuilder.forPort(0).addService(svc).build().start();
        channel = ManagedChannelBuilder.forAddress("localhost", server.getPort()).usePlaintext(true).build();
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
        Single<HelloResponse> resp = stub.sayHello(Single.just(HelloRequest.getDefaultInstance()));
        TestObserver<HelloResponse> test = resp.test();

        test.awaitTerminalEvent(3, TimeUnit.SECONDS);
        test.assertError(t -> t instanceof StatusRuntimeException);
        test.assertError(t -> ((StatusRuntimeException)t).getStatus().getCode() == Status.Code.INTERNAL);
    }

    @Test
    public void oneToMany() {
        RxGreeterGrpc.RxGreeterStub stub = RxGreeterGrpc.newRxStub(channel);
        Flowable<HelloResponse> resp = stub.sayHelloRespStream(Single.just(HelloRequest.getDefaultInstance()));
        TestSubscriber<HelloResponse> test = resp
                .doOnNext(msg -> System.out.println(msg))
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
        Single<HelloResponse> resp = stub.sayHelloReqStream(req);
        TestObserver<HelloResponse> test = resp.test();

        test.awaitTerminalEvent(3, TimeUnit.SECONDS);
        test.assertError(t -> t instanceof StatusRuntimeException);
        // Flowable requests get canceled when unexpected errors happen
        test.assertError(t -> ((StatusRuntimeException)t).getStatus().getCode() == Status.Code.CANCELLED);
    }

    @Test
    public void manyToMany() {
        RxGreeterGrpc.RxGreeterStub stub = RxGreeterGrpc.newRxStub(channel);
        Flowable<HelloRequest> req = Flowable.just(HelloRequest.getDefaultInstance());
        Flowable<HelloResponse> resp = stub.sayHelloBothStream(req);
        TestSubscriber<HelloResponse> test = resp.test();

        test.awaitTerminalEvent(3, TimeUnit.SECONDS);
        test.assertError(t -> t instanceof StatusRuntimeException);
        test.assertError(t -> ((StatusRuntimeException)t).getStatus().getCode() == Status.Code.CANCELLED);
    }
}
