/*
 *  Copyright (c) 2017, salesforce.com, inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.reactivegrpc.examples;

import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import reactor.core.CoreSubscriber;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * A demo for re-establishing a server stream in the face of an error using Reactor.
 */
public final class ResumeStreamReactorDemo {
    private ResumeStreamReactorDemo() { }

    /**
     * FlakyNumberService tries to return the values 1..10, but fails most of the time.
     */
    // CHECKSTYLE DISABLE MagicNumber FOR 10 LINES
    private static class FlakyNumberService extends ReactorNumbersGrpc.NumbersImplBase {
        @Override
        public Flux<Message> oneToMany(Mono<Message> request) {
            return Flux
                    .range(1, 10)
                    .map(i -> {
                        if (ThreadLocalRandom.current().nextInt(3) == 0) {
                            throw new RuntimeException("Oops.");
                        }
                        return Message.newBuilder().setNumber(i).build();
                    });
        }
    }

    public static void main(String[] args) throws Exception {
        Server server = InProcessServerBuilder
                .forName("ResumeStreamReactorDemo")
                .addService(new FlakyNumberService())
                .build()
                .start();
        ManagedChannel channel = InProcessChannelBuilder
                .forName("ResumeStreamReactorDemo")
                .usePlaintext()
                .build();
        ReactorNumbersGrpc.ReactorNumbersStub stub = ReactorNumbersGrpc.newReactorStub(channel);

        // Keep retrying the stream until you get ten in a row with no error
        new GrpcRetryFlux<>(() -> stub.oneToMany(Mono.just(Message.getDefaultInstance())))
                .map(Message::getNumber)
                .subscribe(System.out::println);

        Thread.sleep(TimeUnit.SECONDS.toMillis(1));
        channel.shutdownNow();
        server.shutdownNow();
    }

    /**
     * GrpcRetryFlux automatically restarts a gRPC Flux stream in the face of an error.
     * @param <T>
     */
    private static class GrpcRetryFlux<T> extends Flux<T> {
        private final Flux<T> retryFlux;

        GrpcRetryFlux(Supplier<Flux<T>> fluxSupplier) {
            this.retryFlux = Flux.defer(fluxSupplier::get).retry();
        }

        @Override
        public void subscribe(CoreSubscriber<? super T> actual) {
            retryFlux.subscribe(actual);
        }
    }
}
