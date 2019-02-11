package com.salesforce.reactivegrpc.common;

import java.util.Queue;

import io.reactivex.internal.fuseable.QueueFuseable;
import io.reactivex.internal.fuseable.QueueSubscription;

public class TestStreamObserverAndPublisherWithFusion<T> extends AbstractStreamObserverAndPublisher<T>
        implements QueueSubscription<T> {

    public TestStreamObserverAndPublisherWithFusion(Queue queue, Consumer onSubscribe) {
        super(queue, onSubscribe);
    }

    public TestStreamObserverAndPublisherWithFusion(Queue queue,
            Consumer onSubscribe,
            Runnable onTerminate) {
        super(queue, onSubscribe, onTerminate);
    }

    public TestStreamObserverAndPublisherWithFusion(Queue queue,
            int prefetch,
            Consumer onSubscribe) {
        super(queue, prefetch, onSubscribe);
    }

    public TestStreamObserverAndPublisherWithFusion(Queue queue,
            int prefetch,
            Consumer onSubscribe,
            Runnable onTerminate) {
        super(queue, prefetch, onSubscribe, onTerminate);
    }

    @Override
    public int requestFusion(int requestedMode) {
        if ((requestedMode & QueueFuseable.ASYNC) != 0) {
            outputFused = true;
            return QueueFuseable.ASYNC;
        }
        return QueueFuseable.NONE;
    }

    @Override
    public boolean offer(T t, T t1) {
        throw new UnsupportedOperationException();
    }
}
