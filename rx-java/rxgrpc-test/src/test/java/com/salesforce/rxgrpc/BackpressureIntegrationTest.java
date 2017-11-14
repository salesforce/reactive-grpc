/*
 *  Copyright (c) 2017, salesforce.com, inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.rxgrpc;

import com.google.protobuf.Empty;
import com.salesforce.servicelibs.NumberProto;
import com.salesforce.servicelibs.RxNumbersGrpc;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.reactivex.Flowable;
import io.reactivex.Single;
import io.reactivex.observers.TestObserver;
import io.reactivex.subscribers.TestSubscriber;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("Duplicates")
public class BackpressureIntegrationTest {
    private static final int NUMBER_OF_STREAM_ELEMENTS = 200;

    private static AtomicLong clientLastValueTime;
    private static AtomicLong serverLastValueTime;
    private static AtomicLong clientNbOfWaits;
    private static AtomicLong serverNumberOfWaits;

    private static Server server;
    private static ManagedChannel channel;

    @BeforeClass
    public static void setupServer() throws Exception {
        RxNumbersGrpc.NumbersImplBase svc = new RxNumbersGrpc.NumbersImplBase() {
            @Override
            public Single<NumberProto.Number> requestPressure(Flowable<NumberProto.Number> request) {
                return request
                        .map(proto -> proto.getNumber(0))
                        .doOnNext(i -> System.out.println("    --> " + i))
                        .doOnNext(i -> waitIfValuesAreEqual(i, 3))
                        .last(-1)
                        .map(BackpressureIntegrationTest::protoNum);
            }

            @Override
            public Flowable<NumberProto.Number> responsePressure(Single<Empty> request) {
                return Flowable
                        .fromIterable(IntStream.range(0, NUMBER_OF_STREAM_ELEMENTS)::iterator)
                        .doOnNext(i -> System.out.println("   <-- " + i))
                        .doOnNext(i -> updateNumberOfWaits(serverLastValueTime, serverNumberOfWaits))
                        .map(BackpressureIntegrationTest::protoNum);
            }

            @Override
            public Flowable<NumberProto.Number> twoWayPressure(Flowable<NumberProto.Number> request) {
                request
                    .map(proto -> proto.getNumber(0))
                        .doOnNext(n -> System.out.println("   --> " + n))
                        .doOnNext(n -> waitIfValuesAreEqual(n, 3))
                        .subscribe();
                return Flowable
                        .fromIterable(IntStream.range(0, NUMBER_OF_STREAM_ELEMENTS)::iterator)
                        .doOnNext(i -> System.out.println("                  <-- " + i))
                        .doOnNext(i -> updateNumberOfWaits(serverLastValueTime, serverNumberOfWaits))
                        .map(BackpressureIntegrationTest::protoNum);
            }
        };

        server = InProcessServerBuilder.forName("e2e").addService(svc).build().start();
        channel = InProcessChannelBuilder.forName("e2e").usePlaintext(true).build();
    }

    @Before
    public void resetServerStats() {
        clientLastValueTime = new AtomicLong(0);
        clientNbOfWaits = new AtomicLong(0);
        serverLastValueTime = new AtomicLong(0);
        serverNumberOfWaits = new AtomicLong(0);
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
        RxNumbersGrpc.RxNumbersStub stub = RxNumbersGrpc.newRxStub(channel);

        Flowable<NumberProto.Number> rxRequest = Flowable
                .fromIterable(IntStream.range(0, NUMBER_OF_STREAM_ELEMENTS)::iterator)
                .doOnNext(i -> System.out.println(i + " --> "))
                .doOnNext(i -> updateNumberOfWaits(clientLastValueTime, clientNbOfWaits))
                .map(BackpressureIntegrationTest::protoNum);

        TestObserver<NumberProto.Number> rxResponse = stub.requestPressure(rxRequest).test();

        rxResponse.awaitTerminalEvent(5, TimeUnit.SECONDS);
        rxResponse.assertComplete()
                .assertValue(v -> v.getNumber(0) == NUMBER_OF_STREAM_ELEMENTS - 1);

        assertThat(clientNbOfWaits.get()).isEqualTo(1);
    }

    @Test
    public void serverToClientBackpressure() throws InterruptedException {
        RxNumbersGrpc.RxNumbersStub stub = RxNumbersGrpc.newRxStub(channel);

        Single<Empty> rxRequest = Single.just(Empty.getDefaultInstance());

        TestSubscriber<NumberProto.Number> rxResponse = stub.responsePressure(rxRequest)
                .doOnNext(n -> System.out.println(n.getNumber(0) + "  <--"))
                .doOnNext(n -> waitIfValuesAreEqual(n.getNumber(0), 3))
                .test();

        rxResponse.awaitTerminalEvent(5, TimeUnit.SECONDS);
        rxResponse.assertComplete()
                .assertValueCount(NUMBER_OF_STREAM_ELEMENTS);

        assertThat(serverNumberOfWaits.get()).isEqualTo(1);
    }

    @Test
    public void bidiBackpressure() throws InterruptedException {
        RxNumbersGrpc.RxNumbersStub stub = RxNumbersGrpc.newRxStub(channel);

        Flowable<NumberProto.Number> rxRequest = Flowable
                .fromIterable(IntStream.range(0, NUMBER_OF_STREAM_ELEMENTS)::iterator)
                .doOnNext(i -> System.out.println(i + " --> "))
                .doOnNext(i -> updateNumberOfWaits(clientLastValueTime, clientNbOfWaits))
                .map(BackpressureIntegrationTest::protoNum);

        TestSubscriber<NumberProto.Number> rxResponse = stub.twoWayPressure(rxRequest)
                .doOnNext(n -> System.out.println(n.getNumber(0) + "  <--"))
                .doOnNext(n -> waitIfValuesAreEqual(n.getNumber(0), 3))
                .test();

        rxResponse.awaitTerminalEvent(5, TimeUnit.SECONDS);
        rxResponse.assertComplete().assertValueCount(NUMBER_OF_STREAM_ELEMENTS);

        assertThat(clientNbOfWaits.get()).isEqualTo(1);
        assertThat(serverNumberOfWaits.get()).isEqualTo(1);
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
                Thread.sleep(2000);
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
