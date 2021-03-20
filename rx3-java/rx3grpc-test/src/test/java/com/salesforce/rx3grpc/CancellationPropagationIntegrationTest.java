/*
 *  Copyright (c) 2019, Salesforce.com, Inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.rx3grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import org.awaitility.Duration;
import org.junit.Rule;
import org.junit.Test;

import com.google.protobuf.Empty;
import com.salesforce.grpc.testing.contrib.NettyGrpcServerRule;
import com.salesforce.servicelibs.NumberProto;
import com.salesforce.servicelibs.NumberProto.Number;
import com.salesforce.servicelibs.Rx3NumbersGrpc;
import com.salesforce.servicelibs.Rx3NumbersGrpc.RxNumbersStub;

import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.observers.TestObserver;
import io.reactivex.rxjava3.subscribers.TestSubscriber;

@SuppressWarnings({"unchecked", "Duplicates"})
public class CancellationPropagationIntegrationTest {
    private static final int NUMBER_OF_STREAM_ELEMENTS = 10000;

    @Rule
    public NettyGrpcServerRule serverRule = new NettyGrpcServerRule();

    @Rule
    public UnhandledRxJavaErrorRule errorRule = new UnhandledRxJavaErrorRule();

    private static class TestService extends Rx3NumbersGrpc.NumbersImplBase {
        private AtomicInteger lastNumberProduced = new AtomicInteger(Integer.MIN_VALUE);
        private AtomicBoolean wasCanceled = new AtomicBoolean(false);
        private AtomicBoolean explicitCancel = new AtomicBoolean(false);

        int getLastNumberProduced() {
            return lastNumberProduced.get();
        }

        boolean wasCanceled() {
            return wasCanceled.get();
        }

        void setExplicitCancel(boolean explicitCancel) {
            this.explicitCancel.set(explicitCancel);
        }

        @Override
        public Flowable<NumberProto.Number> responsePressure(Single<Empty> request) {
            // Produce a very long sequence
            return Flowable
                    .fromIterable(IntStream.range(0, NUMBER_OF_STREAM_ELEMENTS)::iterator)
                    .delay(10, TimeUnit.MILLISECONDS)
                    .doOnNext(i -> lastNumberProduced.set(i))
                    .map(CancellationPropagationIntegrationTest::protoNum)
                    .doOnCancel(() -> {
                        wasCanceled.set(true);
                        System.out.println("Server canceled");
                    });
        }

        @Override
        public Single<NumberProto.Number> requestPressure(Flowable<NumberProto.Number> request) {
            if (explicitCancel.get()) {
                // Process a very long sequence
                Disposable subscription = request.subscribe(n -> System.out.println("S: " + n.getNumber(0)));
                return Single
                        .just(protoNum(-1))
                        .delay(250, TimeUnit.MILLISECONDS)
                        // Explicitly cancel by disposing the subscription
                        .doOnSuccess(x -> subscription.dispose());
            } else {
                // Process some of a very long sequence and cancel implicitly with a take(10)
                return request.map(req -> req.getNumber(0))
                        .doOnNext(System.out::println)
                        .take(10)
                        .last(-1)
                        .map(CancellationPropagationIntegrationTest::protoNum);
            }
        }

        @Override
        public Flowable<NumberProto.Number> twoWayPressure(Flowable<NumberProto.Number> request) {
            return requestPressure(request).toFlowable();
        }
    }

    @Test
    public void clientCanCancelServerStreamExplicitly() throws InterruptedException {
        TestService svc = new TestService();
        serverRule.getServiceRegistry().addService(svc);

        RxNumbersStub stub = Rx3NumbersGrpc.newRxStub(serverRule.getChannel());
        TestSubscriber<NumberProto.Number> subscription = Single.just(Empty.getDefaultInstance())
                .to(stub::responsePressure)
                .doOnNext(number -> System.out.println(number.getNumber(0)))
                .doOnError(throwable -> System.out.println(throwable.getMessage()))
                .doOnComplete(() -> System.out.println("Completed"))
                .doOnCancel(() -> System.out.println("Client canceled"))
                .test();

        Thread.sleep(250);
        subscription.cancel();
        Thread.sleep(250);

        subscription.await(3, TimeUnit.SECONDS);
        // Cancellation may or may not deliver the last generated message due to delays in the gRPC processing thread
        assertThat(Math.abs(subscription.values().size() - svc.getLastNumberProduced())).isLessThanOrEqualTo(3);
        assertThat(svc.wasCanceled()).isTrue();

        errorRule.verifyNoError();
    }

    @Test
    public void clientCanCancelServerStreamImplicitly() throws InterruptedException {
        TestService svc = new TestService();
        serverRule.getServiceRegistry().addService(svc);

        RxNumbersStub stub = Rx3NumbersGrpc.newRxStub(serverRule.getChannel());
        TestSubscriber<NumberProto.Number> subscription =  Single.just(Empty.getDefaultInstance())
                .to(stub::responsePressure)
                .doOnNext(number -> System.out.println(number.getNumber(0)))
                .doOnError(throwable -> System.out.println(throwable.getMessage()))
                .doOnComplete(() -> System.out.println("Completed"))
                .doOnCancel(() -> System.out.println("Client canceled"))
                .take(10)
                .test();

        // Consume some work
        Thread.sleep(TimeUnit.SECONDS.toMillis(1));
        subscription.cancel();

        subscription.await(3, TimeUnit.SECONDS);
        subscription.assertValueCount(10);
        subscription.assertComplete();
        assertThat(svc.wasCanceled()).isTrue();

        errorRule.verifyNoError();
    }

    @Test
    public void serverCanCancelClientStreamImplicitly() throws InterruptedException {
        TestService svc = new TestService();
        serverRule.getServiceRegistry().addService(svc);

        Rx3NumbersGrpc.RxNumbersStub stub = Rx3NumbersGrpc.newRxStub(serverRule.getChannel());

        svc.setExplicitCancel(false);

        AtomicBoolean requestWasCanceled = new AtomicBoolean(false);
        AtomicBoolean requestDidProduce = new AtomicBoolean(false);

        Flowable<NumberProto.Number> request = Flowable
                .fromIterable(IntStream.range(0, NUMBER_OF_STREAM_ELEMENTS)::iterator)
                .delay(10, TimeUnit.MILLISECONDS)
                .map(CancellationPropagationIntegrationTest::protoNum)
                .doOnNext(x -> {
                    requestDidProduce.set(true);
                    System.out.println("Produced: " + x.getNumber(0));
                })
                .doOnCancel(() -> {
                    requestWasCanceled.set(true);
                    System.out.println("Client canceled");
                });

        TestObserver<Number> observer = request
                .to(stub::requestPressure)
                .doOnSuccess(number -> System.out.println(number.getNumber(0)))
                .doOnError(throwable -> System.out.println(throwable.getMessage()))
                .test();

        observer.await(3, TimeUnit.SECONDS);
        observer.assertComplete();

        await().atMost(Duration.FIVE_HUNDRED_MILLISECONDS).untilTrue(requestWasCanceled);

        assertThat(requestWasCanceled.get()).isTrue();
        assertThat(requestDidProduce.get()).isTrue();

        errorRule.verifyNoError();
    }

    @Test
    public void serverCanCancelClientStreamExplicitly() throws InterruptedException {
        TestService svc = new TestService();
        serverRule.getServiceRegistry().addService(svc);

        Rx3NumbersGrpc.RxNumbersStub stub = Rx3NumbersGrpc.newRxStub(serverRule.getChannel());

        svc.setExplicitCancel(true);

        AtomicBoolean requestWasCanceled = new AtomicBoolean(false);
        AtomicBoolean requestDidProduce = new AtomicBoolean(false);

        Flowable<NumberProto.Number> request = Flowable
                .fromIterable(IntStream.range(0, NUMBER_OF_STREAM_ELEMENTS)::iterator)
                .delay(10, TimeUnit.MILLISECONDS)
                .map(CancellationPropagationIntegrationTest::protoNum)
                .doOnNext(n -> {
                    requestDidProduce.set(true);
                    System.out.println("P: " + n.getNumber(0));
                })
                .doOnCancel(() -> {
                    requestWasCanceled.set(true);
                    System.out.println("Client canceled");
                });

        TestObserver<NumberProto.Number> observer = request
                .to(stub::requestPressure)
                .doOnSuccess(number -> System.out.println(number.getNumber(0)))
                .doOnError(throwable -> System.out.println(throwable.getMessage()))
                .test();

        observer.await(30, TimeUnit.SECONDS);
        observer.assertComplete();

        await().atMost(Duration.FIVE_HUNDRED_MILLISECONDS).untilTrue(requestWasCanceled);

        assertThat(requestWasCanceled.get()).isTrue();
        assertThat(requestDidProduce.get()).isTrue();

        errorRule.verifyNoError();
    }

    @Test
    public void serverCanCancelClientStreamImplicitlyBidi() throws InterruptedException {
        TestService svc = new TestService();
        serverRule.getServiceRegistry().addService(svc);

        Rx3NumbersGrpc.RxNumbersStub stub = Rx3NumbersGrpc.newRxStub(serverRule.getChannel());

        svc.setExplicitCancel(false);

        AtomicBoolean requestWasCanceled = new AtomicBoolean(false);
        AtomicBoolean requestDidProduce = new AtomicBoolean(false);

        Flowable<NumberProto.Number> request = Flowable
                .fromIterable(IntStream.range(0, NUMBER_OF_STREAM_ELEMENTS)::iterator)
                .delay(10, TimeUnit.MILLISECONDS)
                .map(CancellationPropagationIntegrationTest::protoNum)
                .doOnNext(x -> {
                    requestDidProduce.set(true);
                    System.out.println("Produced: " + x.getNumber(0));
                })
                .doOnCancel(() -> {
                    requestWasCanceled.set(true);
                    System.out.println("Client canceled");
                });

        TestSubscriber<NumberProto.Number> observer = request
                .compose(stub::twoWayPressure)
                .doOnNext(number -> System.out.println(number.getNumber(0)))
                .doOnError(throwable -> System.out.println(throwable.getMessage()))
                .test();

        observer.await(3, TimeUnit.SECONDS);
        assertThat(requestWasCanceled.get()).isTrue();
        assertThat(requestDidProduce.get()).isTrue();

        errorRule.verifyNoError();
    }

    @Test
    public void serverCanCancelClientStreamExplicitlyBidi() throws InterruptedException {
        TestService svc = new TestService();
        serverRule.getServiceRegistry().addService(svc);

        Rx3NumbersGrpc.RxNumbersStub stub = Rx3NumbersGrpc.newRxStub(serverRule.getChannel());

        svc.setExplicitCancel(true);

        AtomicBoolean requestWasCanceled = new AtomicBoolean(false);
        AtomicBoolean requestDidProduce = new AtomicBoolean(false);

        Flowable<NumberProto.Number> request = Flowable
                .fromIterable(IntStream.range(0, NUMBER_OF_STREAM_ELEMENTS)::iterator)
                .delay(10, TimeUnit.MILLISECONDS)
                .map(CancellationPropagationIntegrationTest::protoNum)
                .doOnNext(n -> {
                    requestDidProduce.set(true);
                    System.out.println("P: " + n.getNumber(0));
                })
                .doOnCancel(() -> {
                    requestWasCanceled.set(true);
                    System.out.println("Client canceled");
                });

        TestSubscriber<NumberProto.Number> observer = request
                .compose(stub::twoWayPressure)
                .doOnNext(number -> System.out.println(number.getNumber(0)))
                .doOnError(throwable -> System.out.println(throwable.getMessage()))
                .test();

        observer.await(30, TimeUnit.SECONDS);
        assertThat(requestWasCanceled.get()).isTrue();
        assertThat(requestDidProduce.get()).isTrue();

        errorRule.verifyNoError();
    }

    @Test
    public void prematureResponseStreamDisposalShouldNotThrowUnhandledException() throws Exception {
        TestService svc = new TestService();
        serverRule.getServiceRegistry().addService(svc);

        Rx3NumbersGrpc.RxNumbersStub stub = Rx3NumbersGrpc.newRxStub(serverRule.getChannel());

        // slowly process the response stream
        Disposable subscription = stub.responsePressure(Empty.getDefaultInstance()).subscribe(n -> {
            Thread.sleep(1000);
        });

        subscription.dispose();

        Thread.sleep(200);
        errorRule.verifyNoError();
    }

    private static NumberProto.Number protoNum(int i) {
        Integer[] ints = {i};
        return NumberProto.Number.newBuilder().addAllNumber(Arrays.asList(ints)).build();
    }
}
