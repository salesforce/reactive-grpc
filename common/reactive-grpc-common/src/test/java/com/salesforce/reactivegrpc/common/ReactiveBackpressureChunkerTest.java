package com.salesforce.reactivegrpc.common;

import org.junit.Test;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import static org.assertj.core.api.Assertions.assertThat;

public class ReactiveBackpressureChunkerTest {
    @Test
    public void applySubscribes() {
        ReactiveBackpressureChunker<Object> chunker = new ReactiveBackpressureChunker<Object>(16);

        UpstreamSubscription upstreamSubscription = new UpstreamSubscription();
        DownstreamSubscriber downstreamSubscriber = new DownstreamSubscriber();

        Subscriber<Object> chunkSubscriber = chunker.apply(downstreamSubscriber);
        assertThat(chunkSubscriber).isNotNull();

        chunkSubscriber.onSubscribe(upstreamSubscription);
        assertThat(downstreamSubscriber.upstreamSubscription).isNotNull();
    }

    @Test
    public void requestOneGetsAChunk() {
        int chunkSize = 16;
        ReactiveBackpressureChunker<Object> chunker = new ReactiveBackpressureChunker<Object>(chunkSize);
        UpstreamSubscription upstreamSubscription = new UpstreamSubscription();
        DownstreamSubscriber downstreamSubscriber = new DownstreamSubscriber();

        Subscriber<Object> chunkSubscriber = chunker.apply(downstreamSubscriber);
        chunkSubscriber.onSubscribe(upstreamSubscription);

        downstreamSubscriber.upstreamSubscription.request(1);
        assertThat(upstreamSubscription.lastRequested).isEqualTo(chunkSize);
    }

    @Test
    public void requestOneSupplyOneDoesntRequestAnother() {
        int chunkSize = 16;
        ReactiveBackpressureChunker<Object> chunker = new ReactiveBackpressureChunker<Object>(chunkSize);
        UpstreamSubscription upstreamSubscription = new UpstreamSubscription();
        DownstreamSubscriber downstreamSubscriber = new DownstreamSubscriber();

        Subscriber<Object> chunkSubscriber = chunker.apply(downstreamSubscriber);
        chunkSubscriber.onSubscribe(upstreamSubscription);

        downstreamSubscriber.upstreamSubscription.request(1);
        send(chunkSubscriber, 1);
        assertThat(upstreamSubscription.totalRequested).isEqualTo(chunkSize);
    }

    @Test
    public void requestManyGetsAChunkFirst() {
        int chunkSize = 16;
        ReactiveBackpressureChunker<Object> chunker = new ReactiveBackpressureChunker<Object>(chunkSize);
        UpstreamSubscription upstreamSubscription = new UpstreamSubscription();
        DownstreamSubscriber downstreamSubscriber = new DownstreamSubscriber();

        Subscriber<Object> chunkSubscriber = chunker.apply(downstreamSubscriber);
        chunkSubscriber.onSubscribe(upstreamSubscription);

        downstreamSubscriber.upstreamSubscription.request(256);
        assertThat(upstreamSubscription.lastRequested).isEqualTo(chunkSize);
    }

    @Test
    public void requestManyChunksRequestsAsSatisfiedAndStopsWhenComplete() {
        int chunkSize = 3;
        ReactiveBackpressureChunker<Object> chunker = new ReactiveBackpressureChunker<Object>(chunkSize);
        UpstreamSubscription upstreamSubscription = new UpstreamSubscription();
        DownstreamSubscriber downstreamSubscriber = new DownstreamSubscriber();

        Subscriber<Object> chunkSubscriber = chunker.apply(downstreamSubscriber);
        chunkSubscriber.onSubscribe(upstreamSubscription);

        downstreamSubscriber.upstreamSubscription.request(9);
        assertThat(upstreamSubscription.lastRequested).isEqualTo(chunkSize);
        assertThat(upstreamSubscription.totalRequested).isEqualTo(chunkSize);

        send(chunkSubscriber, 1);
        assertThat(upstreamSubscription.lastRequested).isEqualTo(chunkSize);
        assertThat(upstreamSubscription.totalRequested).isEqualTo(3);
        send(chunkSubscriber, 1);
        assertThat(upstreamSubscription.lastRequested).isEqualTo(chunkSize);
        assertThat(upstreamSubscription.totalRequested).isEqualTo(3);
        send(chunkSubscriber, 1);
        // Chunk satisfied, request next chunk
        assertThat(upstreamSubscription.lastRequested).isEqualTo(chunkSize);
        assertThat(upstreamSubscription.totalRequested).isEqualTo(6);

        send(chunkSubscriber, 1);
        assertThat(upstreamSubscription.lastRequested).isEqualTo(chunkSize);
        assertThat(upstreamSubscription.totalRequested).isEqualTo(6);
        send(chunkSubscriber, 1);
        assertThat(upstreamSubscription.lastRequested).isEqualTo(chunkSize);
        assertThat(upstreamSubscription.totalRequested).isEqualTo(6);
        send(chunkSubscriber, 1);
        // Chunk satisfied, request next chunk
        assertThat(upstreamSubscription.lastRequested).isEqualTo(chunkSize);
        assertThat(upstreamSubscription.totalRequested).isEqualTo(9);

        send(chunkSubscriber, 1);
        assertThat(upstreamSubscription.lastRequested).isEqualTo(chunkSize);
        assertThat(upstreamSubscription.totalRequested).isEqualTo(9);
        send(chunkSubscriber, 1);
        assertThat(upstreamSubscription.lastRequested).isEqualTo(chunkSize);
        assertThat(upstreamSubscription.totalRequested).isEqualTo(9);
        send(chunkSubscriber, 1);
        // Requested satisfied, do not request any more
        assertThat(upstreamSubscription.lastRequested).isEqualTo(chunkSize);
        assertThat(upstreamSubscription.totalRequested).isEqualTo(9);
    }

    @Test
    public void completePropagatesDown() {
        int chunkSize = 3;
        ReactiveBackpressureChunker<Object> chunker = new ReactiveBackpressureChunker<Object>(chunkSize);
        UpstreamSubscription upstreamSubscription = new UpstreamSubscription();
        DownstreamSubscriber downstreamSubscriber = new DownstreamSubscriber();

        Subscriber<Object> chunkSubscriber = chunker.apply(downstreamSubscriber);
        chunkSubscriber.onSubscribe(upstreamSubscription);

        chunkSubscriber.onComplete();
        assertThat(downstreamSubscriber.isComplete).isTrue();
    }

    @Test
    public void errorPropagatesDown() {
        int chunkSize = 3;
        ReactiveBackpressureChunker<Object> chunker = new ReactiveBackpressureChunker<Object>(chunkSize);
        UpstreamSubscription upstreamSubscription = new UpstreamSubscription();
        DownstreamSubscriber downstreamSubscriber = new DownstreamSubscriber();

        Subscriber<Object> chunkSubscriber = chunker.apply(downstreamSubscriber);
        chunkSubscriber.onSubscribe(upstreamSubscription);

        Throwable t = new Throwable();
        chunkSubscriber.onError(t);
        assertThat(downstreamSubscriber.lastThrowable).isEqualTo(t);
    }

    @Test
    public void cancelPropagatesUp() {
        int chunkSize = 3;
        ReactiveBackpressureChunker<Object> chunker = new ReactiveBackpressureChunker<Object>(chunkSize);
        UpstreamSubscription upstreamSubscription = new UpstreamSubscription();
        DownstreamSubscriber downstreamSubscriber = new DownstreamSubscriber();

        Subscriber<Object> chunkSubscriber = chunker.apply(downstreamSubscriber);
        chunkSubscriber.onSubscribe(upstreamSubscription);

        downstreamSubscriber.upstreamSubscription.cancel();
        assertThat(upstreamSubscription.isCancelled).isTrue();
    }

    private void send(Subscriber<Object> subscriber, int count) {
        for (int i = 0; i < count; i++) {
            subscriber.onNext(new Object());
        }
    }

    private static class UpstreamSubscription implements Subscription {
        public long totalRequested = 0;
        public long lastRequested;
        public boolean isCancelled;

        @Override
        public void request(long l) {
            lastRequested = l;
            totalRequested += l;
        }

        @Override
        public void cancel() {
            isCancelled = true;
        }
    }

    private static class DownstreamSubscriber implements Subscriber<Object> {
        public Subscription upstreamSubscription;
        public int totalReceived = 0;
        public Object lastNext;
        public Throwable lastThrowable;
        public boolean isComplete;

        @Override
        public void onSubscribe(Subscription subscription) {
            upstreamSubscription = subscription;
        }

        @Override
        public void onNext(Object obj) {
            totalReceived++;
            lastNext = obj;
        }

        @Override
        public void onError(Throwable throwable) {
            lastThrowable = throwable;
        }

        @Override
        public void onComplete() {
            isComplete = true;
        }
    }
}
