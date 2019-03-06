/*  Copyright (c) 2019, Salesforce.com, Inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.reactorgrpc.stub;

import java.util.concurrent.ForkJoinPool;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import reactor.core.Fuseable;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;


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

        Assertions.assertThat(observer.requestsQueue.size()).isBetween((countPerThread - DEFAULT_CHUNK_SIZE) / PART_OF_CHUNK + 1, (countPerThread - DEFAULT_CHUNK_SIZE) / PART_OF_CHUNK + 3);
    }

    @Test
    public void producerFusedTest() {
        ReactorClientStreamObserverAndPublisher<Integer> processor =
                new ReactorClientStreamObserverAndPublisher<>(null);
        int countPerThread = 100000;
        TestCallStreamObserverProducer observer = new TestCallStreamObserverProducer(ForkJoinPool.commonPool(), processor, countPerThread);
        processor.beforeStart(observer);
        StepVerifier.create(Flux.from(processor))
                    .expectFusion(Fuseable.ANY, Fuseable.ASYNC)
                    .expectNextCount(countPerThread)
                    .verifyComplete();

        Assertions.assertThat(observer.requestsQueue.size()).isBetween((countPerThread - DEFAULT_CHUNK_SIZE) / PART_OF_CHUNK + 1, (countPerThread - DEFAULT_CHUNK_SIZE) / PART_OF_CHUNK + 3);
    }
}