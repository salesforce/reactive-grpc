/*
 *  Copyright (c) 2019, Salesforce.com, Inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.rxgrpc.stub;

import com.salesforce.reactivegrpc.common.AbstractServerStreamObserverAndPublisher;
import com.salesforce.reactivegrpc.common.Consumer;
import io.grpc.stub.CallStreamObserver;
import io.grpc.stub.ServerCallStreamObserver;
import io.reactivex.internal.fuseable.QueueFuseable;
import io.reactivex.internal.fuseable.QueueSubscription;
import io.reactivex.internal.queue.SpscArrayQueue;

/**
 * TODO: Explain what this class does.
 * @param <T> T
 */
class RxServerStreamObserverAndPublisher<T>
        extends AbstractServerStreamObserverAndPublisher<T>
        implements QueueSubscription<T> {

    RxServerStreamObserverAndPublisher(
            ServerCallStreamObserver<?> serverCallStreamObserver,
            Consumer<CallStreamObserver<?>> onSubscribe,
            int prefetch,
            int lowTide) {
        super(serverCallStreamObserver, new SimpleQueueAdapter<T>(new SpscArrayQueue<T>(prefetch)), onSubscribe, prefetch, lowTide);
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