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
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("Duplicates")
public class BackpressureIntegrationTest {
    private static Server server;
    private static ManagedChannel channel;

    private static final int madMultipleCutoff = 100;
    private static BackpressureDetector serverRecBPDetector = new BackpressureDetector(madMultipleCutoff);
    private static BackpressureDetector serverRespBPDetector = new BackpressureDetector(madMultipleCutoff);

    @BeforeClass
    public static void setupServer() throws Exception {
        RxNumbersGrpc.NumbersImplBase svc = new RxNumbersGrpc.NumbersImplBase() {
            @Override
            public Single<NumberProto.Number> requestPressure(Flowable<NumberProto.Number> request) {
                return request
                        .map(proto -> proto.getNumber(0))
                        .doOnNext(i -> {
                            serverRecBPDetector.tick();
                            System.out.println("    --> " + i);
                            try { Thread.sleep(50); } catch (InterruptedException e) {}
                        })
                        .last(-1)
                        .map(BackpressureIntegrationTest::protoNum);
            }

            @Override
            public Flowable<NumberProto.Number> responsePressure(Single<Empty> request) {
                return Flowable
                        .fromIterable(new Sequence(200, serverRespBPDetector))
                        .doOnNext(i -> System.out.println("   <-- " + i))
                        .map(BackpressureIntegrationTest::protoNum);
            }

            @Override
            public Flowable<NumberProto.Number> twoWayPressure(Flowable<NumberProto.Number> request) {
                request
                    .map(proto -> proto.getNumber(0))
                    .subscribe(
                        n -> {
                            serverRecBPDetector.tick();
                            System.out.println("   --> " + n);
                            try { Thread.sleep(50); } catch (InterruptedException e) {}
                        },
                        Throwable::printStackTrace,
                        () -> System.out.println("Server done.")
                    );

                return Flowable
                        .fromIterable(new Sequence(200, serverRespBPDetector))
                        .doOnNext(i -> System.out.println("                  <-- " + i))
                        .map(BackpressureIntegrationTest::protoNum);
            }
        };

        server = InProcessServerBuilder.forName("e2e").addService(svc).build().start();
        channel = InProcessChannelBuilder.forName("e2e").usePlaintext(true).build();
    }

    @Before
    public void resetServerStats() {
        serverRecBPDetector.reset();
        serverRespBPDetector.reset();
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
        Object lock = new Object();

        RxNumbersGrpc.RxNumbersStub stub = RxNumbersGrpc.newRxStub(channel);
        BackpressureDetector clientBackpressureDetector = new BackpressureDetector(madMultipleCutoff);
        Sequence seq = new Sequence(200, clientBackpressureDetector);

        Flowable<NumberProto.Number> rxRequest = Flowable
                .fromIterable(seq)
                .doOnNext(i -> System.out.println(i + " -->"))
                .map(BackpressureIntegrationTest::protoNum);


        Single<NumberProto.Number> rxResponse = stub.requestPressure(rxRequest);

        rxResponse.subscribe(
                n -> {
                    System.out.println("Client done. " + n.getNumber(0));
                    synchronized (lock) {
                        lock.notify();
                    }
                },
                t -> {
                    t.printStackTrace();
                    synchronized (lock) {
                        lock.notify();
                    }
                });

        synchronized (lock) {
            lock.wait(TimeUnit.SECONDS.toMillis(20));
        }

        assertThat(clientBackpressureDetector.backpressureDelayOcurred()).isTrue();
    }

    @Test
    public void serverToClientBackpressure() throws InterruptedException {
        Object lock = new Object();
        BackpressureDetector clientBackpressureDetector = new BackpressureDetector(madMultipleCutoff);

        RxNumbersGrpc.RxNumbersStub stub = RxNumbersGrpc.newRxStub(channel);

        Single<Empty> rxRequest = Single.just(Empty.getDefaultInstance());

        Flowable<NumberProto.Number> rxResponse = stub.responsePressure(rxRequest);
        rxResponse.subscribe(
                n -> {
                    clientBackpressureDetector.tick();
                    System.out.println(n.getNumber(0) + "  <--");
                    try { Thread.sleep(50); } catch (InterruptedException e) {}
                },
                t -> {
                    t.printStackTrace();
                    synchronized (lock) {
                        lock.notify();
                    }
                },
                () -> {
                    System.out.println("Client done.");
                    synchronized (lock) {
                        lock.notify();
                    }
                });

        synchronized (lock) {
            lock.wait(TimeUnit.SECONDS.toMillis(20));
        }

        assertThat(serverRespBPDetector.backpressureDelayOcurred()).isTrue();
    }

    @Test
    public void bidiBackpressure() throws InterruptedException {
        Object lock = new Object();
        BackpressureDetector clientReqBPDetector = new BackpressureDetector(madMultipleCutoff);
        BackpressureDetector clientRespBPDetector = new BackpressureDetector(madMultipleCutoff);

        RxNumbersGrpc.RxNumbersStub stub = RxNumbersGrpc.newRxStub(channel);

        Flowable<NumberProto.Number> rxRequest = Flowable
                .fromIterable(new Sequence(180, clientReqBPDetector))
                .doOnNext(i -> System.out.println(i + " -->"))
                .map(BackpressureIntegrationTest::protoNum);

        Flowable<NumberProto.Number> rxResponse = stub.twoWayPressure(rxRequest);

        rxResponse.subscribe(
                n -> {
                    clientRespBPDetector.tick();
                    System.out.println("               " + n.getNumber(0) + "  <--");
                    try { Thread.sleep(50); } catch (InterruptedException e) {}
                },
                t -> {
                    t.printStackTrace();
                    synchronized (lock) {
                        lock.notify();
                    }
                },
                () -> {
                    System.out.println("Client done.");
                    synchronized (lock) {
                        lock.notify();
                    }
                });

        synchronized (lock) {
            lock.wait(TimeUnit.SECONDS.toMillis(20));
        }

        assertThat(clientReqBPDetector.backpressureDelayOcurred()).isTrue();
        assertThat(serverRespBPDetector.backpressureDelayOcurred()).isTrue();
    }

    private static NumberProto.Number protoNum(int i) {
        Integer[] ints = new Integer[32 * 1024];
        Arrays.setAll(ints, operand -> i);

        return NumberProto.Number.newBuilder().addAllNumber(Arrays.asList(ints)).build();
    }
}
