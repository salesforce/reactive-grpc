/*
 *  Copyright (c) 2019, Salesforce.com, Inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.rx3grpc.stub;

import com.salesforce.reactivegrpc.common.AbstractClientStreamObserverAndPublisher;
import com.salesforce.reactivegrpc.common.Consumer;

import io.grpc.stub.CallStreamObserver;
import io.reactivex.rxjava3.operators.QueueFuseable;
import io.reactivex.rxjava3.operators.QueueSubscription;
import io.reactivex.rxjava3.operators.SpscArrayQueue;

/**
 * TODO: Explain what this class does.
 *
 * @param <T> T
 */
class RxClientStreamObserverAndPublisher<T>
        extends AbstractClientStreamObserverAndPublisher<T>
        implements QueueSubscription<T> {

    RxClientStreamObserverAndPublisher(
            Consumer<CallStreamObserver<?>> onSubscribe,
            Runnable onTerminate) {
        super(new SimpleQueueAdapter<T>(new SpscArrayQueue<T>(DEFAULT_CHUNK_SIZE)), onSubscribe, onTerminate);
    }

    RxClientStreamObserverAndPublisher(
            Consumer<CallStreamObserver<?>> onSubscribe,
            Runnable onTerminate,
            int prefetch,
            int lowTide) {
        super(new SimpleQueueAdapter<T>(new SpscArrayQueue<T>(prefetch)), onSubscribe, onTerminate, prefetch, lowTide);
    }

    @Override
    public int requestFusion(int requestedMode) {
        if ((requestedMode & QueueFuseable.ASYNC) != 0) {
            outputFused = true;
            return QueueFuseable.ASYNC;
        }
        return QueueFuseable.NONE;
    }
}
