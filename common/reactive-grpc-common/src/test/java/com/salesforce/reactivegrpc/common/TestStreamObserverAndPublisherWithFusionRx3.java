/*  Copyright (c) 2019, Salesforce.com, Inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */
package com.salesforce.reactivegrpc.common;

import java.util.Queue;

import com.salesforce.reactivegrpc.common.AbstractStreamObserverAndPublisher;
import com.salesforce.reactivegrpc.common.Consumer;

import io.grpc.stub.CallStreamObserver;
import io.reactivex.rxjava3.internal.fuseable.QueueFuseable;
import io.reactivex.rxjava3.internal.fuseable.QueueSubscription;

/**
 * This class is a test-purpose implementation of the
 * {@link AbstractStreamObserverAndPublisher} class that supports fusion from RxJava 2
 * @param <T>
 */
class TestStreamObserverAndPublisherWithFusionRx3<T> extends AbstractStreamObserverAndPublisher<T>
        implements QueueSubscription<T> {

    TestStreamObserverAndPublisherWithFusionRx3(Queue<T> queue, Consumer<CallStreamObserver<?>> onSubscribe) {
        super(queue, onSubscribe);
    }

    TestStreamObserverAndPublisherWithFusionRx3(Queue<T> queue,
            Consumer<CallStreamObserver<?>> onSubscribe,
            Runnable onTerminate) {
        super(queue, onSubscribe, onTerminate);
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
