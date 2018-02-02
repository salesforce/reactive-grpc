/*
 * Copyright, 1999-2018, SALESFORCE.com
 * All Rights Reserved
 * Company Confidential
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

public class ResumeStreamDemoReactor {
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
                .forName("ResumeStreamDemoReactor")
                .addService(new FlakyNumberService())
                .build()
                .start();
        ManagedChannel channel = InProcessChannelBuilder
                .forName("ResumeStreamDemoReactor")
                .usePlaintext(true)
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

    private static class GrpcRetryFlux<T> extends Flux<T> {
        private final Flux<T> retryFlux;

        public GrpcRetryFlux(Supplier<Flux<T>> fluxSupplier) {
            this.retryFlux = Flux.<T>create(sink -> fluxSupplier.get().subscribe(sink::next, sink::error, sink::complete))
                    .retryWhen(attempts -> attempts.doOnNext(err -> System.out.println("Retrying stream")));
        }

        @Override
        public void subscribe(CoreSubscriber<? super T> actual) {
            retryFlux.subscribe(actual);
        }
    }
}
