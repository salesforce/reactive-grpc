/*
 *  Copyright (c) 2019, Salesforce.com, Inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.reactorgrpc.stub;

import com.salesforce.reactivegrpc.common.AbstractClientStreamObserverAndPublisher;
import com.salesforce.reactivegrpc.common.Consumer;
import io.grpc.stub.CallStreamObserver;
import reactor.core.CoreSubscriber;
import reactor.core.Fuseable;
import reactor.core.publisher.Operators;
import reactor.util.concurrent.Queues;

import java.util.Queue;

/**
 * TODO: Explain what this class does.
 * @param <T> T
 */
class ReactorClientStreamObserverAndPublisher<T>
        extends AbstractClientStreamObserverAndPublisher<T> implements Fuseable.QueueSubscription<T>, Fuseable {

    ReactorClientStreamObserverAndPublisher(Consumer<CallStreamObserver<?>> onSubscribe) {
        super(Queues.<T>get(DEFAULT_CHUNK_SIZE).get(), onSubscribe);
    }

    ReactorClientStreamObserverAndPublisher(
            Consumer<CallStreamObserver<?>> onSubscribe,
            Runnable onTerminate) {
        super(Queues.<T>get(DEFAULT_CHUNK_SIZE).get(), onSubscribe, onTerminate);
    }

    ReactorClientStreamObserverAndPublisher(
            Consumer<CallStreamObserver<?>> onSubscribe,
            Runnable onTerminate,
            int prefetch,
            int lowTide) {
        super(Queues.<T>get(DEFAULT_CHUNK_SIZE).get(), onSubscribe, onTerminate, prefetch, lowTide);
    }

    @Override
    public int requestFusion(int requestedMode) {
        if ((requestedMode & Fuseable.ASYNC) != 0) {
            outputFused = true;
            return Fuseable.ASYNC;
        }
        return Fuseable.NONE;
    }

    @Override
    protected void discardQueue(Queue<T> q) {
        if (downstream instanceof CoreSubscriber) {
            Operators.onDiscardQueueWithClear(q, ((CoreSubscriber) downstream).currentContext(), null);
        } else {
            q.clear();
        }
    }

    @Override
    protected void discardElement(T t) {
        if (downstream instanceof CoreSubscriber) {
            Operators.onDiscard(t, ((CoreSubscriber) downstream).currentContext());
        }
    }
}
