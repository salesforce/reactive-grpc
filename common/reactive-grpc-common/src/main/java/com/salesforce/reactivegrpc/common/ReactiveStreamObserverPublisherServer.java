/*
 *  Copyright (c) 2017, salesforce.com, inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.reactivegrpc.common;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.ServerCallStreamObserver;
import org.reactivestreams.Subscriber;

/**
 * The gRPC server-side implementation of {@link ReactiveStreamObserverPublisherBase}.
 *
 * @param <T>
 */
public class ReactiveStreamObserverPublisherServer<T> extends ReactiveStreamObserverPublisherBase<T> {
    private ServerCallStreamObserver callStreamObserver;
    private volatile boolean abandonDelayedCancel;

    public ReactiveStreamObserverPublisherServer(ServerCallStreamObserver callStreamObserver) {
        super(callStreamObserver);
        this.callStreamObserver = callStreamObserver;
    }

    @Override
    protected ReactiveStreamObserverPublisherSubscriptionBase createSubscription() {
        return new ReactiveStreamObserverPublisherSubscriptionBase() {
            @Override
            public void cancel() {
                // Don't cancel twice if the server is already canceled
                if (callStreamObserver.isCancelled()) {
                    return;
                }

                new Thread() {
                    private final int WAIT_FOR_ERROR_DELAY_MILLS = 100;

                    @Override
                    public void run() {
                        try {
                            Thread.sleep(WAIT_FOR_ERROR_DELAY_MILLS);
                            if (!abandonDelayedCancel) {
                                ReactiveStreamObserverPublisherServer.super.cancel();
                                callStreamObserver.onError(Status.CANCELLED.withDescription("Server canceled request").asRuntimeException());

                                // Release the subscriber, we don't need a reference to it anymore
                                ReactiveStreamObserverPublisherServer.super.freeSubscriber();
                                callStreamObserver = null;
                            }
                        } catch (Throwable ex) {

                        }
                    }
                }.start();
            }
        };
    }

    // These methods are overridden to give more descriptive stack traces
    @Override
    public void subscribe(Subscriber<? super T> subscriber) {
        super.subscribe(subscriber);
    }

    @Override
    public void onNext(T value) {
        super.onNext(value);
    }

    @Override
    public void onError(Throwable throwable) {
        // This condition is not an error and is safe to ignore. If the client dies unexpectedly, the server calls cancel.
        //
        // If the cancel happens before a half-close, the ServerCallStreamObserver's cancellation handler
        // is run, and then a CANCELLED StatusRuntimeException is sent. The StatusRuntimeException can be ignored
        // because the upstream reactive stream has already been cancelled.
        if (throwable instanceof StatusRuntimeException && throwable.getMessage().contains("cancelled before receiving half close")) {
            return;
        }

        super.onError(throwable);
    }

    @Override
    public void onCompleted() {
        super.onCompleted();
    }

    public void abortPendingCancel() {
        abandonDelayedCancel = true;
    }
}
