/*  Copyright (c) 2019, Salesforce.com, Inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.reactivegrpc.common;

import static com.salesforce.reactivegrpc.common.AbstractStreamObserverAndPublisher.DEFAULT_CHUNK_SIZE;
import static com.salesforce.reactivegrpc.common.AbstractStreamObserverAndPublisher.TWO_THIRDS_OF_DEFAULT_CHUNK_SIZE;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;

import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.functions.Function;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subscribers.TestSubscriber;

public class BackpressureChunkingRx3Test {
    @Test
    public void chunkOperatorCorrectlyChunksInfiniteRequest() throws InterruptedException {
        int chunkSize = DEFAULT_CHUNK_SIZE;

        int partOfChunk = TWO_THIRDS_OF_DEFAULT_CHUNK_SIZE;
        int num = chunkSize * 2;

        AbstractStreamObserverAndPublisher<Long> source =
                new TestStreamObserverAndPublisherWithFusionRx3<Long>(new ConcurrentLinkedQueue<Long>(), null);
        AsyncRangeCallStreamObserver observer = new AsyncRangeCallStreamObserver(Executors.newSingleThreadExecutor(), source, num);
        source.onSubscribe(observer);
        TestSubscriber<Long> testSubscriber = Flowable.fromPublisher(source)
                                                      .test();


		testSubscriber.await(30, TimeUnit.SECONDS);
        testSubscriber.assertComplete();

        assertThat(observer.requestsQueue).containsExactly(chunkSize, partOfChunk, partOfChunk, partOfChunk);
        assertThat(source.outputFused).isFalse();
    }

    @Test
    public void chunkOperatorCorrectlyChunksFiniteRequest() throws InterruptedException {
        int chunkSize = DEFAULT_CHUNK_SIZE;

        int partOfChunk = TWO_THIRDS_OF_DEFAULT_CHUNK_SIZE;
        int num = chunkSize * 2;

        AbstractStreamObserverAndPublisher<Long> source =
                new TestStreamObserverAndPublisherWithFusionRx3<Long>(new ConcurrentLinkedQueue<Long>(), null);
        AsyncRangeCallStreamObserver observer = new AsyncRangeCallStreamObserver(Executors.newSingleThreadExecutor(), source, num);
        source.onSubscribe(observer);
        TestSubscriber<Long> testSubscriber = Flowable.fromPublisher(source)
                                                      .test(num);

		testSubscriber.await(30, TimeUnit.SECONDS);
        testSubscriber.assertComplete();

        assertThat(observer.requestsQueue).containsExactly(chunkSize, partOfChunk, partOfChunk, partOfChunk);
        assertThat(source.outputFused).isFalse();
    }

    @Test
    public void chunkOperatorCorrectlyChunksInfiniteRequestFusion() throws InterruptedException {
        int chunkSize = DEFAULT_CHUNK_SIZE;

        int partOfChunk = TWO_THIRDS_OF_DEFAULT_CHUNK_SIZE;
        int num = chunkSize * 2;

        AbstractStreamObserverAndPublisher<Long> source =
                new TestStreamObserverAndPublisherWithFusionRx3<Long>(new ConcurrentLinkedQueue<Long>(), null);
        AsyncRangeCallStreamObserver observer = new AsyncRangeCallStreamObserver(Executors.newSingleThreadExecutor(), source, num);
        source.onSubscribe(observer);
        TestSubscriber<Long> testSubscriber = Flowable.fromPublisher(source)
                                                      .observeOn(Schedulers.trampoline())
                                                      .test();


		testSubscriber.await(30, TimeUnit.SECONDS);
        testSubscriber.assertComplete();

        assertThat(observer.requestsQueue).containsExactly(chunkSize, partOfChunk, partOfChunk, partOfChunk);
        assertThat(source.outputFused).isTrue();
    }

    @Test
    public void chunkOperatorCorrectlyChunksFiniteRequestFusion() throws InterruptedException {
        int chunkSize = DEFAULT_CHUNK_SIZE;

        int partOfChunk = TWO_THIRDS_OF_DEFAULT_CHUNK_SIZE;
        int num = chunkSize * 2;

        AbstractStreamObserverAndPublisher<Long> source =
                new TestStreamObserverAndPublisherWithFusionRx3<Long>(new ConcurrentLinkedQueue<Long>(), null);
        AsyncRangeCallStreamObserver observer = new AsyncRangeCallStreamObserver(Executors.newSingleThreadExecutor(), source, num);
        source.onSubscribe(observer);
        TestSubscriber<Long> testSubscriber = Flowable.fromPublisher(source)
                                                      .observeOn(Schedulers.trampoline())
                                                      .test(num);

		testSubscriber.await(30, TimeUnit.SECONDS);
        testSubscriber.assertComplete();

        assertThat(observer.requestsQueue).containsExactly(chunkSize, partOfChunk, partOfChunk, partOfChunk);
        assertThat(source.outputFused).isTrue();
    }

    /**
     * https://github.com/salesforce/reactive-grpc/issues/120
     */
    @Test
    public void chunkOperatorWorksWithConcatMap() throws InterruptedException {
        int chunkSize = DEFAULT_CHUNK_SIZE;

        AbstractStreamObserverAndPublisher<Long> source =
                new AbstractStreamObserverAndPublisher<Long>(new ConcurrentLinkedQueue<Long>(), null){};
        AsyncRangeCallStreamObserver observer = new AsyncRangeCallStreamObserver(Executors.newSingleThreadExecutor(), source, 24);
        source.onSubscribe(observer);
        TestSubscriber<Long> testSubscriber = Flowable.fromPublisher(source)
                                                      .concatMap(new Function<Long, Publisher<Long>>() {
                                                          @Override
                                                          public Publisher<Long> apply(Long item) throws Exception {
                                                              return Flowable.just(item).delay(3, TimeUnit.MILLISECONDS);
                                                          }
                                                      })
                                                      .test();

		testSubscriber.await(30, TimeUnit.SECONDS);
        testSubscriber.assertNoErrors();

        assertThat(observer.requestsQueue).containsExactly(chunkSize);
    }
}
