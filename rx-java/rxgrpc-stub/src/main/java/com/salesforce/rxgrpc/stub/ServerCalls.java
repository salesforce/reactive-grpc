/*
 *  Copyright (c) 2017, salesforce.com, inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.rxgrpc.stub;

import com.google.common.base.Preconditions;
import com.salesforce.reactivegrpc.common.Function;
import io.grpc.Status;
import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import io.reactivex.Flowable;
import io.reactivex.Single;
import io.reactivex.functions.Consumer;

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
            TRequest request, final StreamObserver<TResponse> responseObserver,
            Function<Single<TRequest>, Single<TResponse>> delegate) {
        try {
            Single<TRequest> rxRequest = Single.just(request);

            Single<TResponse> rxResponse = Preconditions.checkNotNull(delegate.apply(rxRequest));
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
     * Implements a unary -> stream call as {@link Single} -> {@link Flowable}, where the server responds with a
     * stream of messages.
     */
    public static <TRequest, TResponse> void oneToMany(
            TRequest request, StreamObserver<TResponse> responseObserver,
            Function<Single<TRequest>, Flowable<TResponse>> delegate) {
        try {
            Single<TRequest> rxRequest = Single.just(request);

            Flowable<TResponse> rxResponse = Preconditions.checkNotNull(delegate.apply(rxRequest));
            RxSubscriberAndServerProducer<TResponse> serverProducer =
                    rxResponse.subscribeWith(new RxSubscriberAndServerProducer<TResponse>());
            serverProducer.subscribe((ServerCallStreamObserver<TResponse>) responseObserver);
        } catch (Throwable throwable) {
            responseObserver.onError(prepareError(throwable));
        }
    }

    /**
     * Implements a stream -> unary call as {@link Flowable} -> {@link Single}, where the client transits a stream of
     * messages.
     */
    public static <TRequest, TResponse> StreamObserver<TRequest> manyToOne(
            final StreamObserver<TResponse> responseObserver,
            Function<Flowable<TRequest>, Single<TResponse>> delegate) {
        final RxServerStreamObserverAndPublisher<TRequest> streamObserverPublisher =
                new RxServerStreamObserverAndPublisher<TRequest>((ServerCallStreamObserver<TResponse>) responseObserver, null);

        try {
            Single<TResponse> rxResponse = Preconditions.checkNotNull(delegate.apply(Flowable.unsafeCreate(streamObserverPublisher)));
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
     * Implements a bidirectional stream -> stream call as {@link Flowable} -> {@link Flowable}, where both the client
     * and the server independently stream to each other.
     */
    public static <TRequest, TResponse> StreamObserver<TRequest> manyToMany(
            StreamObserver<TResponse> responseObserver,
            Function<Flowable<TRequest>, Flowable<TResponse>> delegate) {
        final RxServerStreamObserverAndPublisher<TRequest> streamObserverPublisher =
                new RxServerStreamObserverAndPublisher<TRequest>((ServerCallStreamObserver<TResponse>) responseObserver, null);

        try {
            Flowable<TResponse> rxResponse = Preconditions.checkNotNull(delegate.apply(Flowable.unsafeCreate(streamObserverPublisher)));
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
