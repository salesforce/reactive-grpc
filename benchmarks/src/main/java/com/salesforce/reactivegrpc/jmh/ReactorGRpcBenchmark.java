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

import com.salesforce.reactivegrpc.jmh.proto.Messages;
import com.salesforce.reactivegrpc.jmh.proto.ReactorBenchmarkServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
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
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Benchmarks Spring Reactor calls.
 */
//CHECKSTYLE.OFF: MagicNumber
@BenchmarkMode(Mode.Throughput)
@Warmup(iterations = 10)
@Measurement(iterations = 10, time = 10, timeUnit = TimeUnit.SECONDS)
@OutputTimeUnit(TimeUnit.SECONDS)
@Fork(1)
@State(Scope.Benchmark)
public class ReactorGRpcBenchmark {

    private static final Mono<Messages.SimpleRequest> MONO_REQUEST =
        Mono.just(Messages.SimpleRequest.getDefaultInstance());

    private static final Flux<Messages.SimpleRequest> FLUX_REQUEST;

    static {
        Messages.SimpleRequest[] array = new Messages.SimpleRequest[100000];
        Arrays.fill(array, Messages.SimpleRequest.getDefaultInstance());

        FLUX_REQUEST = Flux.fromArray(array);
    }


    private Server         reactiveServer;
    private ManagedChannel reactiveChannel;

    private ReactorBenchmarkServiceGrpc.ReactorBenchmarkServiceStub reactiveClient;

    @Setup
    public void setup() throws IOException {
        System.out.println("---------- SETUP ONCE -------------");
        ScheduledExecutorService scheduledExecutorService =
            Executors.newScheduledThreadPool(Runtime.getRuntime()
                                                    .availableProcessors());
        reactiveServer =
            InProcessServerBuilder.forName("benchmark-reactiveServer")
                                  .scheduledExecutorService(scheduledExecutorService)
                                  .addService(new BenchmarkReactorServerServiceImpl(100000))
                                  .build()
                                  .start();

        reactiveChannel = InProcessChannelBuilder.forName("benchmark-reactiveServer")
                                                 .build();
        reactiveClient = ReactorBenchmarkServiceGrpc.newReactorStub(reactiveChannel);
    }

    @TearDown
    public void tearDown() throws InterruptedException {
        System.out.println("---------- TEAR DOWN ONCE -------------");
        reactiveServer.shutdownNow();
        reactiveChannel.shutdownNow();
        reactiveServer.awaitTermination(1000, TimeUnit.MILLISECONDS);
        reactiveChannel.awaitTermination(1000, TimeUnit.MILLISECONDS);
    }

    @Benchmark
    public Object reactiveUnaryCall(Blackhole blackhole) throws InterruptedException {
        PerfSubscriber actual = new PerfSubscriber(blackhole);

        reactiveClient.unaryCall(MONO_REQUEST)
                      .subscribe(actual);
        actual.latch.await();

        return actual;
    }

    @Benchmark
    public Object reactiveServerStreamingCall(Blackhole blackhole)
        throws InterruptedException {
        PerfSubscriber actual = new PerfSubscriber(blackhole);

        reactiveClient.streamingFromServer(MONO_REQUEST)
                      .subscribe(actual);

        actual.latch.await();

        return actual;
    }

    @Benchmark
    public Object reactiveClientStreamingCall(Blackhole blackhole) throws InterruptedException {
        PerfSubscriber actual = new PerfSubscriber(blackhole);

        reactiveClient.streamingFromClient(FLUX_REQUEST)
                      .subscribe(actual);

        actual.latch.await();

        return actual;
    }


    @Benchmark
    public Object reactiveBothWaysStreamingCall(Blackhole blackhole) throws InterruptedException {
        PerfSubscriber actual = new PerfSubscriber(blackhole);

        reactiveClient.streamingBothWays(FLUX_REQUEST)
                      .subscribe(actual);

        actual.latch.await();

        return actual;
    }
}
