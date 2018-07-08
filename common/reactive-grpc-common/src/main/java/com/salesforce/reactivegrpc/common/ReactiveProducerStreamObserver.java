/*
 *  Copyright (c) 2017, salesforce.com, inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.reactivegrpc.common;

import com.google.common.base.Preconditions;
import io.grpc.stub.ClientCallStreamObserver;
import io.grpc.stub.ClientResponseObserver;
import io.grpc.stub.StreamObserver;
import org.reactivestreams.Publisher;

/**
 * LambdaStreamObserver configures client-side manual flow control for the producing end of a message stream.
 *
 * @param <TRequest>
 * @param <TResponse>
 */
public class ReactiveProducerStreamObserver<TRequest, TResponse> implements StreamObserver<TResponse>, ClientResponseObserver<TRequest, TResponse> {
    private Publisher<TRequest> rxProducer;
    private final Consumer<TResponse> onNext;
    private final Consumer<Throwable> onError;
    private final Runnable onCompleted;
    private ReactivePublisherBackpressureOnReadyHandlerClient<TRequest> onReadyHandler;

    public ReactiveProducerStreamObserver(Publisher<TRequest> rxProducer, Consumer<TResponse> onNext, Consumer<Throwable> onError, Runnable onCompleted) {
        this.onNext = Preconditions.checkNotNull(onNext);
        this.onError = Preconditions.checkNotNull(onError);
        this.onCompleted = Preconditions.checkNotNull(onCompleted);
        this.rxProducer = Preconditions.checkNotNull(rxProducer);
    }

    @Override
    public void beforeStart(ClientCallStreamObserver<TRequest> producerStream) {
        Preconditions.checkNotNull(producerStream);
        // Subscribe to the rxProducer with an adapter to a gRPC StreamObserver that respects backpressure
        // signals from the underlying gRPC client transport.
        onReadyHandler = new ReactivePublisherBackpressureOnReadyHandlerClient<TRequest>(producerStream);
    }

    public void rxSubscribe() {
        rxProducer.subscribe(onReadyHandler);
    }

    public void cancel() {
        onReadyHandler.cancel();
    }

    @Override
    public void onNext(TResponse tResponse) {
        onNext.accept(tResponse);
    }

    @Override
    public void onError(Throwable throwable) {
        onError.accept(throwable);
        // Free references for GC
        rxProducer = null;
        onReadyHandler = null;
    }

    @Override
    public void onCompleted() {
        onCompleted.run();
        // Free references for GC
        rxProducer = null;
        onReadyHandler = null;
    }
}