/*
 *  Copyright (c) 2017, salesforce.com, inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.reactivegrpc.common;

import io.grpc.stub.ClientCallStreamObserver;
import io.grpc.stub.ClientResponseObserver;

/**
 * {@code CancellableStreamObserver} is used by {@code ClientCalls} and wraps a {@link io.grpc.stub.StreamObserver},
 * invoking an {@code onCanceledHandler} when a terminal event occurs ({@code onComplete()} or {@code onError()}.
 * This is needed to ensure that upstream reactive streams are cancelled when downstream reactive streams are notified
 * of a terminal event.
 *
 * @param <TRequest>
 * @param <TResponse>
 */
public class CancellableStreamObserver<TRequest, TResponse> implements ClientResponseObserver<TRequest, TResponse> {
    private final ClientResponseObserver<TRequest, TResponse> delegate;
    private final Runnable onCanceledHandler;

    public CancellableStreamObserver(ClientResponseObserver<TRequest, TResponse> delegate, Runnable onCanceledHandler) {
        this.delegate = delegate;
        this.onCanceledHandler = onCanceledHandler;
    }

    @Override
    public void onNext(TResponse value) {
        delegate.onNext(value);
    }

    @Override
    public void onError(Throwable t) {
        // Signal the upstream that it shouldn't produce any more messages
        onCanceledHandler.run();
        // Signal the downstream of the error
        delegate.onError(t);
    }

    @Override
    public void onCompleted() {
        // Signal the upstream that it shouldn't produce any more messages
        onCanceledHandler.run();
        // Signal the downstream completion
        delegate.onCompleted();
    }

    @Override
    public void beforeStart(ClientCallStreamObserver<TRequest> requestStream) {
        delegate.beforeStart(requestStream);
    }
}
