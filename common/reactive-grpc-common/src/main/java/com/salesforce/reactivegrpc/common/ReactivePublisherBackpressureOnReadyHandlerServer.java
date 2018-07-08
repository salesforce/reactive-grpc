/*
 *  Copyright (c) 2017, salesforce.com, inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.reactivegrpc.common;

import io.grpc.stub.ServerCallStreamObserver;
import org.reactivestreams.Subscription;

/**
 * The gRPC server-side implementation of {@link ReactivePublisherBackpressureOnReadyHandlerBase}.
 *
 * @param <T>
 */
public class ReactivePublisherBackpressureOnReadyHandlerServer<T> extends ReactivePublisherBackpressureOnReadyHandlerBase<T> {
    public ReactivePublisherBackpressureOnReadyHandlerServer(ServerCallStreamObserver<T> requestStream) {
        super(requestStream);
        requestStream.setOnCancelHandler(new Runnable() {
            @Override
            public void run() {
                ReactivePublisherBackpressureOnReadyHandlerServer.super.cancelSubscription();
            }
        });
    }

    // These methods are overridden to give more descriptive stack traces
    @Override
    public void onSubscribe(Subscription subscription) {
        super.onSubscribe(subscription);
    }

    @Override
    public void onNext(T t) {
        super.onNext(t);
    }

    @Override
    public void onError(Throwable throwable) {
        super.onError(throwable);
    }

    @Override
    public void onComplete() {
        super.onComplete();
    }
}
