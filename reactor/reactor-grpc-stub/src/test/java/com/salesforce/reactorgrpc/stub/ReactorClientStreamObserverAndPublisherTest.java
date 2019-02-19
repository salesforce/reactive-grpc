/*  Copyright (c) 2019, Salesforce.com, Inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.reactorgrpc.stub;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.Nullable;

import io.grpc.stub.ClientCallStreamObserver;
import io.grpc.stub.StreamObserver;
import org.assertj.core.api.Assertions;
import org.junit.Test;
import reactor.core.Fuseable;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

public class ReactorClientStreamObserverAndPublisherTest {

    @Test
    public void multiThreadedProducerTest() {
        ReactorClientStreamObserverAndPublisher<Long> processor =
                new ReactorClientStreamObserverAndPublisher<>(null);
        int countPerThread = 100000;
        TestCallStreamObserver observer = new TestCallStreamObserver(processor, countPerThread);
        processor.beforeStart(observer);
        StepVerifier.create(Flux.from(processor)
                                .subscribeOn(Schedulers.single()))
                    .expectNextCount(countPerThread)
                    .verifyComplete();

        Assertions.assertThat(observer.requestsQueue.size()).isEqualTo(countPerThread / 16 + 1);
    }

    @Test
    public void producerFusedTest() {
        ReactorClientStreamObserverAndPublisher<Long> processor =
                new ReactorClientStreamObserverAndPublisher<>(null);
        int countPerThread = 100000;
        TestCallStreamObserver observer = new TestCallStreamObserver(processor, countPerThread);
        processor.beforeStart(observer);
        StepVerifier.create(Flux.from(processor))
                    .expectFusion(Fuseable.ANY, Fuseable.ASYNC)
                    .expectNextCount(countPerThread)
                    .verifyComplete();

        Assertions.assertThat(observer.requestsQueue.size()).isEqualTo(countPerThread / 16 + 1);
    }

    static class TestCallStreamObserver extends ClientCallStreamObserver<Long> {


        final Queue<Integer> requestsQueue = new ConcurrentLinkedQueue<>();
        final AtomicLong requested = new AtomicLong();

        final StreamObserver<Long> subscriber;

        final long max;


        volatile boolean cancelled;

        long index = 0;

        TestCallStreamObserver(StreamObserver<Long> subscriber, long max) {
            this.subscriber = subscriber;
            this.max = max;
        }

        @Override
        public void cancel(@Nullable String message, @Nullable Throwable cause) {
            cancelled = true;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void onNext(Long value) {

        }

        @Override
        public void onError(Throwable t) {

        }

        @Override
        public void onCompleted() {

        }

        @Override
        public void setOnReadyHandler(Runnable onReadyHandler) {

        }

        @Override
        public void disableAutoInboundFlowControl() {

        }

        @Override
        public void request(int count) {
            requestsQueue.add(count);

            long n = count;

            long initialRequested;

            do {
                initialRequested = requested.get();

                if (initialRequested == Long.MAX_VALUE) {
                    return;
                }

                n = initialRequested + n;

                if (n <= 0) {
                    n = Long.MAX_VALUE;
                }

            } while (!requested.compareAndSet(initialRequested, n));

            if (initialRequested > 0) {
                return;
            }

            new Thread(() -> {
                int sent = 0;

                while (true) {

                    for (; sent < requested.get() && index < max; sent++, index++) {

                        if (cancelled) {
                            return;
                        }

                        subscriber.onNext(index);
                    }

                    if (cancelled) {
                        return;
                    }

                    if (index == max) {
                        subscriber.onCompleted();
                        return;
                    }

                    if (requested.addAndGet(-sent) == 0) {
                        return;
                    }

                    sent = 0;
                }
            }).start();
        }

        @Override
        public void setMessageCompression(boolean enable) {

        }
    }

}