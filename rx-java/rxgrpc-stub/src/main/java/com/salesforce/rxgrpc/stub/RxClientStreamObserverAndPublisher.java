/*
 *  Copyright (c) 2017, salesforce.com, inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.rxgrpc.stub;

import com.salesforce.reactivegrpc.common.AbstractClientStreamObserverAndPublisher;
import com.salesforce.reactivegrpc.common.Consumer;
import io.grpc.stub.CallStreamObserver;
import io.reactivex.internal.fuseable.QueueFuseable;
import io.reactivex.internal.fuseable.QueueSubscription;
import io.reactivex.internal.queue.SpscArrayQueue;

public class RxClientStreamObserverAndPublisher<T>
        extends AbstractClientStreamObserverAndPublisher<T>
        implements QueueSubscription<T> {

    public RxClientStreamObserverAndPublisher(Consumer<CallStreamObserver<?>> onSubscribe) {
        super(new SimpleQueueAdapter<T>(new SpscArrayQueue<T>(DEFAULT_CHUNK_SIZE)), onSubscribe);
    }

    public RxClientStreamObserverAndPublisher(
            Consumer<CallStreamObserver<?>> onSubscribe,
            Runnable onTerminate) {
        super(new SimpleQueueAdapter<T>(new SpscArrayQueue<T>(DEFAULT_CHUNK_SIZE)), onSubscribe, onTerminate);
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
