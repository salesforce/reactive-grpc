/*  Copyright (c) 2019, Salesforce.com, Inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.rxgrpc;

import com.google.protobuf.Empty;
import com.salesforce.grpc.testing.contrib.NettyGrpcServerRule;
import com.salesforce.servicelibs.NumberProto;
import com.salesforce.servicelibs.RxNumbersGrpc;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.reactivex.Flowable;
import io.reactivex.Single;
import io.reactivex.observers.TestObserver;
import io.reactivex.subscribers.TestSubscriber;
import org.junit.Rule;
import org.junit.Test;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

public class ServerErrorUpstreamCancellationIntegrationTest {
    @Rule
    public NettyGrpcServerRule serverRule = new NettyGrpcServerRule();

    @Rule
    public UnhandledRxJavaErrorRule errorRule = new UnhandledRxJavaErrorRule().autoVerifyNoError();

    private static class ExplodeAfterFiveService extends RxNumbersGrpc.NumbersImplBase {
        @Override
        public Flowable<NumberProto.Number> twoWayPressure(Flowable<NumberProto.Number> request) {
            return request.map(x -> kaboom());
        }

        @Override
        public Single<NumberProto.Number> requestPressure(Flowable<NumberProto.Number> request) {
            return request.map(x -> kaboom()).firstOrError();
        }

        @Override
        public Flowable<NumberProto.Number> responsePressure(Single<Empty> request) {
            return request.map(x -> kaboom()).toFlowable();
        }

        private NumberProto.Number kaboom() {
            throw Status.FAILED_PRECONDITION.asRuntimeException();
        }
    }

    @Test
    public void serverErrorSignalsUpstreamCancellationManyToOne() {
        serverRule.getServiceRegistry().addService(new ExplodeAfterFiveService());
        RxNumbersGrpc.RxNumbersStub stub = RxNumbersGrpc.newRxStub(serverRule.getChannel());

        AtomicBoolean upstreamCancel = new AtomicBoolean(false);
        AtomicReference<Throwable> throwable = new AtomicReference<>();

        TestObserver<NumberProto.Number> observer = Flowable.range(0, Integer.MAX_VALUE)
                .map(this::protoNum)
                .doOnCancel(() -> upstreamCancel.set(true))
                .as(stub::requestPressure)
                .doOnError(throwable::set)
                .doOnSuccess(i -> System.out.println(i.getNumber(0)))
                .test();

        observer.awaitTerminalEvent(3, TimeUnit.SECONDS);
        observer.assertError(StatusRuntimeException.class);
        assertThat(upstreamCancel.get()).isTrue();
        assertThat(((StatusRuntimeException) throwable.get()).getStatus()).isEqualTo(Status.FAILED_PRECONDITION);
    }

    @Test
    public void serverErrorSignalsUpstreamCancellationBidi() {
        serverRule.getServiceRegistry().addService(new ExplodeAfterFiveService());
        RxNumbersGrpc.RxNumbersStub stub = RxNumbersGrpc.newRxStub(serverRule.getChannel());

        AtomicBoolean upstreamCancel = new AtomicBoolean(false);

        TestSubscriber<NumberProto.Number> subscriber = Flowable.range(0, Integer.MAX_VALUE)
                .map(this::protoNum)
                .doOnCancel(() -> upstreamCancel.set(true))
                .compose(stub::twoWayPressure)
                .doOnNext(i -> System.out.println(i.getNumber(0)))
                .test();

        subscriber.awaitTerminalEvent(3, TimeUnit.SECONDS);
        subscriber.assertError(StatusRuntimeException.class);
        assertThat(upstreamCancel.get()).isTrue();
    }

    private NumberProto.Number protoNum(int i) {
        Integer[] ints = {i};
        return NumberProto.Number.newBuilder().addAllNumber(Arrays.asList(ints)).build();
    }
}
