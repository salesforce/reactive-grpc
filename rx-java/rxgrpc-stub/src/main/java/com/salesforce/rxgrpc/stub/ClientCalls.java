/*
 *  Copyright (c) 2019, Salesforce.com, Inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.rxgrpc.stub;

import com.salesforce.reactivegrpc.common.BiConsumer;
import com.salesforce.reactivegrpc.common.Function;

import io.grpc.CallOptions;
import io.grpc.stub.CallStreamObserver;
import io.grpc.stub.StreamObserver;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.core.SingleOnSubscribe;

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
                .create((SingleOnSubscribe<TResponse>) emitter -> rxRequest.subscribe(
                        request -> delegate.accept(request, new StreamObserver<>() {
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
                        }),
                        emitter::onError
                ))
                .lift(new SubscribeOnlyOnceSingleOperator<>());
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
                    .flatMapPublisher(request -> {
                        final RxClientStreamObserverAndPublisher<TResponse> consumerStreamObserver =
                                new RxClientStreamObserverAndPublisher<>(null, null, prefetch, lowTide);

                        delegate.accept(request, consumerStreamObserver);

                        return consumerStreamObserver;
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
                    flowableSource.subscribeWith(new RxSubscriberAndClientProducer<>());
            final RxClientStreamObserverAndPublisher<TResponse> observerAndPublisher =
                new RxClientStreamObserverAndPublisher<TResponse>(
                        observer -> subscriberAndGRPCProducer.subscribe((CallStreamObserver<TRequest>) observer),
                        subscriberAndGRPCProducer::cancel
                );
            delegate.apply(observerAndPublisher);

            return Flowable.fromPublisher(observerAndPublisher)
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
                    flowableSource.subscribeWith(new RxSubscriberAndClientProducer<>());
            final RxClientStreamObserverAndPublisher<TResponse> observerAndPublisher =
                new RxClientStreamObserverAndPublisher<TResponse>(
                        observer -> subscriberAndGRPCProducer.subscribe((CallStreamObserver<TRequest>) observer),
                        subscriberAndGRPCProducer::cancel,
                    prefetch, lowTide);
            delegate.apply(observerAndPublisher);

            return Flowable.fromPublisher(observerAndPublisher);
        } catch (Throwable throwable) {
            return Flowable.error(throwable);
        }
    }

}
