/*
 *  Copyright (c) 2019, Salesforce.com, Inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.reactorgrpc;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import com.google.protobuf.Empty;
import com.salesforce.grpc.testing.contrib.NettyGrpcServerRule;
import com.salesforce.servicelibs.NumberProto;
import com.salesforce.servicelibs.ReactorNumbersGrpc;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SuppressWarnings({"unchecked", "Duplicates"})
@RunWith(Parameterized.class)
public class CancellationPropagationIntegrationTest {
    private static final int NUMBER_OF_STREAM_ELEMENTS = 10000;
    private static final int SEQUENCE_DELAY_MILLIS = 10;


    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
                { new TestService(), false },
                { new FusedTestService(), true }
        });
    }

    @Rule
    public NettyGrpcServerRule serverRule = new NettyGrpcServerRule();

    private final AbstractNumberImplBase service;
    private final boolean expectFusion;


    public CancellationPropagationIntegrationTest(AbstractNumberImplBase service, boolean expectFusion) {
        this.service = service;
        this.expectFusion = expectFusion;
    }

    @Test
    public void clientCanCancelServerStreamExplicitly() throws InterruptedException {
        serverRule.getServiceRegistry().addService(service);

        AtomicInteger lastNumberConsumed = new AtomicInteger(Integer.MAX_VALUE);
        ReactorNumbersGrpc.ReactorNumbersStub stub = ReactorNumbersGrpc.newReactorStub(serverRule.getChannel());
        Mono<Empty> request = Mono.just(Empty.getDefaultInstance());

        if (!expectFusion) {
            request = request.hide();
        }

        Flux<NumberProto.Number> test =
                request
                    .as(stub::responsePressure)
                    .doOnNext(number -> {lastNumberConsumed.set(number.getNumber(0)); System.out.println("C: " + number.getNumber(0));})
                    .doOnError(throwable -> System.out.println(throwable.getMessage()))
                    .doOnComplete(() -> System.out.println("Completed"))
                    .doOnCancel(() -> System.out.println("Client canceled"));

        if (!expectFusion) {
            test = test.hide();
        }

        Disposable subscription = test.publish().connect();

        Thread.sleep(1000);
        subscription.dispose();
        Thread.sleep(1000);

        // Cancellation may or may not deliver the last generated message due to delays in the gRPC processing thread
        assertThat(Math.abs(lastNumberConsumed.get() - service.getLastNumberProduced())).isLessThanOrEqualTo(3);
        assertThat(service.wasCanceled()).isTrue();
    }

    @Test
    public void clientCanCancelServerStreamImplicitly() throws InterruptedException {
        serverRule.getServiceRegistry().addService(service);

        ReactorNumbersGrpc.ReactorNumbersStub stub = ReactorNumbersGrpc.newReactorStub(serverRule.getChannel());
        Mono<Empty> request = Mono.just(Empty.getDefaultInstance());

        if (!expectFusion) {
            request = request.hide();
        }

        Flux<NumberProto.Number> test =
                request
                    .as(stub::responsePressure)
                    .doOnNext(number -> System.out.println(number.getNumber(0)))
                    .doOnError(throwable -> System.out.println(throwable.getMessage()))
                    .doOnComplete(() -> System.out.println("Completed"))
                    .doOnCancel(() -> System.out.println("Client canceled"))
                    .take(10);

        if (!expectFusion) {
            test = test.hide();
        }

        Disposable subscription = test.publish().connect();

        Thread.sleep(1000);

        assertThat(service.wasCanceled()).isTrue();
    }

    @Test
    public void serverCanCancelClientStreamImplicitly() {
        serverRule.getServiceRegistry().addService(service);

        ReactorNumbersGrpc.ReactorNumbersStub stub = ReactorNumbersGrpc.newReactorStub(serverRule.getChannel());

        service.setExplicitCancel(false);

        AtomicBoolean requestWasCanceled = new AtomicBoolean(false);
        AtomicBoolean requestDidProduce = new AtomicBoolean(false);

        Flux<NumberProto.Number> request = Flux
                .fromIterable(IntStream.range(0, NUMBER_OF_STREAM_ELEMENTS)::iterator)
                .delayElements(Duration.ofMillis(SEQUENCE_DELAY_MILLIS))
                .map(CancellationPropagationIntegrationTest::protoNum)
                .doOnNext(x -> {
                    requestDidProduce.set(true);
                    System.out.println("Produced: " + x.getNumber(0));
                })
                .doOnCancel(() -> {
                    requestWasCanceled.set(true);
                    System.out.println("Client canceled");
                });

        if (!expectFusion) {
            request = request.hide();
        }

        Mono<NumberProto.Number> observer = request.as(stub::requestPressure)
                .doOnSuccess(number -> System.out.println(number.getNumber(0)))
                .doOnError(throwable -> System.out.println(throwable.getMessage()));

        StepVerifier.Step<NumberProto.Number> stepVerifier = StepVerifier.create(observer);

        if (expectFusion) {
            stepVerifier = ((StepVerifier.FirstStep<NumberProto.Number>) stepVerifier).expectFusion();
        }

        stepVerifier
                .expectNext(protoNum(9))
                .verifyComplete();

        await().atMost(org.awaitility.Duration.FIVE_HUNDRED_MILLISECONDS).untilTrue(requestWasCanceled);

        assertThat(requestWasCanceled.get()).isTrue();
        assertThat(requestDidProduce.get()).isTrue();
    }

    @Test
    public void serverCanCancelClientStreamExplicitly() {
        serverRule.getServiceRegistry().addService(service);

        ReactorNumbersGrpc.ReactorNumbersStub stub = ReactorNumbersGrpc.newReactorStub(serverRule.getChannel());

        service.setExplicitCancel(true);

        AtomicBoolean requestWasCanceled = new AtomicBoolean(false);
        AtomicBoolean requestDidProduce = new AtomicBoolean(false);

        Flux<NumberProto.Number> request = Flux
                .fromIterable(IntStream.range(0, NUMBER_OF_STREAM_ELEMENTS)::iterator)
                .delayElements(Duration.ofMillis(SEQUENCE_DELAY_MILLIS))
                .map(CancellationPropagationIntegrationTest::protoNum)
                .doOnNext(n -> {
                    requestDidProduce.set(true);
                    System.out.println("P: " + n.getNumber(0));
                })
                .doOnCancel(() -> {
                    requestWasCanceled.set(true);
                    System.out.println("Client canceled");
                });

        if (!expectFusion) {
            request = request.hide();
        }

        Mono<NumberProto.Number> observer = request.as(stub::requestPressure)
                .doOnSuccess(number -> System.out.println(number.getNumber(0)))
                .doOnError(throwable -> System.out.println(throwable.getMessage()));

        StepVerifier.Step<NumberProto.Number> stepVerifier = StepVerifier.create(observer);

        if (expectFusion) {
            stepVerifier = ((StepVerifier.FirstStep<NumberProto.Number>) stepVerifier).expectFusion();
        }

        stepVerifier
                .expectNext(protoNum(-1))
                .verifyComplete();

        await().atMost(org.awaitility.Duration.FIVE_HUNDRED_MILLISECONDS).untilTrue(requestWasCanceled);

        assertThat(requestWasCanceled.get()).isTrue();
        assertThat(requestDidProduce.get()).isTrue();
    }

    @Test
    public void serverCanCancelClientStreamImplicitlyBidi() {
        serverRule.getServiceRegistry().addService(service);

        ReactorNumbersGrpc.ReactorNumbersStub stub = ReactorNumbersGrpc.newReactorStub(serverRule.getChannel());

        service.setExplicitCancel(false);

        AtomicBoolean requestWasCanceled = new AtomicBoolean(false);
        AtomicBoolean requestDidProduce = new AtomicBoolean(false);

        Flux<NumberProto.Number> request = Flux
                .fromIterable(IntStream.range(0, NUMBER_OF_STREAM_ELEMENTS)::iterator)
                .delayElements(Duration.ofMillis(SEQUENCE_DELAY_MILLIS))
                .map(CancellationPropagationIntegrationTest::protoNum)
                .doOnNext(x -> {
                    requestDidProduce.set(true);
                    System.out.println("Produced: " + x.getNumber(0));
                })
                .doOnCancel(() -> {
                    requestWasCanceled.set(true);
                    System.out.println("Client canceled");
                });

        if (!expectFusion) {
            request = request.hide();
        }

        Flux<NumberProto.Number> observer = request.transform(stub::twoWayPressure)
                .doOnNext(number -> System.out.println(number.getNumber(0)))
                .doOnError(throwable -> System.out.println(throwable.getMessage()));

        StepVerifier.Step<NumberProto.Number> stepVerifier = StepVerifier.create(observer);

        if (expectFusion) {
            stepVerifier = ((StepVerifier.FirstStep<NumberProto.Number>) stepVerifier).expectFusion();
        }

        stepVerifier
                .expectNext(protoNum(9))
                .verifyComplete();

        await().atMost(org.awaitility.Duration.FIVE_HUNDRED_MILLISECONDS).untilTrue(requestWasCanceled);

        assertThat(requestWasCanceled.get()).isTrue();
        assertThat(requestDidProduce.get()).isTrue();
    }

    @Test
    public void serverCanCancelClientStreamExplicitlyBidi() {
        serverRule.getServiceRegistry().addService(service);

        ReactorNumbersGrpc.ReactorNumbersStub stub = ReactorNumbersGrpc.newReactorStub(serverRule.getChannel());

        service.setExplicitCancel(true);

        AtomicBoolean requestWasCanceled = new AtomicBoolean(false);
        AtomicBoolean requestDidProduce = new AtomicBoolean(false);

        Flux<NumberProto.Number> request = Flux
                .fromIterable(IntStream.range(0, NUMBER_OF_STREAM_ELEMENTS)::iterator)
                .delayElements(Duration.ofMillis(SEQUENCE_DELAY_MILLIS))
                .map(CancellationPropagationIntegrationTest::protoNum)
                .doOnNext(n -> {
                    requestDidProduce.set(true);
                    System.out.println("P: " + n.getNumber(0));
                })
                .doOnCancel(() -> {
                    requestWasCanceled.set(true);
                    System.out.println("Client canceled");
                });

        if (!expectFusion) {
            request = request.hide();
        }

        Flux<NumberProto.Number> observer = request.transform(stub::twoWayPressure)
                .doOnNext(number -> System.out.println(number.getNumber(0)))
                .doOnError(throwable -> System.out.println(throwable.getMessage()));

        StepVerifier.Step<NumberProto.Number> stepVerifier = StepVerifier.create(observer);

        if (expectFusion) {
            stepVerifier = ((StepVerifier.FirstStep<NumberProto.Number>) stepVerifier).expectFusion();
        }

        stepVerifier
                .expectNext(protoNum(-1))
                .verifyComplete();

        await().atMost(org.awaitility.Duration.FIVE_HUNDRED_MILLISECONDS).untilTrue(requestWasCanceled);

        assertThat(requestWasCanceled.get()).isTrue();
        assertThat(requestDidProduce.get()).isTrue();
    }

    private static NumberProto.Number protoNum(int i) {
        Integer[] ints = {i};
        return NumberProto.Number.newBuilder().addAllNumber(Arrays.asList(ints)).build();
    }

    static abstract class AbstractNumberImplBase extends ReactorNumbersGrpc.NumbersImplBase {
        AtomicInteger lastNumberProduced = new AtomicInteger(Integer.MIN_VALUE);
        AtomicBoolean wasCanceled = new AtomicBoolean(false);
        AtomicBoolean explicitCancel = new AtomicBoolean(false);

        public int getLastNumberProduced() {
            return lastNumberProduced.get();
        }

        public boolean wasCanceled() {
            return wasCanceled.get();
        }

        public void setExplicitCancel(boolean explicitCancel) {
            this.explicitCancel.set(explicitCancel);
        }

    }

    private static class TestService extends AbstractNumberImplBase {

        @Override
        public Flux<NumberProto.Number> responsePressure(Mono<Empty> request) {
            // Produce a very long sequence
            return Flux
                    .fromIterable(IntStream.range(0, NUMBER_OF_STREAM_ELEMENTS)::iterator)
                    .delayElements(Duration.ofMillis(SEQUENCE_DELAY_MILLIS))
                    .doOnNext(i -> lastNumberProduced.set(i))
                    .map(CancellationPropagationIntegrationTest::protoNum)
                    .doOnCancel(() -> {
                        wasCanceled.set(true);
                        System.out.println("Server canceled");
                    })
                    .hide();
        }

        @Override
        public Mono<NumberProto.Number> requestPressure(Flux<NumberProto.Number> request) {
            if (explicitCancel.get()) {
                // Process a very long sequence
                Disposable subscription = request.subscribe(n -> System.out.println("S: " + n.getNumber(0)));
                return Mono
                        .just(protoNum(-1))
                        .delayElement(Duration.ofMillis(250))
                        // Explicitly cancel by disposing the subscription
                        .doOnSuccess(x -> subscription.dispose())
                        .hide();
            } else {
                // Process some of a very long sequence and cancel implicitly with a take(10)
                return request.map(req -> req.getNumber(0))
                              .doOnNext(System.out::println)
                              .take(10)
                              .last(-1)
                              .map(CancellationPropagationIntegrationTest::protoNum)
                              .hide();
            }
        }

        @Override
        public Flux<NumberProto.Number> twoWayPressure(Flux<NumberProto.Number> request) {
            return requestPressure(request).flux().hide();
        }
    }

    private static class FusedTestService extends AbstractNumberImplBase{

        @Override
        public Flux<NumberProto.Number> responsePressure(Mono<Empty> request) {
            // Produce a very long sequence
            return Flux
                    .fromIterable(IntStream.range(0, NUMBER_OF_STREAM_ELEMENTS)::iterator)
                    .delayElements(Duration.ofMillis(SEQUENCE_DELAY_MILLIS))
                    .doOnNext(i -> lastNumberProduced.set(i))
                    .map(CancellationPropagationIntegrationTest::protoNum)
                    .doOnCancel(() -> {
                        wasCanceled.set(true);
                        System.out.println("Server canceled");
                    });
        }

        @Override
        public Mono<NumberProto.Number> requestPressure(Flux<NumberProto.Number> request) {
            if (explicitCancel.get()) {
                // Process a very long sequence
                Disposable subscription = request.subscribe(n -> System.out.println("S: " + n.getNumber(0)));
                return Mono
                        .just(protoNum(-1))
                        .delayElement(Duration.ofMillis(250))
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
        public Flux<NumberProto.Number> twoWayPressure(Flux<NumberProto.Number> request) {
            return requestPressure(request).flux();
        }
    }
}
