/*
 *  Copyright (c) 2019, Salesforce.com, Inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.reactorgrpc.stub;

import java.util.function.Function;

import com.google.common.base.Preconditions;
import io.grpc.CallOptions;
import io.grpc.Status;
import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Utility functions for processing different server call idioms. We have one-to-one correspondence
 * between utilities in this class and the potential signatures in a generated server stub class so
 * that the runtime can vary behavior without requiring regeneration of the stub.
 */
public final class ServerCalls {
    private ServerCalls() {

    }

    /**
     * Implements a unary → unary call using {@link Mono} → {@link Mono}.
     */
    public static <TRequest, TResponse> void oneToOne(
            TRequest request, StreamObserver<TResponse> responseObserver,
            Function<TRequest, Mono<TResponse>> delegate,
            Function<Throwable, Throwable> prepareError) {
        try {
            Mono<TResponse> rxResponse = Preconditions.checkNotNull(delegate.apply(request));
            Disposable subscription = rxResponse.subscribe(
                value -> {
                    // Don't try to respond if the server has already canceled the request
                    if (responseObserver instanceof ServerCallStreamObserver && ((ServerCallStreamObserver) responseObserver).isCancelled()) {
                        return;
                    }
                    responseObserver.onNext(value);
                },
                throwable -> responseObserver.onError(prepareError.apply(throwable)),
                responseObserver::onCompleted
            );
            cancelSubscriptionOnCallEnd(subscription, (ServerCallStreamObserver<?>) responseObserver);
        } catch (Throwable throwable) {
            responseObserver.onError(prepareError.apply(throwable));
        }
    }

    /**
     * Implements a unary → stream call as {@link Mono} → {@link Flux}, where the server responds with a
     * stream of messages.
     */
    public static <TRequest, TResponse> void oneToMany(
            TRequest request, StreamObserver<TResponse> responseObserver,
            Function<TRequest, Flux<TResponse>> delegate,
            Function<Throwable, Throwable> prepareError) {
        try {
            Flux<TResponse> rxResponse = Preconditions.checkNotNull(delegate.apply(request));
            ReactorSubscriberAndServerProducer<TResponse> server = rxResponse.subscribeWith(new ReactorSubscriberAndServerProducer<>(prepareError::apply));
            server.subscribe((ServerCallStreamObserver<TResponse>) responseObserver);
        } catch (Throwable throwable) {
            responseObserver.onError(prepareError.apply(throwable));
        }
    }

    /**
     * Implements a stream → unary call as {@link Flux} → {@link Mono}, where the client transits a stream of
     * messages.
     */
    public static <TRequest, TResponse> StreamObserver<TRequest> manyToOne(
            StreamObserver<TResponse> responseObserver,
            Function<Flux<TRequest>, Mono<TResponse>> delegate,
            Function<Throwable, Throwable> prepareError,
            CallOptions options) {

        final int prefetch = ReactorCallOptions.getPrefetch(options);
        final int lowTide = ReactorCallOptions.getLowTide(options);

        ReactorServerStreamObserverAndPublisher<TRequest> streamObserverPublisher =
                new ReactorServerStreamObserverAndPublisher<>((ServerCallStreamObserver<TResponse>) responseObserver, null, prefetch, lowTide);

        try {
            Mono<TResponse> rxResponse = Preconditions.checkNotNull(delegate.apply(Flux.from(streamObserverPublisher)));
            Disposable subscription = rxResponse.subscribe(
                value -> {
                    // Don't try to respond if the server has already canceled the request
                    if (!streamObserverPublisher.isCancelled()) {
                        responseObserver.onNext(value);
                    }
                },
                throwable -> {
                    // Don't try to respond if the server has already canceled the request
                    if (!streamObserverPublisher.isCancelled()) {
                        streamObserverPublisher.abortPendingCancel();
                        responseObserver.onError(prepareError.apply(throwable));
                    }
                },
                responseObserver::onCompleted
            );
            cancelSubscriptionOnCallEnd(subscription, (ServerCallStreamObserver<?>) responseObserver);
        } catch (Throwable throwable) {
            responseObserver.onError(prepareError.apply(throwable));
        }

        return streamObserverPublisher;
    }

    /**
     * Implements a bidirectional stream → stream call as {@link Flux} → {@link Flux}, where both the client
     * and the server independently stream to each other.
     */
    public static <TRequest, TResponse> StreamObserver<TRequest> manyToMany(
            StreamObserver<TResponse> responseObserver,
            Function<Flux<TRequest>, Flux<TResponse>> delegate,
            Function<Throwable, Throwable> prepareError,
            CallOptions options) {

        final int prefetch = ReactorCallOptions.getPrefetch(options);
        final int lowTide = ReactorCallOptions.getLowTide(options);

        ReactorServerStreamObserverAndPublisher<TRequest> streamObserverPublisher =
                new ReactorServerStreamObserverAndPublisher<>((ServerCallStreamObserver<TResponse>) responseObserver, null, prefetch, lowTide);
        try {
            Flux<TResponse> rxResponse = Preconditions.checkNotNull(delegate.apply(Flux.from(streamObserverPublisher)));
            ReactorSubscriberAndServerProducer<TResponse> subscriber = new ReactorSubscriberAndServerProducer<>(prepareError::apply);
            subscriber.subscribe((ServerCallStreamObserver<TResponse>) responseObserver);
            // Don't try to respond if the server has already canceled the request
            rxResponse.subscribe(subscriber);
        } catch (Throwable throwable) {
            responseObserver.onError(prepareError.apply(throwable));
        }

        return streamObserverPublisher;
    }

    /**
     * Implements default error mapping.
     */
    public static Throwable prepareError(Throwable throwable) {
        if (throwable instanceof StatusException || throwable instanceof StatusRuntimeException) {
            return throwable;
        } else {
            return Status.fromThrowable(throwable).asException();
        }
    }

    private static void cancelSubscriptionOnCallEnd(Disposable subscription, ServerCallStreamObserver<?> responseObserver) {
        responseObserver.setOnCancelHandler(subscription::dispose);
        responseObserver.setOnCloseHandler(subscription::dispose);
    }
}
