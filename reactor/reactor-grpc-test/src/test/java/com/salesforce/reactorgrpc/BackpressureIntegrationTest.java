/*
 *  Copyright (c) 2017, salesforce.com, inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.reactorgrpc;

import com.google.protobuf.Empty;
import com.salesforce.servicelibs.NumberProto;
import com.salesforce.servicelibs.ReactorNumbersGrpc;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("Duplicates")
public class BackpressureIntegrationTest {
    private static final int NUMBER_OF_STREAM_ELEMENTS = 270;

    private static AtomicLong lastValueTime;
    private static AtomicLong numberOfWaits;

    private static Server server;
    private static ManagedChannel channel;

    @BeforeClass
    public static void setupServer() throws Exception {
        ReactorNumbersGrpc.NumbersImplBase svc = new ReactorNumbersGrpc.NumbersImplBase() {
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
                return responsePressure(null);
            }
        };

        server = InProcessServerBuilder.forName("e2e").addService(svc).build().start();
        channel = InProcessChannelBuilder.forName("e2e").usePlaintext(true).build();
    }

    @Before
    public void resetServerStats() {
        lastValueTime = new AtomicLong(0);
        numberOfWaits = new AtomicLong(0);
    }

    @AfterClass
    public static void stopServer() throws InterruptedException {
        server.shutdown();
        server.awaitTermination();
        channel.shutdown();

        server = null;
        channel = null;
    }

    @Test
    public void clientToServerBackpressure() throws InterruptedException {
        ReactorNumbersGrpc.ReactorNumbersStub stub = ReactorNumbersGrpc.newReactorStub(channel);

        Flux<NumberProto.Number> reactorRequest = Flux
                .fromIterable(IntStream.range(0, NUMBER_OF_STREAM_ELEMENTS)::iterator)
                .doOnNext(i -> System.out.println(i + " --> "))
                .doOnNext(i -> updateNumberOfWaits(lastValueTime, numberOfWaits))
                .map(BackpressureIntegrationTest::protoNum);

        Mono<NumberProto.Number> reactorResponse = stub.requestPressure(reactorRequest);

        StepVerifier.create(reactorResponse)
                .expectNextMatches(v -> v.getNumber(0) == NUMBER_OF_STREAM_ELEMENTS - 1)
                .expectComplete()
                .verify(Duration.ofSeconds(5));

        assertThat(numberOfWaits.get()).isEqualTo(1);
    }

    @Test
    public void serverToClientBackpressure() throws InterruptedException {
        ReactorNumbersGrpc.ReactorNumbersStub stub = ReactorNumbersGrpc.newReactorStub(channel);

        Mono<Empty> reactorRequest = Mono.just(Empty.getDefaultInstance());

        Flux<NumberProto.Number> reactorResponse = stub.responsePressure(reactorRequest)
                .doOnNext(n -> System.out.println(n.getNumber(0) + "  <--"))
                .doOnNext(n -> waitIfValuesAreEqual(n.getNumber(0), 3));

        StepVerifier.create(reactorResponse)
                .expectNextCount(NUMBER_OF_STREAM_ELEMENTS)
                .expectComplete()
                .verify(Duration.ofSeconds(5));

        assertThat(numberOfWaits.get()).isEqualTo(1);
    }

    @Test
    public void bidiResponseBackpressure() throws InterruptedException {
        ReactorNumbersGrpc.ReactorNumbersStub stub = ReactorNumbersGrpc.newReactorStub(channel);

        Flux<NumberProto.Number> reactorResponse = stub.twoWayResponsePressure(Flux.empty())
                .doOnNext(n -> System.out.println(n.getNumber(0) + "  <--"))
                .doOnNext(n -> waitIfValuesAreEqual(n.getNumber(0), 3));

        StepVerifier.create(reactorResponse)
                .expectNextCount(NUMBER_OF_STREAM_ELEMENTS)
                .expectComplete()
                .verify(Duration.ofSeconds(5));

        assertThat(numberOfWaits.get()).isEqualTo(1);
    }

    @Test
    public void bidiRequestBackpressure() throws InterruptedException {
        ReactorNumbersGrpc.ReactorNumbersStub stub = ReactorNumbersGrpc.newReactorStub(channel);

        Flux<NumberProto.Number> reactorRequest = Flux
                .fromIterable(IntStream.range(0, NUMBER_OF_STREAM_ELEMENTS)::iterator)
                .doOnNext(i -> System.out.println(i + " --> "))
                .doOnNext(i -> updateNumberOfWaits(lastValueTime, numberOfWaits))
                .map(BackpressureIntegrationTest::protoNum);

        Flux<NumberProto.Number> reactorResponse = stub.twoWayRequestPressure(reactorRequest);

        StepVerifier.create(reactorResponse)
                .expectNextMatches(v -> v.getNumber(0) == NUMBER_OF_STREAM_ELEMENTS - 1)
                .expectComplete()
                .verify(Duration.ofSeconds(5));

        assertThat(numberOfWaits.get()).isEqualTo(1);
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
}
