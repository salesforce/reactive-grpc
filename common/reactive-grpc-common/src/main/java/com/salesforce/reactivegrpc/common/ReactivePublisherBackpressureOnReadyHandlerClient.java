/*
 *  Copyright (c) 2017, salesforce.com, inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.reactivegrpc.common;

import io.grpc.Status;
import io.grpc.stub.ClientCallStreamObserver;
import org.reactivestreams.Subscription;

/**
 * The gRPC client-side implementation of {@link ReactivePublisherBackpressureOnReadyHandlerBase}.
 *
 * @param <T>
 */
public class ReactivePublisherBackpressureOnReadyHandlerClient<T> extends ReactivePublisherBackpressureOnReadyHandlerBase<T> {
    private ClientCallStreamObserver<T> requestStream;

    public ReactivePublisherBackpressureOnReadyHandlerClient(final ClientCallStreamObserver<T> requestStream) {
        super(requestStream);
        this.requestStream = requestStream;
    }

    @Override
    public void cancel() {
        super.cancel();
        requestStream.cancel("Cancelled", Status.CANCELLED.asException());
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
