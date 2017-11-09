/*
 *  Copyright (c) 2017, salesforce.com, inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.reactorgrpc.stub;

import com.google.common.util.concurrent.Runnables;
import com.salesforce.grpc.contrib.LambdaStreamObserver;
import com.salesforce.reactivegrpccommon.CancellableStreamObserver;
import io.grpc.stub.StreamObserver;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Operators;

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
     * Implements a unary -> unary call using {@link Mono} -> {@link Mono}.
     */
    public static <TRequest, TResponse> Mono<TResponse> oneToOne(
            Mono<TRequest> rxRequest,
            BiConsumer<TRequest, StreamObserver<TResponse>> delegate) {
        try {
            return Mono
                    .<TResponse>create(emitter -> rxRequest.subscribe(
                        request -> delegate.accept(request, new LambdaStreamObserver<TResponse>(
                            emitter::success,
                            emitter::error,
                            Runnables.doNothing()
                        )),
                        emitter::error
                    ))
                    .transform(Operators.lift(new SubscribeOnlyOnceLifter<TResponse>()));
        } catch (Throwable throwable) {
            return Mono.error(throwable);
        }
    }

    /**
     * Implements a unary -> stream call as {@link Mono} -> {@link Flux}, where the server responds with a
     * stream of messages.
     */
    public static <TRequest, TResponse> Flux<TResponse> oneToMany(
            Mono<TRequest> rxRequest,
            BiConsumer<TRequest, StreamObserver<TResponse>> delegate) {
        try {
            ReactorConsumerStreamObserver<TRequest, TResponse> consumerStreamObserver = new ReactorConsumerStreamObserver<>();
            rxRequest.subscribe(request -> delegate.accept(request, consumerStreamObserver));
            return ((Flux<TResponse>) consumerStreamObserver.getRxConsumer())
                    .transform(Operators.lift(new SubscribeOnlyOnceLifter<TResponse>()));
        } catch (Throwable throwable) {
            return Flux.error(throwable);
        }
    }

    /**
     * Implements a stream -> unary call as {@link Flux} -> {@link Mono}, where the client transits a stream of
     * messages.
     */
    public static <TRequest, TResponse> Mono<TResponse> manyToOne(
            Flux<TRequest> rxRequest,
            Function<StreamObserver<TResponse>, StreamObserver<TRequest>> delegate) {
        try {
            return Mono
                    .<TResponse>create(emitter -> {
                        ReactorProducerStreamObserver<TRequest, TResponse> reactorProducerStreamObserver = new ReactorProducerStreamObserver<>(
                                rxRequest,
                                emitter::success,
                                emitter::error,
                                Runnables.doNothing());
                        delegate.apply(
                                new CancellableStreamObserver<>(reactorProducerStreamObserver,
                                reactorProducerStreamObserver::cancel));
                        reactorProducerStreamObserver.rxSubscribe();
                    })
                    .transform(Operators.lift(new SubscribeOnlyOnceLifter<TResponse>()));
        } catch (Throwable throwable) {
            return Mono.error(throwable);
        }
    }

    /**
     * Implements a bidirectional stream -> stream call as {@link Flux} -> {@link Flux}, where both the client
     * and the server independently stream to each other.
     */
    public static <TRequest, TResponse> Flux<TResponse> manyToMany(
            Flux<TRequest> rxRequest,
            Function<StreamObserver<TResponse>, StreamObserver<TRequest>> delegate) {
        try {
            ReactorProducerConsumerStreamObserver<TRequest, TResponse> consumerStreamObserver = new ReactorProducerConsumerStreamObserver<>(rxRequest);
            delegate.apply(new CancellableStreamObserver<>(consumerStreamObserver, consumerStreamObserver::cancel));
            consumerStreamObserver.rxSubscribe();
            return ((Flux<TResponse>) consumerStreamObserver.getRxConsumer())
                    .transform(Operators.lift(new SubscribeOnlyOnceLifter<TResponse>()));
        } catch (Throwable throwable) {
            return Flux.error(throwable);
        }
    }
}
