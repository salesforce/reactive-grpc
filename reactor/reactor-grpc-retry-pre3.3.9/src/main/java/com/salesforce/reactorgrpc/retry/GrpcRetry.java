/*
 *  Copyright (c) 2019, Salesforce.com, Inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.reactorgrpc.retry;

import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.function.Function;

/**
 * {@code GrpcRetry} is used to transparently re-establish a streaming gRPC request in the event of a server error.
 * <p>
 * During a retry, the upstream rx pipeline is re-subscribed to acquire a request message and the RPC call re-issued.
 * The downstream rx pipeline never sees the error.
 */
public final class GrpcRetry {
    private GrpcRetry() { }

    /**
     * {@link GrpcRetry} functions for streaming response gRPC operations.
     */
    public static final class OneToMany {
        private OneToMany() {
        }

        /**
         * Retries a streaming gRPC call, using the same semantics as {@link Flux#retryWhen(Function)}.
         *
         * For easier use, use the Retry builder from
         * <a href="https://github.com/reactor/reactor-addons/blob/master/reactor-extra/src/main/java/reactor/retry/Retry.java">Reactor Extras</a>.
         *
         * @param operation the gRPC operation to retry, typically from a generated reactive-grpc stub class
         * @param whenFactory receives a Publisher of notifications with which a user can complete or error, aborting the retry
         * @param <I> I
         * @param <O> O
         *
         * @see Flux#retryWhen(Function)
         */
        public static <I, O> Function<? super Mono<I>, Flux<O>> retryWhen(final Function<Mono<I>, Flux<O>> operation, final Function<Flux<Throwable>, ? extends Publisher<?>> whenFactory) {
            return request -> Flux.defer(() -> operation.apply(request)).retryWhen(whenFactory);
        }

        /**
         * Retries a streaming gRPC call with a fixed delay between retries.
         *
         * @param operation the gRPC operation to retry, typically from a generated reactive-grpc stub class
         * @param delay the delay between retries
         * @param <I> I
         * @param <O> O
         */
        public static <I, O> Function<? super Mono<I>, Flux<O>> retryAfter(final Function<Mono<I>, Flux<O>> operation, final Duration delay) {
            return retryWhen(operation, errors -> errors.delayElements(delay));
        }

        /**
         * Retries a streaming gRPC call immediately.
         *
         * @param operation the gRPC operation to retry, typically from a generated reactive-grpc stub class
         * @param <I> I
         * @param <O> O
         */
        public static <I, O> Function<? super Mono<I>, Flux<O>> retryImmediately(final Function<Mono<I>, Flux<O>> operation) {
            return retryWhen(operation, errors -> errors);
        }
    }

    /**
     * {@link GrpcRetry} functions for bi-directional streaming gRPC operations.
     */
    public static final class ManyToMany {
        private ManyToMany() {
        }

        /**
         * Retries a streaming gRPC call, using the same semantics as {@link Flux#retryWhen(Function)}.
         *
         * For easier use, use the Retry builder from
         * <a href="https://github.com/reactor/reactor-addons/blob/master/reactor-extra/src/main/java/reactor/retry/Retry.java">Reactor Extras</a>.
         *
         * @param operation the gRPC operation to retry, typically from a generated reactive-grpc stub class
         * @param whenFactory receives a Publisher of notifications with which a user can complete or error, aborting the retry
         * @param <I> I
         * @param <O> O
         *
         * @see Flux#retryWhen(Function)
         */
        public static <I, O> Function<? super Flux<I>, ? extends Publisher<O>> retryWhen(final Function<Flux<I>, Flux<O>> operation, final Function<Flux<Throwable>, ? extends Publisher<?>> whenFactory) {
            return request -> Flux.defer(() -> operation.apply(request)).retryWhen(whenFactory);
        }

        /**
         * Retries a streaming gRPC call with a fixed delay between retries.
         *
         * @param operation the gRPC operation to retry, typically from a generated reactive-grpc stub class
         * @param delay the delay between retries
         * @param <I> I
         * @param <O> O
         */
        public static <I, O> Function<? super Flux<I>, ? extends Publisher<O>> retryAfter(final Function<Flux<I>, Flux<O>> operation, final Duration delay) {
            return retryWhen(operation, errors -> errors.delayElements(delay));
        }

        /**
         * Retries a streaming gRPC call immediately.
         *
         * @param operation the gRPC operation to retry, typically from a generated reactive-grpc stub class
         * @param <I> I
         * @param <O> O
         */
        public static <I, O> Function<? super Flux<I>, ? extends Publisher<O>> retryImmediately(final Function<Flux<I>, Flux<O>> operation) {
            return retryWhen(operation, errors -> errors);
        }
    }

    /**
     * {@link GrpcRetry} functions for streaming request gRPC operations.
     */
    public static final class ManyToOne {
        private ManyToOne() {
        }

        /**
         * Retries a streaming gRPC call, using the same semantics as {@link Flux#retryWhen(Function)}.
         *
         * For easier use, use the Retry builder from
         * <a href="https://github.com/reactor/reactor-addons/blob/master/reactor-extra/src/main/java/reactor/retry/Retry.java">Reactor Extras</a>.
         *
         * @param operation the gRPC operation to retry, typically from a generated reactive-grpc stub class
         * @param whenFactory receives a Publisher of notifications with which a user can complete or error, aborting the retry
         * @param <I> I
         * @param <O> O
         *
         * @see Flux#retryWhen(Function)
         */
        public static <I, O> Function<? super Flux<I>, Mono<O>> retryWhen(final Function<Flux<I>, Mono<O>> operation, final Function<Flux<Throwable>, ? extends Publisher<?>> whenFactory) {
            return request -> Mono.defer(() -> operation.apply(request)).retryWhen(whenFactory);
        }

        /**
         * Retries a streaming gRPC call with a fixed delay between retries.
         *
         * @param operation the gRPC operation to retry, typically from a generated reactive-grpc stub class
         * @param delay the delay between retries
         * @param <I> I
         * @param <O> O
         */
        public static <I, O> Function<? super Flux<I>, Mono<O>> retryAfter(final Function<Flux<I>, Mono<O>> operation, final Duration delay) {
            return retryWhen(operation, errors -> errors.delayElements(delay));
        }

        /**
         * Retries a streaming gRPC call immediately.
         *
         * @param operation the gRPC operation to retry, typically from a generated reactive-grpc stub class
         * @param <I> I
         * @param <O> O
         */
        public static <I, O> Function<? super Flux<I>, Mono<O>> retryImmediately(final Function<Flux<I>, Mono<O>> operation) {
            return retryWhen(operation, errors -> errors);
        }
    }
}