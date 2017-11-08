/*
 *  Copyright (c) 2017, salesforce.com, inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.rxgrpc.stub;

import com.google.common.base.Preconditions;
import com.salesforce.grpc.contrib.LambdaStreamObserver;
import io.grpc.Status;
import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.CallStreamObserver;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import io.reactivex.Flowable;
import io.reactivex.Single;
import io.reactivex.schedulers.Schedulers;
import org.reactivestreams.Subscriber;

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
     * Implements a unary -> unary call using {@link Single} -> {@link Single}.
     */
    public static <TRequest, TResponse> void oneToOne(
            TRequest request, StreamObserver<TResponse> responseObserver,
            Function<Single<TRequest>, Single<TResponse>> delegate) {
        try {
            Single<TRequest> rxRequest = Single.just(request);

            Single<TResponse> rxResponse = Preconditions.checkNotNull(delegate.apply(rxRequest));
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
     * Implements a unary -> stream call as {@link Single} -> {@link Flowable}, where the server responds with a
     * stream of messages.
     */
    public static <TRequest, TResponse> void oneToMany(
            TRequest request, StreamObserver<TResponse> responseObserver,
            Function<Single<TRequest>, Flowable<TResponse>> delegate) {
        try {
            Single<TRequest> rxRequest = Single.just(request);

            Flowable<TResponse> rxResponse = Preconditions.checkNotNull(delegate.apply(rxRequest));
            rxResponse.subscribe(new RxFlowableBackpressureOnReadyHandler<>(
                    (ServerCallStreamObserver<TResponse>) responseObserver));
        } catch (Throwable throwable) {
            responseObserver.onError(prepareError(throwable));
        }
    }

    /**
     * Implements a stream -> unary call as {@link Flowable} -> {@link Single}, where the client transits a stream of
     * messages.
     */
    public static <TRequest, TResponse> StreamObserver<TRequest> manyToOne(
            StreamObserver<TResponse> responseObserver,
            Function<Flowable<TRequest>, Single<TResponse>> delegate) {
        RxStreamObserverPublisher<TRequest> streamObserverPublisher =
                new RxStreamObserverPublisher<>((CallStreamObserver<TResponse>) responseObserver);

        try {
            Single<TResponse> rxResponse = Preconditions.checkNotNull(delegate.apply(
                    Flowable.unsafeCreate(streamObserverPublisher)
                            .observeOn(Schedulers.from(RxExecutor.getSerializingExecutor()))));
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
     * Implements a bidirectional stream -> stream call as {@link Flowable} -> {@link Flowable}, where both the client
     * and the server independently stream to each other.
     */
    public static <TRequest, TResponse> StreamObserver<TRequest> manyToMany(
            StreamObserver<TResponse> responseObserver,
            Function<Flowable<TRequest>, Flowable<TResponse>> delegate) {
        RxStreamObserverPublisher<TRequest> streamObserverPublisher =
                new RxStreamObserverPublisher<>((CallStreamObserver<TResponse>) responseObserver);

        try {
            Flowable<TResponse> rxResponse = Preconditions.checkNotNull(delegate.apply(
                    Flowable.unsafeCreate(streamObserverPublisher)
                            .observeOn(Schedulers.from(RxExecutor.getSerializingExecutor()))));
            Subscriber<TResponse> subscriber = new RxFlowableBackpressureOnReadyHandler<>(
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
