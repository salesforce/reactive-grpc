/*
 *  Copyright (c) 2017, salesforce.com, inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.reactivegrpc.common;

import io.grpc.Status;
import io.grpc.stub.ServerCallStreamObserver;
import org.reactivestreams.Subscription;

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
    protected Subscription createSubscription() {
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

    public void abortPendingCancel() {
        abandonDelayedCancel = true;
    }
}
