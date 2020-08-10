/*
 *  Copyright (c) 2019, Salesforce.com, Inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.rx3grpc;

import java.util.concurrent.TimeUnit;

import org.junit.Rule;
import org.junit.Test;

import com.salesforce.grpc.testing.contrib.NettyGrpcServerRule;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;

@SuppressWarnings("Duplicates")
public class UnaryZeroMessageResponseIntegrationTest {
    @Rule
    public NettyGrpcServerRule serverRule = new NettyGrpcServerRule();

    @Rule
    public UnhandledRxJavaErrorRule errorRule = new UnhandledRxJavaErrorRule().autoVerifyNoError();

    private static class MissingUnaryResponseService extends GreeterGrpc.GreeterImplBase {
        @Override
        public void sayHello(HelloRequest request, StreamObserver<HelloResponse> responseObserver) {
            responseObserver.onCompleted();
        }

        @Override
        public StreamObserver<HelloRequest> sayHelloReqStream(StreamObserver<HelloResponse> responseObserver) {
            return new StreamObserver<HelloRequest>() {
                @Override
                public void onNext(HelloRequest helloRequest) {
                    responseObserver.onCompleted();
                }

                @Override
                public void onError(Throwable throwable) {

                }

                @Override
                public void onCompleted() {

                }
            };
        }
    }

    @Test
    public void zeroMessageResponseOneToOne() throws InterruptedException {
        serverRule.getServiceRegistry().addService(new MissingUnaryResponseService());

        Rx3GreeterGrpc.RxGreeterStub stub = Rx3GreeterGrpc.newRxStub(serverRule.getChannel());
        Single<HelloRequest> req = Single.just(HelloRequest.newBuilder().setName("rxjava").build());
        Single<HelloResponse> resp = req.compose(stub::sayHello);

        TestObserver<String> testObserver = resp.map(HelloResponse::getMessage).test();
        testObserver.await(3, TimeUnit.SECONDS);
        testObserver.assertError(StatusRuntimeException.class);
        testObserver.assertError(t -> ((StatusRuntimeException) t).getStatus().getCode() == Status.CANCELLED.getCode());
    }

    @Test
    public void zeroMessageResponseManyToOne() throws InterruptedException {
        serverRule.getServiceRegistry().addService(new MissingUnaryResponseService());

        Rx3GreeterGrpc.RxGreeterStub stub = Rx3GreeterGrpc.newRxStub(serverRule.getChannel());
        Flowable<HelloRequest> req = Flowable.just(
                HelloRequest.newBuilder().setName("a").build(),
                HelloRequest.newBuilder().setName("b").build(),
                HelloRequest.newBuilder().setName("c").build());

        Single<HelloResponse> resp = req.to(stub::sayHelloReqStream);

        TestObserver<String> testObserver = resp.map(HelloResponse::getMessage).test();
        testObserver.await(3, TimeUnit.SECONDS);
        testObserver.assertError(StatusRuntimeException.class);
        testObserver.assertError(t -> ((StatusRuntimeException) t).getStatus().getCode() == Status.CANCELLED.getCode());
    }
}
