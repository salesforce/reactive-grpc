/*
 *  Copyright (c) 2019, Salesforce.com, Inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.rx3grpc.stub;

import com.google.common.base.Preconditions;
import com.salesforce.reactivegrpc.common.Function;

import io.grpc.CallOptions;
import io.grpc.Status;
import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.functions.Consumer;

/**
 * Utility functions for processing different server call idioms. We have one-to-one correspondence
 * between utilities in this class and the potential signatures in a generated server stub class so
 * that the runtime can vary behavior without requiring regeneration of the stub.
 */
public final class ServerCalls {
    private ServerCalls() {

    }

    /**
     * Implements a unary → unary call using {@link Single} → {@link Single}.
     */
    public static <TRequest, TResponse> void oneToOne(
            final TRequest request,
            final StreamObserver<TResponse> responseObserver,
            final Function<TRequest, Single<TResponse>> delegate) {
        try {
            final Single<TResponse> rxResponse = Preconditions.checkNotNull(delegate.apply(request));
            rxResponse.subscribe(
                    new Consumer<TResponse>() {
                        @Override
                        public void accept(TResponse value) {
                            // Don't try to respond if the server has already canceled the request
                            if (responseObserver instanceof ServerCallStreamObserver && ((ServerCallStreamObserver) responseObserver).isCancelled()) {
                                return;
                            }
                            responseObserver.onNext(value);
                            responseObserver.onCompleted();
                        }
                    },
                    new Consumer<Throwable>() {
                        @Override
                        public void accept(Throwable throwable) {
                            responseObserver.onError(prepareError(throwable));
                        }
                    });
        } catch (Throwable throwable) {
            responseObserver.onError(prepareError(throwable));
        }
    }

    /**
     * Implements a unary → stream call as {@link Single} → {@link Flowable}, where the server responds with a
     * stream of messages.
     */
    public static <TRequest, TResponse> void oneToMany(
            final TRequest request,
            final StreamObserver<TResponse> responseObserver,
            final Function<TRequest, Flowable<TResponse>> delegate) {
        try {
            final Flowable<TResponse> rxResponse = Preconditions.checkNotNull(delegate.apply(request));
            final RxSubscriberAndServerProducer<TResponse> serverProducer =
                    rxResponse.subscribeWith(new RxSubscriberAndServerProducer<TResponse>());
            serverProducer.subscribe((ServerCallStreamObserver<TResponse>) responseObserver);
        } catch (Throwable throwable) {
            responseObserver.onError(prepareError(throwable));
        }
    }

    /**
     * Implements a stream → unary call as {@link Flowable} → {@link Single}, where the client transits a stream of
     * messages.
     */
    public static <TRequest, TResponse> StreamObserver<TRequest> manyToOne(
            final StreamObserver<TResponse> responseObserver,
            final Function<Flowable<TRequest>, Single<TResponse>> delegate,
            final CallOptions options) {

        final int prefetch = RxCallOptions.getPrefetch(options);
        final int lowTide = RxCallOptions.getLowTide(options);

        final RxServerStreamObserverAndPublisher<TRequest> streamObserverPublisher =
                new RxServerStreamObserverAndPublisher<TRequest>((ServerCallStreamObserver<TResponse>) responseObserver, null, prefetch, lowTide);

        try {
            final Single<TResponse> rxResponse = Preconditions.checkNotNull(delegate.apply(Flowable.fromPublisher(streamObserverPublisher)));
            rxResponse.subscribe(
                    new Consumer<TResponse>() {
                        @Override
                        public void accept(TResponse value) {
                            // Don't try to respond if the server has already canceled the request
                            if (!streamObserverPublisher.isCancelled()) {
                                responseObserver.onNext(value);
                                responseObserver.onCompleted();
                            }
                        }
                    },
                    new Consumer<Throwable>() {
                        @Override
                        public void accept(Throwable throwable) {
                            // Don't try to respond if the server has already canceled the request
                            if (!streamObserverPublisher.isCancelled()) {
                                streamObserverPublisher.abortPendingCancel();
                                responseObserver.onError(prepareError(throwable));
                            }
                        }
                    }
            );
        } catch (Throwable throwable) {
            responseObserver.onError(prepareError(throwable));
        }

        return streamObserverPublisher;
    }

    /**
     * Implements a bidirectional stream → stream call as {@link Flowable} → {@link Flowable}, where both the client
     * and the server independently stream to each other.
     */
    public static <TRequest, TResponse> StreamObserver<TRequest> manyToMany(
            final StreamObserver<TResponse> responseObserver,
            final Function<Flowable<TRequest>, Flowable<TResponse>> delegate,
            final CallOptions options) {

        final int prefetch = RxCallOptions.getPrefetch(options);
        final int lowTide = RxCallOptions.getLowTide(options);

        final RxServerStreamObserverAndPublisher<TRequest> streamObserverPublisher =
                new RxServerStreamObserverAndPublisher<TRequest>((ServerCallStreamObserver<TResponse>) responseObserver, null, prefetch, lowTide);

        try {
            final Flowable<TResponse> rxResponse = Preconditions.checkNotNull(delegate.apply(Flowable.fromPublisher(streamObserverPublisher)));
            final RxSubscriberAndServerProducer<TResponse> subscriber = new RxSubscriberAndServerProducer<TResponse>();
            subscriber.subscribe((ServerCallStreamObserver<TResponse>) responseObserver);
            // Don't try to respond if the server has already canceled the request
            rxResponse.subscribe(subscriber);
        } catch (Throwable throwable) {
            responseObserver.onError(prepareError(throwable));
        }

        return streamObserverPublisher;
    }

    private static Throwable prepareError(Throwable throwable) {
        if (throwable instanceof StatusException || throwable instanceof StatusRuntimeException) {
            return throwable;
        } else {
            return Status.fromThrowable(throwable).asException();
        }
    }
}
