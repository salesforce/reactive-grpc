/*  Copyright (c) 2019, Salesforce.com, Inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.reactivegrpc.common;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import io.reactivex.Flowable;
import io.reactivex.functions.Function;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subscribers.TestSubscriber;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;

import static com.salesforce.reactivegrpc.common.AbstractStreamObserverAndPublisher.DEFAULT_CHUNK_SIZE;
import static com.salesforce.reactivegrpc.common.AbstractStreamObserverAndPublisher.TWO_THIRDS_OF_DEFAULT_CHUNK_SIZE;
import static org.assertj.core.api.Assertions.assertThat;

public class BackpressureChunkingTest {
    @Test
    public void chunkOperatorCorrectlyChunksInfiniteRequest() {
        int chunkSize = DEFAULT_CHUNK_SIZE;

        int partOfChunk = TWO_THIRDS_OF_DEFAULT_CHUNK_SIZE;
        int num = chunkSize * 2;

        AbstractStreamObserverAndPublisher<Long> source =
                new TestStreamObserverAndPublisherWithFusion<Long>(new ConcurrentLinkedQueue<Long>(), null);
        AsyncRangeCallStreamObserver observer = new AsyncRangeCallStreamObserver(Executors.newSingleThreadExecutor(), source, num);
        source.onSubscribe(observer);
        TestSubscriber<Long> testSubscriber = Flowable.fromPublisher(source)
                                                      .test();


        testSubscriber.awaitTerminalEvent();
        testSubscriber.assertComplete();

        assertThat(observer.requestsQueue).containsExactly(chunkSize, partOfChunk, partOfChunk, partOfChunk);
        assertThat(source.outputFused).isFalse();
    }

    @Test
    public void chunkOperatorCorrectlyChunksFiniteRequest() {
        int chunkSize = DEFAULT_CHUNK_SIZE;

        int partOfChunk = TWO_THIRDS_OF_DEFAULT_CHUNK_SIZE;
        int num = chunkSize * 2;

        AbstractStreamObserverAndPublisher<Long> source =
                new TestStreamObserverAndPublisherWithFusion<Long>(new ConcurrentLinkedQueue<Long>(), null);
        AsyncRangeCallStreamObserver observer = new AsyncRangeCallStreamObserver(Executors.newSingleThreadExecutor(), source, num);
        source.onSubscribe(observer);
        TestSubscriber<Long> testSubscriber = Flowable.fromPublisher(source)
                                                      .test(num);

        testSubscriber.awaitTerminalEvent();
        testSubscriber.assertComplete();

        assertThat(observer.requestsQueue).containsExactly(chunkSize, partOfChunk, partOfChunk, partOfChunk);
        assertThat(source.outputFused).isFalse();
    }

    @Test
    public void chunkOperatorCorrectlyChunksInfiniteRequestFusion() {
        int chunkSize = DEFAULT_CHUNK_SIZE;

        int partOfChunk = TWO_THIRDS_OF_DEFAULT_CHUNK_SIZE;
        int num = chunkSize * 2;

        AbstractStreamObserverAndPublisher<Long> source =
                new TestStreamObserverAndPublisherWithFusion<Long>(new ConcurrentLinkedQueue<Long>(), null);
        AsyncRangeCallStreamObserver observer = new AsyncRangeCallStreamObserver(Executors.newSingleThreadExecutor(), source, num);
        source.onSubscribe(observer);
        TestSubscriber<Long> testSubscriber = Flowable.fromPublisher(source)
                                                      .observeOn(Schedulers.trampoline())
                                                      .test();


        testSubscriber.awaitTerminalEvent();
        testSubscriber.assertComplete();

        assertThat(observer.requestsQueue).containsExactly(chunkSize, partOfChunk, partOfChunk, partOfChunk);
        assertThat(source.outputFused).isTrue();
    }

    @Test
    public void chunkOperatorCorrectlyChunksFiniteRequestFusion() {
        int chunkSize = DEFAULT_CHUNK_SIZE;

        int partOfChunk = TWO_THIRDS_OF_DEFAULT_CHUNK_SIZE;
        int num = chunkSize * 2;

        AbstractStreamObserverAndPublisher<Long> source =
                new TestStreamObserverAndPublisherWithFusion<Long>(new ConcurrentLinkedQueue<Long>(), null);
        AsyncRangeCallStreamObserver observer = new AsyncRangeCallStreamObserver(Executors.newSingleThreadExecutor(), source, num);
        source.onSubscribe(observer);
        TestSubscriber<Long> testSubscriber = Flowable.fromPublisher(source)
                                                      .observeOn(Schedulers.trampoline())
                                                      .test(num);

        testSubscriber.awaitTerminalEvent();
        testSubscriber.assertComplete();

        assertThat(observer.requestsQueue).containsExactly(chunkSize, partOfChunk, partOfChunk, partOfChunk);
        assertThat(source.outputFused).isTrue();
    }

    /**
     * https://github.com/salesforce/reactive-grpc/issues/120
     */
    @Test
    public void chunkOperatorWorksWithConcatMap() {
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

        testSubscriber.awaitTerminalEvent();
        testSubscriber.assertNoErrors();

        assertThat(observer.requestsQueue).containsExactly(chunkSize);
    }
}
