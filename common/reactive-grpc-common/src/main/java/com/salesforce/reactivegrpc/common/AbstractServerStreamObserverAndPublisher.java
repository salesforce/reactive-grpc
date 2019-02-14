/*
 *  Copyright (c) 2017, salesforce.com, inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.reactivegrpc.common;

import java.util.Queue;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.CallStreamObserver;
import io.grpc.stub.ServerCallStreamObserver;

/**
 * The gRPC server-side implementation of
 * {@link AbstractStreamObserverAndPublisher}.
 *
 * @param <T>
 */
public abstract class AbstractServerStreamObserverAndPublisher<T>
        extends AbstractStreamObserverAndPublisher<T> {

    private volatile boolean abandonDelayedCancel;

    public AbstractServerStreamObserverAndPublisher(
            ServerCallStreamObserver<?> serverCallStreamObserver,
            Queue<T> queue,
            Consumer<CallStreamObserver<?>> onSubscribe) {
        super(queue, onSubscribe);
        super.onSubscribe(serverCallStreamObserver);
    }

    public AbstractServerStreamObserverAndPublisher(
            ServerCallStreamObserver<?> serverCallStreamObserver,
            Queue<T> queue,
            Consumer<CallStreamObserver<?>> onSubscribe,
            Runnable onTerminate) {
        super(queue, onSubscribe, onTerminate);
        super.onSubscribe(serverCallStreamObserver);
    }

    public AbstractServerStreamObserverAndPublisher(
            ServerCallStreamObserver<?> serverCallStreamObserver,
            Queue<T> queue,
            int prefetch,
            Consumer<CallStreamObserver<?>> onSubscribe) {
        super(queue, prefetch, onSubscribe);
        super.onSubscribe(serverCallStreamObserver);
    }

    public AbstractServerStreamObserverAndPublisher(
            ServerCallStreamObserver<?> serverCallStreamObserver,
            Queue<T> queue,
            int prefetch,
            Consumer<CallStreamObserver<?>> onSubscribe,
            Runnable onTerminate) {
        super(queue, prefetch, onSubscribe, onTerminate);
        super.onSubscribe(serverCallStreamObserver);
    }

    @Override
    public void onError(Throwable throwable) {
        // This condition is not an error and is safe to ignore. If the client dies unexpectedly, the server calls cancel.
        //
        // If the cancel happens before a half-close, the ServerCallStreamObserver's cancellation handler
        // is run, and then a CANCELLED StatusRuntimeException is sent. The StatusRuntimeException can be ignored
        // because the subscription reactive stream has already been cancelled.
        if (throwable instanceof StatusRuntimeException && throwable.getMessage().contains("cancelled before receiving half close")) {
            return;
        }

        super.onError(throwable);
    }

    @Override
    public void cancel() {
        // Don't cancel twice if the server is already canceled
        final ServerCallStreamObserver observer = (ServerCallStreamObserver) subscription;

        if (observer.isCancelled()) {
            return;
        }

        new Thread() {
            private final int WAIT_FOR_ERROR_DELAY_MILLS = 100;

            @Override
            public void run() {
                try {
                    Thread.sleep(WAIT_FOR_ERROR_DELAY_MILLS);
                    if (!abandonDelayedCancel) {
                        AbstractServerStreamObserverAndPublisher.super.cancel();
                        observer.onError(Status.CANCELLED.withDescription("Server canceled request").asRuntimeException());

                        // Release the subscriber, we don't need a reference to it anymore
//                        AbstractReactiveStreamObserverPublisherServer.super.freeSubscriber();
                    }
                } catch (Throwable ex) {

                }
            }
        }.start();
    }

    public void abortPendingCancel() {
        abandonDelayedCancel = true;
    }
}