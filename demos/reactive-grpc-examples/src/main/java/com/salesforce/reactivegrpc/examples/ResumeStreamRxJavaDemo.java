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
import io.reactivex.Flowable;
import io.reactivex.Single;
import org.reactivestreams.Subscriber;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * A demo for re-establishing a server stream in the face of an error using RxJava.
 */
public final class ResumeStreamRxJavaDemo {
    private ResumeStreamRxJavaDemo() { }

    /**
     * FlakyNumberService tries to return the values 1..10, but fails most of the time.
     */
    // CHECKSTYLE DISABLE MagicNumber FOR 10 LINES
    private static class FlakyNumberService extends RxNumbersGrpc.NumbersImplBase {
        @Override
        public Flowable<Message> oneToMany(Single<Message> request) {
            return Flowable
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
        RxNumbersGrpc.RxNumbersStub stub = RxNumbersGrpc.newRxStub(channel);

        // Keep retrying the stream until you get ten in a row with no error
        new GrpcRetryFlowable<>(() -> stub.oneToMany(Single.just(Message.getDefaultInstance())))
                .map(Message::getNumber)
                .subscribe(System.out::println);

        Thread.sleep(TimeUnit.SECONDS.toMillis(1));
        channel.shutdownNow();
        server.shutdownNow();
    }

    /**
     * GrpcRetryFlowable automatically restarts a gRPC Flowable stream in the face of an error.
     * @param <T>
     */
    private static class GrpcRetryFlowable<T> extends Flowable<T> {
        private final Flowable<T> retryFlowable;

        GrpcRetryFlowable(Supplier<Flowable<T>> flowableSupplier) {
            this.retryFlowable = Flowable.defer(flowableSupplier::get).retry();
        }

        @Override
        protected void subscribeActual(Subscriber<? super T> s) {
            retryFlowable.subscribe(s);
        }
    }
}
