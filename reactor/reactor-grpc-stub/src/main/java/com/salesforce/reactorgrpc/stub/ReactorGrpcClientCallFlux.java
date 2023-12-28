/*
 *  Copyright (c) 2019, Salesforce.com, Inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */
package com.salesforce.reactorgrpc.stub;

import io.grpc.stub.CallStreamObserver;
import io.grpc.stub.StreamObserver;
import org.reactivestreams.Subscription;
import reactor.core.CoreSubscriber;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxOperator;
import reactor.util.context.Context;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Create a {@link Flux} that allows for correct context propagation in client calls.
 *
 * @param <TRequest>
 * @param <TResponse>
 */
public class ReactorGrpcClientCallFlux<TRequest, TResponse> extends FluxOperator<TRequest, TResponse> {

    private final ReactorSubscriberAndClientProducer<TRequest> requestConsumer;
    private final ReactorClientStreamObserverAndPublisher<TResponse> responsePublisher;
    private final Function<StreamObserver<TResponse>, StreamObserver<TRequest>> delegate;

    ReactorGrpcClientCallFlux(Flux<TRequest> in, Function<StreamObserver<TResponse>, StreamObserver<TRequest>> delegate) {
        super(in);
        this.delegate = delegate;
        this.requestConsumer = new ReactorSubscriberAndClientProducer<>();
        this.responsePublisher = new ReactorClientStreamObserverAndPublisher<>(s -> requestConsumer.subscribe((CallStreamObserver<TRequest>) s), requestConsumer::cancel);
    }

    public ReactorGrpcClientCallFlux(Flux<TRequest> in, Function<StreamObserver<TResponse>, StreamObserver<TRequest>> delegate, int prefetch, int lowTide) {
        super(in);
        this.delegate = delegate;
        this.requestConsumer = new ReactorSubscriberAndClientProducer<>();
        this.responsePublisher = new ReactorClientStreamObserverAndPublisher<>(s -> requestConsumer.subscribe((CallStreamObserver<TRequest>) s), requestConsumer::cancel, prefetch, lowTide);
    }

    public Consumer<? super Subscription> onSubscribeHook() {
        return s -> this.delegate.apply(responsePublisher);
    }

    @Override
    public void subscribe(CoreSubscriber<? super TResponse> actual) {
        responsePublisher.subscribe(actual);
        source.subscribe(new CoreSubscriber<TRequest>() {
            @Override
            public void onSubscribe(Subscription s) {
                requestConsumer.onSubscribe(s);
            }

            @Override
            public void onNext(TRequest tRequest) {
                requestConsumer.onNext(tRequest);
            }

            @Override
            public void onError(Throwable throwable) {
                requestConsumer.onError(throwable);
            }

            @Override
            public void onComplete() {
                requestConsumer.onComplete();
            }

            @Override
            public Context currentContext() {
                return actual.currentContext();
            }
        });
    }
}
