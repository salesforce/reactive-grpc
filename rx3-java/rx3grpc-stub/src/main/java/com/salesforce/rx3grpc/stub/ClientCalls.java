/*
 *  Copyright (c) 2019, Salesforce.com, Inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.rx3grpc.stub;

import org.reactivestreams.Publisher;

import com.salesforce.reactivegrpc.common.BiConsumer;
import com.salesforce.reactivegrpc.common.Function;

import io.grpc.CallOptions;
import io.grpc.stub.CallStreamObserver;
import io.grpc.stub.StreamObserver;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.core.SingleEmitter;
import io.reactivex.rxjava3.core.SingleOnSubscribe;
import io.reactivex.rxjava3.functions.Consumer;

/**
 * Utility functions for processing different client call idioms. We have one-to-one correspondence
 * between utilities in this class and the potential signatures in a generated stub client class so
 * that the runtime can vary behavior without requiring regeneration of the stub.
 */
public final class ClientCalls {
    private ClientCalls() {

    }

    /**
     * Implements a unary → unary call using {@link Single} → {@link Single}.
     */
    public static <TRequest, TResponse> Single<TResponse> oneToOne(
            final Single<TRequest> rxRequest,
            final BiConsumer<TRequest, StreamObserver<TResponse>> delegate,
            final CallOptions options) {
        try {
            return Single
                .create(new SingleOnSubscribe<TResponse>() {
                    @Override
                    public void subscribe(final SingleEmitter<TResponse> emitter) {
                        rxRequest.subscribe(
                            new Consumer<TRequest>() {
                                @Override
                                public void accept(TRequest request) {
                                    delegate.accept(request, new StreamObserver<TResponse>() {
                                        @Override
                                        public void onNext(TResponse tResponse) {
                                            emitter.onSuccess(tResponse);
                                        }

                                        @Override
                                        public void onError(Throwable throwable) {
                                            emitter.onError(throwable);
                                        }

                                        @Override
                                        public void onCompleted() {
                                            // Do nothing
                                        }
                                    });
                                }
                            },
                            new Consumer<Throwable>() {
                                @Override
                                public void accept(Throwable t) {
                                    emitter.onError(t);
                                }
                            }
                        );
                    }
                })
                .lift(new SubscribeOnlyOnceSingleOperator<TResponse>());
        } catch (Throwable throwable) {
            return Single.error(throwable);
        }
    }

    /**
     * Implements a unary → stream call as {@link Single} → {@link Flowable}, where the server responds with a
     * stream of messages.
     */
    public static <TRequest, TResponse> Flowable<TResponse> oneToMany(
            final Single<TRequest> rxRequest,
            final BiConsumer<TRequest, StreamObserver<TResponse>> delegate,
            final CallOptions options) {
        try {

            final int prefetch = RxCallOptions.getPrefetch(options);
            final int lowTide = RxCallOptions.getLowTide(options);

            return rxRequest
                    .flatMapPublisher(new io.reactivex.rxjava3.functions.Function<TRequest, Publisher<? extends TResponse>>() {
                        @Override
                        public Publisher<? extends TResponse> apply(TRequest request) {
                            final RxClientStreamObserverAndPublisher<TResponse> consumerStreamObserver =
                                new RxClientStreamObserverAndPublisher<TResponse>(null, null, prefetch, lowTide);

                            delegate.accept(request, consumerStreamObserver);

                            return consumerStreamObserver;
                        }
                    });
        } catch (Throwable throwable) {
            return Flowable.error(throwable);
        }
    }

    /**
     * Implements a stream → unary call as {@link Flowable} → {@link Single}, where the client transits a stream of
     * messages.
     */
    @SuppressWarnings("unchecked")
    public static <TRequest, TResponse> Single<TResponse> manyToOne(
            final Flowable<TRequest> flowableSource,
            final Function<StreamObserver<TResponse>, StreamObserver<TRequest>> delegate,
            final CallOptions options) {
        try {
            final RxSubscriberAndClientProducer<TRequest> subscriberAndGRPCProducer =
                    flowableSource.subscribeWith(new RxSubscriberAndClientProducer<TRequest>());
            final RxClientStreamObserverAndPublisher<TResponse> observerAndPublisher =
                new RxClientStreamObserverAndPublisher<TResponse>(
                    new com.salesforce.reactivegrpc.common.Consumer<CallStreamObserver<?>>() {
                        @Override
                        public void accept(CallStreamObserver<?> observer) {
                            subscriberAndGRPCProducer.subscribe((CallStreamObserver<TRequest>) observer);
                        }
                    },
                    new Runnable() {
                        @Override
                        public void run() {
                            subscriberAndGRPCProducer.cancel();
                        }
                    }
                );

            return Flowable.fromPublisher(observerAndPublisher)
                        .doOnSubscribe(s -> delegate.apply(observerAndPublisher))
                        .singleOrError();
        } catch (Throwable throwable) {
            return Single.error(throwable);
        }
    }

    /**
     * Implements a bidirectional stream → stream call as {@link Flowable} → {@link Flowable}, where both the client
     * and the server independently stream to each other.
     */
    @SuppressWarnings("unchecked")
    public static <TRequest, TResponse> Flowable<TResponse> manyToMany(
            final Flowable<TRequest> flowableSource,
            final Function<StreamObserver<TResponse>, StreamObserver<TRequest>> delegate,
            final CallOptions options) {

        final int prefetch = RxCallOptions.getPrefetch(options);
        final int lowTide = RxCallOptions.getLowTide(options);

        try {
            final RxSubscriberAndClientProducer<TRequest> subscriberAndGRPCProducer =
                    flowableSource.subscribeWith(new RxSubscriberAndClientProducer<TRequest>());
            final RxClientStreamObserverAndPublisher<TResponse> observerAndPublisher =
                new RxClientStreamObserverAndPublisher<TResponse>(
                    new com.salesforce.reactivegrpc.common.Consumer<CallStreamObserver<?>>() {
                        @Override
                        public void accept(CallStreamObserver<?> observer) {
                            subscriberAndGRPCProducer.subscribe((CallStreamObserver<TRequest>) observer);
                        }
                    },
                    new Runnable() {
                        @Override
                        public void run() {
                            subscriberAndGRPCProducer.cancel();
                        }
                    },
                    prefetch, lowTide);

            return Flowable.fromPublisher(observerAndPublisher).doOnSubscribe(s -> delegate.apply(observerAndPublisher));
        } catch (Throwable throwable) {
            return Flowable.error(throwable);
        }
    }
}
