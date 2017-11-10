/*
 *  Copyright (c) 2017, salesforce.com, inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.reactorgrpc.stub;

import com.google.common.base.Preconditions;
import com.salesforce.grpc.contrib.LambdaStreamObserver;
import com.salesforce.reactivegrpccommon.ReactiveExecutor;
import com.salesforce.reactivegrpccommon.ReactivePublisherBackpressureOnReadyHandler;
import com.salesforce.reactivegrpccommon.ReactiveStreamObserverPublisher;
import io.grpc.Status;
import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.CallStreamObserver;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import reactor.core.scheduler.Schedulers;
import org.reactivestreams.Subscriber;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.function.Function;

/**
 * Utility functions for processing different server call idioms. We have one-to-one correspondence
 * between utilities in this class and the potential signatures in a generated server stub class so
 * that the runtime can vary behavior without requiring regeneration of the stub.
 */
public final class ServerCalls {
    private ServerCalls() {

    }

    /**
     * Implements a unary -> unary call using {@link Mono} -> {@link Mono}.
     */
    public static <TRequest, TResponse> void oneToOne(
            TRequest request, StreamObserver<TResponse> responseObserver,
            Function<Mono<TRequest>, Mono<TResponse>> delegate) {
        try {
            Mono<TRequest> rxRequest = Mono.just(request);

            Mono<TResponse> rxResponse = Preconditions.checkNotNull(delegate.apply(rxRequest));
            rxResponse.subscribe(
                value -> {
                    // Don't try to respond if the server has already canceled the request
                    if (responseObserver instanceof ServerCallStreamObserver && ((ServerCallStreamObserver) responseObserver).isCancelled()) {
                        return;
                    }
                    responseObserver.onNext(value);
                    responseObserver.onCompleted();
                },
                throwable -> responseObserver.onError(prepareError(throwable)));
        } catch (Throwable throwable) {
            responseObserver.onError(prepareError(throwable));
        }
    }

    /**
     * Implements a unary -> stream call as {@link Mono} -> {@link Flux}, where the server responds with a
     * stream of messages.
     */
    public static <TRequest, TResponse> void oneToMany(
            TRequest request, StreamObserver<TResponse> responseObserver,
            Function<Mono<TRequest>, Flux<TResponse>> delegate) {
        try {
            Mono<TRequest> rxRequest = Mono.just(request);

            Flux<TResponse> rxResponse = Preconditions.checkNotNull(delegate.apply(rxRequest));
            rxResponse.subscribe(new ReactivePublisherBackpressureOnReadyHandler<>(
                    (ServerCallStreamObserver<TResponse>) responseObserver));
        } catch (Throwable throwable) {
            responseObserver.onError(prepareError(throwable));
        }
    }

    /**
     * Implements a stream -> unary call as {@link Flux} -> {@link Mono}, where the client transits a stream of
     * messages.
     */
    public static <TRequest, TResponse> StreamObserver<TRequest> manyToOne(
            StreamObserver<TResponse> responseObserver,
            Function<Flux<TRequest>, Mono<TResponse>> delegate) {
        ReactiveStreamObserverPublisher<TRequest> streamObserverPublisher =
                new ReactiveStreamObserverPublisher<>((CallStreamObserver<TResponse>) responseObserver);

        try {
            Mono<TResponse> rxResponse = Preconditions.checkNotNull(delegate.apply(
                    Flux.from(streamObserverPublisher)
                            .publishOn(Schedulers.fromExecutor(ReactiveExecutor.getSerializingExecutor()))));
            rxResponse.subscribe(
                value -> {
                    // Don't try to respond if the server has already canceled the request
                    if (!streamObserverPublisher.isCanceled()) {
                        responseObserver.onNext(value);
                        responseObserver.onCompleted();
                    }
                },
                throwable -> {
                    // Don't try to respond if the server has already canceled the request
                    if (!streamObserverPublisher.isCanceled()) {
                        responseObserver.onError(prepareError(throwable));
                    }
                }
            );
        } catch (Throwable throwable) {
            responseObserver.onError(prepareError(throwable));
        }

        return new LambdaStreamObserver<>(
                streamObserverPublisher::onNext,
                streamObserverPublisher::onError,
                streamObserverPublisher::onCompleted);
    }

    /**
     * Implements a bidirectional stream -> stream call as {@link Flux} -> {@link Flux}, where both the client
     * and the server independently stream to each other.
     */
    public static <TRequest, TResponse> StreamObserver<TRequest> manyToMany(
            StreamObserver<TResponse> responseObserver,
            Function<Flux<TRequest>, Flux<TResponse>> delegate) {
        ReactiveStreamObserverPublisher<TRequest> streamObserverPublisher =
                new ReactiveStreamObserverPublisher<>((CallStreamObserver<TResponse>) responseObserver);

        try {
            Flux<TResponse> rxResponse = Preconditions.checkNotNull(delegate.apply(
                    Flux.from(streamObserverPublisher)
                            .publishOn(Schedulers.fromExecutor(ReactiveExecutor.getSerializingExecutor()))));
            Subscriber<TResponse> subscriber = new ReactivePublisherBackpressureOnReadyHandler<>(
                    (ServerCallStreamObserver<TResponse>) responseObserver);
            // Don't try to respond if the server has already canceled the request
            rxResponse.subscribe(
                tResponse -> {
                    if (!streamObserverPublisher.isCanceled()) {
                        subscriber.onNext(tResponse);
                    }
                },
                throwable -> {
                    if (!streamObserverPublisher.isCanceled()) {
                        subscriber.onError(throwable);
                    }
                },
                () -> {
                    if (!streamObserverPublisher.isCanceled()) {
                        subscriber.onComplete();
                    }
                },
                subscriber::onSubscribe
            );
        } catch (Throwable throwable) {
            responseObserver.onError(prepareError(throwable));
        }

        return new LambdaStreamObserver<>(
                streamObserverPublisher::onNext,
                streamObserverPublisher::onError,
                streamObserverPublisher::onCompleted);
    }

    private static Throwable prepareError(Throwable throwable) {
        if (throwable instanceof StatusException || throwable instanceof StatusRuntimeException) {
            return throwable;
        } else {
            return Status.fromThrowable(throwable).asException();
        }
    }
}
