/*
 *  Copyright (c) 2017, salesforce.com, inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.rxgrpc.stub;

import com.google.common.util.concurrent.Runnables;
import com.salesforce.grpc.contrib.LambdaStreamObserver;
import com.salesforce.reactivegrpccommon.CancellableStreamObserver;
import io.grpc.stub.StreamObserver;
import io.reactivex.Flowable;
import io.reactivex.Single;

import java.util.function.BiConsumer;
import java.util.function.Function;

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
            Single<TRequest> rxRequest,
            BiConsumer<TRequest, StreamObserver<TResponse>> delegate) {
        try {
            return Single
                    .<TResponse>create(emitter -> rxRequest.subscribe(
                        request -> delegate.accept(request, new LambdaStreamObserver<TResponse>(
                            emitter::onSuccess,
                            emitter::onError,
                            Runnables.doNothing()
                        )),
                        emitter::onError
                    ))
                    .lift(new SubscribeOnlyOnceSingleOperator<>());
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
            BiConsumer<TRequest, StreamObserver<TResponse>> delegate) {
        try {
            RxConsumerStreamObserver<TRequest, TResponse> consumerStreamObserver = new RxConsumerStreamObserver<>();
            rxRequest.subscribe(request -> delegate.accept(request, consumerStreamObserver));
            return ((Flowable<TResponse>) consumerStreamObserver.getRxConsumer())
                    .lift(new SubscribeOnlyOnceFlowableOperator<>());
        } catch (Throwable throwable) {
            return Flowable.error(throwable);
        }
    }

    /**
     * Implements a stream -> unary call as {@link Flowable} -> {@link Single}, where the client transits a stream of
     * messages.
     */
    public static <TRequest, TResponse> Single<TResponse> manyToOne(
            Flowable<TRequest> rxRequest,
            Function<StreamObserver<TResponse>, StreamObserver<TRequest>> delegate) {
        try {
            return Single
                    .<TResponse>create(emitter -> {
                        RxProducerStreamObserver<TRequest, TResponse> rxProducerStreamObserver = new RxProducerStreamObserver<>(
                                rxRequest,
                                emitter::onSuccess,
                                emitter::onError,
                                Runnables.doNothing());
                        delegate.apply(
                                new CancellableStreamObserver<>(rxProducerStreamObserver,
                                rxProducerStreamObserver::cancel));
                        rxProducerStreamObserver.rxSubscribe();
                    }).lift(new SubscribeOnlyOnceSingleOperator<>());
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
            RxProducerConsumerStreamObserver<TRequest, TResponse> consumerStreamObserver = new RxProducerConsumerStreamObserver<>(rxRequest);
            delegate.apply(new CancellableStreamObserver<>(consumerStreamObserver, consumerStreamObserver::cancel));
            consumerStreamObserver.rxSubscribe();
            return ((Flowable<TResponse>) consumerStreamObserver.getRxConsumer())
                    .lift(new SubscribeOnlyOnceFlowableOperator<>());
        } catch (Throwable throwable) {
            return Flowable.error(throwable);
        }
    }
}
