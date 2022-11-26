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
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

import com.google.protobuf.Empty;
import com.salesforce.servicelibs.NumberProto;
import com.salesforce.servicelibs.ReactorNumbersGrpc;
import io.grpc.testing.GrpcServerRule;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("Duplicates")
@Ignore
@RunWith(Parameterized.class)
public class BackpressureIntegrationTest {
    private static final int NUMBER_OF_STREAM_ELEMENTS = 512 * 12;


    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
                { new TestService(), false },
                { new FusedTestService(), true }
        });
    }

    @Rule
    public GrpcServerRule serverRule = new GrpcServerRule();


    private static AtomicLong lastValueTime;
    private static AtomicLong numberOfWaits;

    private final ReactorNumbersGrpc.NumbersImplBase service;
    private final boolean expectFusion;


    public BackpressureIntegrationTest(ReactorNumbersGrpc.NumbersImplBase service, boolean expectFusion) {
        this.service = service;
        this.expectFusion = expectFusion;
    }

    @Before
    public void resetServerStats() {
        lastValueTime = new AtomicLong(0);
        numberOfWaits = new AtomicLong(0);
    }

    @Test
    public void clientToServerBackpressure() {
        serverRule.getServiceRegistry().addService(service);

        ReactorNumbersGrpc.ReactorNumbersStub stub = ReactorNumbersGrpc.newReactorStub(serverRule.getChannel());

        Flux<NumberProto.Number> reactorRequest = Flux
                .fromIterable(IntStream.range(0, NUMBER_OF_STREAM_ELEMENTS)::iterator)
                .doOnNext(i -> System.out.println(i + " --> "))
                .doOnNext(i -> updateNumberOfWaits(lastValueTime, numberOfWaits))
                .map(BackpressureIntegrationTest::protoNum);

        if (!expectFusion) {
            reactorRequest = reactorRequest.hide();
        }

        Mono<NumberProto.Number> reactorResponse = reactorRequest.as(stub::requestPressure);

        StepVerifier.Step<NumberProto.Number> stepVerifier = StepVerifier.create(reactorResponse);

        if (expectFusion) {
            stepVerifier = ((StepVerifier.FirstStep<NumberProto.Number>) stepVerifier).expectFusion();
        }

        stepVerifier
                    .expectNextMatches(v -> v.getNumber(0) == NUMBER_OF_STREAM_ELEMENTS - 1)
                    .expectComplete()
                    .verify(Duration.ofSeconds(15));

        assertThat(numberOfWaits.get()).isGreaterThan(0L);
    }

    @Test
    public void serverToClientBackpressure() {
        serverRule.getServiceRegistry().addService(service);

        ReactorNumbersGrpc.ReactorNumbersStub stub = ReactorNumbersGrpc.newReactorStub(serverRule.getChannel());

        Mono<Empty> reactorRequest = Mono.just(Empty.getDefaultInstance());

        if (!expectFusion) {
            reactorRequest = reactorRequest.hide();
        }

        Flux<NumberProto.Number> reactorResponse = reactorRequest.as(stub::responsePressure)
                .doOnNext(n -> System.out.println(n.getNumber(0) + "  <--"))
                .doOnNext(n -> waitIfValuesAreEqual(n.getNumber(0), 3));


        StepVerifier
                .create(reactorResponse)
                .expectNextCount(NUMBER_OF_STREAM_ELEMENTS)
                .expectComplete()
                .verify(Duration.ofSeconds(15));

        assertThat(numberOfWaits.get()).isGreaterThan(0L);
    }

    @Test
    public void bidiResponseBackpressure() {
        serverRule.getServiceRegistry().addService(service);

        ReactorNumbersGrpc.ReactorNumbersStub stub = ReactorNumbersGrpc.newReactorStub(serverRule.getChannel());

        Flux<NumberProto.Number> reactorRequest = Flux.empty();

        if (!expectFusion) {
            reactorRequest = reactorRequest.hide();
        }

        Flux<NumberProto.Number> reactorResponse = reactorRequest.transform(stub::twoWayResponsePressure)
                .doOnNext(n -> System.out.println(n.getNumber(0) + "  <--"))
                .doOnNext(n -> waitIfValuesAreEqual(n.getNumber(0), 3));

        StepVerifier.Step<NumberProto.Number> stepVerifier = StepVerifier.create(reactorResponse);

        if (expectFusion) {
            stepVerifier = ((StepVerifier.FirstStep<NumberProto.Number>) stepVerifier).expectFusion();
        }

        stepVerifier
                .expectNextCount(NUMBER_OF_STREAM_ELEMENTS)
                .expectComplete()
                .verify(Duration.ofSeconds(15));

        assertThat(numberOfWaits.get()).isGreaterThan(0L);
    }

    @Test
    public void bidiRequestBackpressure() {
        serverRule.getServiceRegistry().addService(service);

        ReactorNumbersGrpc.ReactorNumbersStub stub = ReactorNumbersGrpc.newReactorStub(serverRule.getChannel());

        Flux<NumberProto.Number> reactorRequest = Flux
                .fromIterable(IntStream.range(0, NUMBER_OF_STREAM_ELEMENTS)::iterator)
                .doOnNext(i -> System.out.println(i + " --> "))
                .doOnNext(i -> updateNumberOfWaits(lastValueTime, numberOfWaits))
                .map(BackpressureIntegrationTest::protoNum);

        if (!expectFusion) {
            reactorRequest = reactorRequest.hide();
        }

        Flux<NumberProto.Number> reactorResponse = reactorRequest.transform(stub::twoWayRequestPressure);

        StepVerifier.Step<NumberProto.Number> stepVerifier = StepVerifier.create(reactorResponse);

        if (expectFusion) {
            stepVerifier = ((StepVerifier.FirstStep<NumberProto.Number>) stepVerifier).expectFusion();
        }

        stepVerifier
                .expectNextMatches(v -> v.getNumber(0) == NUMBER_OF_STREAM_ELEMENTS - 1)
                .expectComplete()
                .verify(Duration.ofSeconds(15));

        assertThat(numberOfWaits.get()).isGreaterThan(0L);
    }


    private static void updateNumberOfWaits(AtomicLong start, AtomicLong maxTime) {
        Long now = System.currentTimeMillis();
        Long startValue = start.get();
        if (startValue != 0 && now - startValue > 1000) {
            maxTime.incrementAndGet();
        }
        start.set(now);
    }

    private static void waitIfValuesAreEqual(int value, int other) {
        if (value == other) {
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
            }
        }
    }

    private static NumberProto.Number protoNum(int i) {
        Integer[] ints = new Integer[32 * 1024];
        Arrays.setAll(ints, operand -> i);

        return NumberProto.Number.newBuilder().addAllNumber(Arrays.asList(ints)).build();
    }


    private static class TestService extends ReactorNumbersGrpc.NumbersImplBase {
        @Override
        public Mono<NumberProto.Number> requestPressure(Flux<NumberProto.Number> request) {
            return request
                    .hide()
                    .map(proto -> proto.getNumber(0))
                    .doOnNext(i -> System.out.println("    --> " + i))
                    .doOnNext(i -> waitIfValuesAreEqual(i, 3))
                    .last(-1)
                    .map(BackpressureIntegrationTest::protoNum)
                    .hide();
        }

        @Override
        public Flux<NumberProto.Number> responsePressure(Mono<Empty> request) {
            return Flux
                    .fromIterable(IntStream.range(0, NUMBER_OF_STREAM_ELEMENTS)::iterator)
                    .doOnNext(i -> System.out.println("   <-- " + i))
                    .doOnNext(i -> updateNumberOfWaits(lastValueTime, numberOfWaits))
                    .map(BackpressureIntegrationTest::protoNum)
                    .hide();
        }

        @Override
        public Flux<NumberProto.Number> twoWayRequestPressure(Flux<NumberProto.Number> request) {
            return requestPressure(request).flux();
        }

        @Override
        public Flux<NumberProto.Number> twoWayResponsePressure(Flux<NumberProto.Number> request) {
            return Flux.merge(
                    request.then(Mono.empty()),
                    responsePressure((Empty) null)
            );
        }
    }

    private static class FusedTestService extends ReactorNumbersGrpc.NumbersImplBase {
        @Override
        public Mono<NumberProto.Number> requestPressure(Flux<NumberProto.Number> request) {
            return request
                    .map(proto -> proto.getNumber(0))
                    .doOnNext(i -> System.out.println("    --> " + i))
                    .doOnNext(i -> waitIfValuesAreEqual(i, 3))
                    .last(-1)
                    .map(BackpressureIntegrationTest::protoNum);
        }

        @Override
        public Flux<NumberProto.Number> responsePressure(Mono<Empty> request) {
            return Flux
                    .fromIterable(IntStream.range(0, NUMBER_OF_STREAM_ELEMENTS)::iterator)
                    .doOnNext(i -> System.out.println("   <-- " + i))
                    .doOnNext(i -> updateNumberOfWaits(lastValueTime, numberOfWaits))
                    .map(BackpressureIntegrationTest::protoNum);
        }

        @Override
        public Flux<NumberProto.Number> twoWayRequestPressure(Flux<NumberProto.Number> request) {
            return requestPressure(request).flux();
        }

        @Override
        public Flux<NumberProto.Number> twoWayResponsePressure(Flux<NumberProto.Number> request) {
            request.subscribe();
            return responsePressure((Empty) null);
        }
    }
}
