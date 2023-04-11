/*  Copyright (c) 2019, Salesforce.com, Inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.reactorgrpc.stub;

import org.junit.jupiter.api.Test;
import reactor.core.Fuseable;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;


public class ReactorClientStreamObserverAndPublisherTest {

    private static final int DEFAULT_CHUNK_SIZE = 512;
    private static final int PART_OF_CHUNK = DEFAULT_CHUNK_SIZE * 2 / 3;

    @Test
    public void multiThreadedProducerTest() {
        ReactorClientStreamObserverAndPublisher<Integer> processor =
            new ReactorClientStreamObserverAndPublisher<>(null);
        int countPerThread = 100000;
        TestCallStreamObserverProducer observer =
            new TestCallStreamObserverProducer(ForkJoinPool.commonPool(), processor, countPerThread);
        processor.beforeStart(observer);
        StepVerifier.create(Flux.from(processor)
                                .subscribeOn(Schedulers.single()))
                    .expectNextCount(countPerThread)
                    .verifyComplete();

        assertThat(observer.requestsQueue.size()).isBetween((countPerThread - DEFAULT_CHUNK_SIZE) / PART_OF_CHUNK + 1,
            (countPerThread - DEFAULT_CHUNK_SIZE) / PART_OF_CHUNK + 3);
    }

    @Test
    public void producerFusedTest() {
        ReactorClientStreamObserverAndPublisher<Integer> processor =
            new ReactorClientStreamObserverAndPublisher<>(null);
        int countPerThread = 100000;
        TestCallStreamObserverProducer observer = new TestCallStreamObserverProducer(ForkJoinPool.commonPool(),
            processor, countPerThread);
        processor.beforeStart(observer);
        StepVerifier.create(Flux.from(processor))
                    .expectFusion(Fuseable.ANY, Fuseable.ASYNC)
                    .expectNextCount(countPerThread)
                    .verifyComplete();

        assertThat(observer.requestsQueue.size()).isBetween((countPerThread - DEFAULT_CHUNK_SIZE) / PART_OF_CHUNK + 1,
            (countPerThread - DEFAULT_CHUNK_SIZE) / PART_OF_CHUNK + 3);
    }

    @Test
    public void discardQueueTest() {
        ReactorClientStreamObserverAndPublisher<Integer> processor =
            new ReactorClientStreamObserverAndPublisher<>(null);
        int countPerThread = 5;
        TestCallStreamObserverProducer observer = new TestCallStreamObserverProducer(ForkJoinPool.commonPool(),
            processor, countPerThread);
        processor.beforeStart(observer);

        ConcurrentLinkedQueue<Integer> discardedByObserverAndPublisher = new ConcurrentLinkedQueue<>();
        ConcurrentLinkedQueue<Integer> discardedByPublishOn = new ConcurrentLinkedQueue<>();

        AtomicBoolean firstHandled = new AtomicBoolean();
        Flux<Integer> consumer =
            Flux.from(processor)
                .doOnDiscard(Integer.class, discardedByObserverAndPublisher::add)
                .log("processor")
                .limitRate(1)
                .publishOn(Schedulers.parallel())
                .limitRate(1)
                .doOnDiscard(Integer.class, discardedByPublishOn::add)
                .<Integer>handle((i, sink) -> {
                    if (firstHandled.compareAndSet(false, true)) {
                        try {
                            Thread.sleep(100);
                        } catch (Exception e) {
                            // noop
                        }
                        sink.next(i);
                    } else {
                        sink.complete();
                    }
                })
                .log("handled");

        StepVerifier.create(consumer)
                    .expectNext(0)
                    .verifyComplete();

        // 1 is dropped in handle without invoking the discard hook,
        assertThat(discardedByObserverAndPublisher).containsExactly(3, 4);
        // impl details: processor is able to schedule 2 before it's cancelled
        // also, discard hooks are cumulative, so not using containsExactly
        assertThat(discardedByPublishOn).contains(2);
    }
}