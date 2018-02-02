/*
 *  Copyright (c) 2017, salesforce.com, inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.rxgrpc.stub;

import com.google.common.util.concurrent.Runnables;
import com.salesforce.reactivegrpc.common.CancellableStreamObserver;
import com.salesforce.reactivegrpc.common.ReactiveProducerStreamObserver;
import io.grpc.stub.StreamObserver;
import io.reactivex.Flowable;
import io.reactivex.Single;

import com.salesforce.reactivegrpc.common.BiConsumer;
import com.salesforce.reactivegrpc.common.Function;
import io.reactivex.SingleEmitter;
import io.reactivex.SingleOnSubscribe;
import io.reactivex.functions.Consumer;

/**
 * Utility functions for processing different client call idioms. We have one-to-one correspondence
 * between utilities in this class and the potential signatures in a generated stub client class so
 * that the runtime can vary behavior without requiring regeneration of the stub.
 */
public final class ClientCalls {
    private ClientCalls() {

    }

    /**
     * Implements a unary -> unary call using {@link Single} -> {@link Single}.
     */
    public static <TRequest, TResponse> Single<TResponse> oneToOne(
            final Single<TRequest> rxRequest,
            final BiConsumer<TRequest, StreamObserver<TResponse>> delegate) {
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
     * Implements a unary -> stream call as {@link Single} -> {@link Flowable}, where the server responds with a
     * stream of messages.
     */
    public static <TRequest, TResponse> Flowable<TResponse> oneToMany(
            Single<TRequest> rxRequest,
            final BiConsumer<TRequest, StreamObserver<TResponse>> delegate) {
        try {
            final RxConsumerStreamObserver<TRequest, TResponse> consumerStreamObserver = new RxConsumerStreamObserver<TRequest, TResponse>();
            rxRequest.subscribe(new Consumer<TRequest>() {
                @Override
                public void accept(TRequest request) {
                    delegate.accept(request, consumerStreamObserver);
                }
            });
            return ((Flowable<TResponse>) consumerStreamObserver.getRxConsumer())
                    .lift(new SubscribeOnlyOnceFlowableOperator<TResponse>());
        } catch (Throwable throwable) {
            return Flowable.error(throwable);
        }
    }

    /**
     * Implements a stream -> unary call as {@link Flowable} -> {@link Single}, where the client transits a stream of
     * messages.
     */
    public static <TRequest, TResponse> Single<TResponse> manyToOne(
            final Flowable<TRequest> rxRequest,
            final Function<StreamObserver<TResponse>, StreamObserver<TRequest>> delegate) {
        try {
            return Single
                .create(new SingleOnSubscribe<TResponse>() {
                    @Override
                    public void subscribe(final SingleEmitter<TResponse> emitter) {
                        final ReactiveProducerStreamObserver<TRequest, TResponse> rxProducerStreamObserver = new ReactiveProducerStreamObserver<TRequest, TResponse>(
                            rxRequest,
                            new com.salesforce.reactivegrpc.common.Consumer<TResponse>() {
                                @Override
                                public void accept(TResponse t) {
                                    emitter.onSuccess(t);
                                }
                            },
                            new com.salesforce.reactivegrpc.common.Consumer<Throwable>() {
                                @Override
                                public void accept(Throwable t) {
                                    emitter.onError(t);
                                }
                            },
                            Runnables.doNothing());
                        delegate.apply(
                            new CancellableStreamObserver<TRequest, TResponse>(rxProducerStreamObserver,
                                new Runnable() {
                                    @Override
                                    public void run() {
                                        rxProducerStreamObserver.cancel();
                                    }
                                }));
                        rxProducerStreamObserver.rxSubscribe();
                    }
                }).lift(new SubscribeOnlyOnceSingleOperator<TResponse>());
        } catch (Throwable throwable) {
            return Single.error(throwable);
        }
    }

    /**
     * Implements a bidirectional stream -> stream call as {@link Flowable} -> {@link Flowable}, where both the client
     * and the server independently stream to each other.
     */
    public static <TRequest, TResponse> Flowable<TResponse> manyToMany(
            Flowable<TRequest> rxRequest,
            Function<StreamObserver<TResponse>, StreamObserver<TRequest>> delegate) {
        try {
            final RxProducerConsumerStreamObserver<TRequest, TResponse> consumerStreamObserver = new RxProducerConsumerStreamObserver<TRequest, TResponse>(rxRequest);
            delegate.apply(new CancellableStreamObserver<TRequest, TResponse>(consumerStreamObserver, new Runnable() {
                @Override
                public void run() {
                    consumerStreamObserver.cancel();
                }
            }));
            consumerStreamObserver.rxSubscribe();
            return ((Flowable<TResponse>) consumerStreamObserver.getRxConsumer())
                    .lift(new SubscribeOnlyOnceFlowableOperator<TResponse>());
        } catch (Throwable throwable) {
            return Flowable.error(throwable);
        }
    }
}
