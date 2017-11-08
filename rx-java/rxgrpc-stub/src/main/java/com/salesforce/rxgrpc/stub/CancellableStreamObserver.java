/*
 *  Copyright (c) 2017, salesforce.com, inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.rxgrpc.stub;

import io.grpc.Status;
import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.ClientCallStreamObserver;
import io.grpc.stub.ClientResponseObserver;

/**
 * CancellableStreamObserver wraps a {@link io.grpc.stub.StreamObserver} and invokes an onCanceledHandler if
 * {@link io.grpc.stub.StreamObserver#onError(Throwable)} is invoked with a {@link StatusException} or
 * {@link StatusRuntimeException} of type {@link Status.Code#CANCELLED}. This class is used to hook gRPC server
 * cancellation events.
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
        if (t instanceof StatusException) {
            if (((StatusException) t).getStatus().getCode() == Status.Code.CANCELLED) {
                onCanceledHandler.run();
            }
        }
        if (t instanceof StatusRuntimeException) {
            if (((StatusRuntimeException) t).getStatus().getCode() == Status.Code.CANCELLED) {
                onCanceledHandler.run();
            }
        }
        delegate.onError(t);
    }

    @Override
    public void onCompleted() {
        delegate.onCompleted();
    }

    @Override
    public void beforeStart(ClientCallStreamObserver<TRequest> requestStream) {
        delegate.beforeStart(requestStream);
    }
}
