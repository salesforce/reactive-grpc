/*
 *  Copyright (c) 2017, salesforce.com, inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.reactivegrpc.common;

import io.grpc.stub.ClientCallStreamObserver;
import org.reactivestreams.Subscription;

/**
 * The gRPC client-side implementation of {@link ReactiveStreamObserverPublisherBase}.
 *
 * @param <T>
 */
public class ReactiveStreamObserverPublisherClient<T> extends ReactiveStreamObserverPublisherBase<T> {
    private ClientCallStreamObserver callStreamObserver;

    public ReactiveStreamObserverPublisherClient(ClientCallStreamObserver callStreamObserver) {
        super(callStreamObserver);
        this.callStreamObserver = callStreamObserver;
    }

    @Override
    protected Subscription createSubscription() {
        return new ReactiveStreamObserverPublisherSubscriptionBase() {
            @Override
            public void cancel() {
                ReactiveStreamObserverPublisherClient.this.cancel();
                if (callStreamObserver != null) {
                    callStreamObserver.cancel("Client canceled request", null);

                    // Release the subscriber, we don't need a reference to it anymore
                    ReactiveStreamObserverPublisherClient.super.freeSubscriber();
                    callStreamObserver = null;
                }
            }
        };
    }
}
