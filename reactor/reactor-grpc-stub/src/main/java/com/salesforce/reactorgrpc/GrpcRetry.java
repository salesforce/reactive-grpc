/*
 *  Copyright (c) 2017, salesforce.com, inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.reactorgrpc;

import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.function.Function;

/**
 * TODO.
 */
public final class GrpcRetry {
    private GrpcRetry() { }

    /**
     * TODO.
     */
    public static final class OneToMany {
        private OneToMany() {
        }

        public static <I, O> Function<? super Mono<I>, Flux<O>> retryWhen(final Function<Mono<I>, Flux<O>> operation, final Function<Flux<Throwable>, ? extends Publisher<?>> whenFactory) {
            return request -> Flux.defer(() -> operation.apply(request)).retryWhen(whenFactory);
        }

        public static <I, O> Function<? super Mono<I>, Flux<O>> retryAfter(final Function<Mono<I>, Flux<O>> operation, final Duration delay) {
            return retryWhen(operation, errors -> errors.delayElements(delay));
        }

        public static <I, O> Function<? super Mono<I>, Flux<O>> retryImmediately(final Function<Mono<I>, Flux<O>> operation) {
            return retryWhen(operation, errors -> errors);
        }
    }

    /**
     * TODO.
     */
    public static final class ManyToMany {
        private ManyToMany() {
        }

        public static <I, O> Function<? super Flux<I>, Flux<O>> retryWhen(final Function<Flux<I>, Flux<O>> operation, final Function<Flux<Throwable>, ? extends Publisher<?>> whenFactory) {
            return request -> Flux.defer(() -> operation.apply(request)).retryWhen(whenFactory);
        }

        public static <I, O> Function<? super Flux<I>, Flux<O>> retryAfter(final Function<Flux<I>, Flux<O>> operation, final Duration delay) {
            return retryWhen(operation, errors -> errors.delayElements(delay));
        }

        public static <I, O> Function<? super Flux<I>, Flux<O>> retryImmediately(final Function<Flux<I>, Flux<O>> operation) {
            return retryWhen(operation, errors -> errors);
        }
    }

    /**
     * TODO.
     */
    public static final class ManyToOne {
        private ManyToOne() {
        }

        public static <I, O> Function<? super Flux<I>, Mono<O>> retryWhen(final Function<Flux<I>, Mono<O>> operation, final Function<Flux<Throwable>, ? extends Publisher<?>> whenFactory) {
            return request -> Mono.defer(() -> operation.apply(request)).retryWhen(whenFactory);
        }

        public static <I, O> Function<? super Flux<I>, Mono<O>> retryAfter(final Function<Flux<I>, Mono<O>> operation, final Duration delay) {
            return retryWhen(operation, errors -> errors.delayElements(delay));
        }

        public static <I, O> Function<? super Flux<I>, Mono<O>> retryImmediately(final Function<Flux<I>, Mono<O>> operation) {
            return retryWhen(operation, errors -> errors);
        }
    }
}
