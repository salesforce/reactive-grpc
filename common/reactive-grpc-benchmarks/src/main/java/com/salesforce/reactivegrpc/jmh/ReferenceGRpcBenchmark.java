/*
 *  Copyright (c) 2019, Salesforce.com, Inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */
package com.salesforce.reactivegrpc.jmh;

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.salesforce.reactivegrpc.jmh.proto.BenchmarkServiceGrpc;
import com.salesforce.reactivegrpc.jmh.proto.Messages;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.ClientCallStreamObserver;
import io.grpc.stub.StreamObserver;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

/**
 * Baseline benchmarks for gRPC calls.
 */
//CHECKSTYLE.OFF: MagicNumber
@BenchmarkMode(Mode.Throughput)
@Warmup(iterations = 10)
@Measurement(iterations = 10, time = 10, timeUnit = TimeUnit.SECONDS)
@OutputTimeUnit(TimeUnit.SECONDS)
@Fork(1)
@State(Scope.Benchmark)
public class ReferenceGRpcBenchmark {

    static final Messages.SimpleRequest   REQUEST       = Messages.SimpleRequest.getDefaultInstance();
    static final Messages.SimpleRequest[] ARRAY_REQUEST = new Messages.SimpleRequest[100000];

    static {
        Arrays.fill(ARRAY_REQUEST, Messages.SimpleRequest.getDefaultInstance());
    }

    private Server         gRpcServer;
    private ManagedChannel gRpcChannel;

    private BenchmarkServiceGrpc.BenchmarkServiceStub gRpcClient;

    @Setup
    public void setup() throws IOException {
        System.out.println("---------- SETUP ONCE -------------");
        ScheduledExecutorService scheduledExecutorService =
            Executors.newScheduledThreadPool(Runtime.getRuntime()
                                                    .availableProcessors());
        gRpcServer =
            InProcessServerBuilder.forName("benchmark-gRpcServer")
                                  .scheduledExecutorService(scheduledExecutorService)
                                  .addService(new com.salesforce.reactivegrpc.jmh.BenchmarkGRpcServerServiceImpl(100000))
                                  .build()
                                  .start();
        gRpcChannel = InProcessChannelBuilder.forName("benchmark-gRpcServer")
                                             .build();
        gRpcClient = BenchmarkServiceGrpc.newStub(gRpcChannel);
    }

    @TearDown
    public void tearDown() throws InterruptedException {
        System.out.println("---------- TEAR DOWN ONCE -------------");
        gRpcServer.shutdownNow();
        gRpcChannel.shutdownNow();
        gRpcServer.awaitTermination(1000, TimeUnit.MILLISECONDS);
        gRpcChannel.awaitTermination(1000, TimeUnit.MILLISECONDS);
    }

    @Benchmark
    public Object gRpcUnaryCall(Blackhole blackhole) throws InterruptedException {
        PerfObserver observer = new PerfObserver(blackhole);

        gRpcClient.unaryCall(REQUEST, observer);

        observer.latch.await();

        return observer;
    }

    @Benchmark
    public Object gRpcServerStreamingCall(Blackhole blackhole) throws InterruptedException {
        PerfObserver observer = new PerfObserver(blackhole);

        gRpcClient.streamingFromServer(REQUEST, observer);

        observer.latch.await();

        return observer;
    }

    @Benchmark
    public Object gRpcClientStreamingCall(Blackhole blackhole) throws InterruptedException {
        PerfObserver observer = new PerfObserver(blackhole);
        StreamObserver<Messages.SimpleRequest> sender = gRpcClient.streamingFromClient(observer);

        for (Messages.SimpleRequest request : ARRAY_REQUEST) {
            sender.onNext(request);
        }

        sender.onCompleted();

        observer.latch.await();

        return observer;
    }

    @Benchmark
    public Object gRpcBothWaysStreamingCall(Blackhole blackhole) throws InterruptedException {
        PerfObserver observer = new PerfObserver(blackhole) {
            private boolean done;
            @Override
            public void beforeStart(ClientCallStreamObserver<Messages.SimpleRequest> sender) {
                sender.setOnReadyHandler(() -> {
                    if (done) {
                        return;
                    }

                    for (Messages.SimpleRequest request : ARRAY_REQUEST) {
                        sender.onNext(request);
                    }

                    sender.onCompleted();
                    done = true;
                });
                super.beforeStart(observer);
            }
        };

        gRpcClient.streamingFromClient(observer);

        observer.latch.await();

        return observer;
    }
}
