/*
 *  Copyright (c) 2017, salesforce.com, inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.reactorgrpc.stub;

import com.google.common.base.Preconditions;
import com.salesforce.grpc.contrib.LambdaStreamObserver;
import com.salesforce.reactivegrpccommon.ReactivePublisherBackpressureOnReadyHandler;
import io.grpc.stub.ClientCallStreamObserver;
import io.grpc.stub.ClientResponseObserver;
import reactor.core.publisher.Flux;

import java.util.function.Consumer;

/**
 * LambdaStreamObserver configures client-side manual flow control for the producing end of a message stream.
 *
 * @param <TRequest>
 * @param <TResponse>
 */
public class ReactorProducerStreamObserver<TRequest, TResponse> extends LambdaStreamObserver<TResponse> implements ClientResponseObserver<TRequest, TResponse> {
    private Flux<TRequest> rxProducer;
    private ReactivePublisherBackpressureOnReadyHandler<TRequest> onReadyHandler;

    public ReactorProducerStreamObserver(Flux<TRequest> rxProducer, Consumer<TResponse> onNext, Consumer<Throwable> onError, Runnable onCompleted) {
        super(
            Preconditions.checkNotNull(onNext),
            Preconditions.checkNotNull(onError),
            Preconditions.checkNotNull(onCompleted)
        );
        this.rxProducer = Preconditions.checkNotNull(rxProducer);
    }

    @Override
    public void beforeStart(ClientCallStreamObserver<TRequest> producerStream) {
        Preconditions.checkNotNull(producerStream);
        // Subscribe to the rxProducer with an adapter to a gRPC StreamObserver that respects backpressure
        // signals from the underlying gRPC client transport.
        onReadyHandler = new ReactivePublisherBackpressureOnReadyHandler<>(producerStream);
    }

    public void rxSubscribe() {
        rxProducer.subscribe(onReadyHandler);
    }

    public void cancel() {
        onReadyHandler.cancel();
    }
}