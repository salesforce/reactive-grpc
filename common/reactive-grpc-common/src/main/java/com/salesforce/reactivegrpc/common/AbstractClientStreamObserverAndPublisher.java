/*
 *  Copyright (c) 2017, salesforce.com, inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.reactivegrpc.common;

import java.util.Queue;

import io.grpc.stub.CallStreamObserver;
import io.grpc.stub.ClientCallStreamObserver;
import io.grpc.stub.ClientResponseObserver;

/**
 * The gRPC client-side implementation of
 * {@link AbstractStreamObserverAndPublisher}.
 *
 * @param <T>
 */
public abstract class AbstractClientStreamObserverAndPublisher<T>
        extends AbstractStreamObserverAndPublisher<T>
        implements ClientResponseObserver<T, T> {

    public AbstractClientStreamObserverAndPublisher(
            Queue<T> queue,
            Consumer<CallStreamObserver<?>> onSubscribe) {
        super(queue, onSubscribe);
    }

    public AbstractClientStreamObserverAndPublisher(
            Queue<T> queue,
            Consumer<CallStreamObserver<?>> onSubscribe,
            Runnable onTerminate) {
        super(queue, onSubscribe, onTerminate);
    }

    public AbstractClientStreamObserverAndPublisher(
            Queue<T> queue,
            int prefetch,
            Consumer<CallStreamObserver<?>> onSubscribe) {
        super(queue, prefetch, onSubscribe);
    }

    public AbstractClientStreamObserverAndPublisher(
            Queue<T> queue,
            int prefetch,
            Consumer<CallStreamObserver<?>> onSubscribe,
            Runnable onTerminate) {
        super(queue, prefetch, onSubscribe, onTerminate);
    }

    @Override
    public void beforeStart(ClientCallStreamObserver<T> requestStream) {
        super.onSubscribe(requestStream);
    }

    @Override
    protected void doOnCancel() {
        if (subscription != null) {
            ((ClientCallStreamObserver<?>) subscription).cancel("Client canceled request", null);
        }
    }
}
