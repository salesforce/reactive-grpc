/*
 *  Copyright (c) 2019, Salesforce.com, Inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.rxgrpc;

import io.reactivex.*;
import io.reactivex.functions.Function;
import org.reactivestreams.Publisher;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

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
        private OneToMany() { }

        /**
         * Retries a streaming gRPC call, using the same semantics as {@link Flowable#retryWhen(Function)}.
         *
         * For easier use, use the RetryWhen builder from
         * <a href="https://davidmoten.github.io/rxjava2-extras/apidocs/com/github/davidmoten/rx2/RetryWhen.html">RxJava2 Extras</a>.
         *
         * @param operation the gRPC operation to retry, typically from a generated reactive-grpc stub class
         * @param handler receives a Publisher of notifications with which a user can complete or error, aborting the retry
         * @param <I> I
         * @param <O> O
         *
         * @see Flowable#retryWhen(Function)
         */
        public static <I, O> SingleConverter<I, Flowable<O>> retryWhen(final Function<Single<I>, Flowable<O>> operation, final Function<? super Flowable<Throwable>, ? extends Publisher<?>> handler) {
            return new SingleConverter<I, Flowable<O>>() {
                @Override
                public Flowable<O> apply(final Single<I> request) {
                    return Flowable.defer(new Callable<Publisher<O>>() {
                        @Override
                        public Publisher<O> call() throws Exception {
                            return operation.apply(request);
                        }
                    }).retryWhen(handler);
                }
            };
        }

        /**
         * Retries a streaming gRPC call with a fixed delay between retries.
         *
         * @param operation the gRPC operation to retry, typically from a generated reactive-grpc stub class
         * @param delay the delay between retries
         * @param unit the units to use for {@code delay}
         * @param <I> I
         * @param <O> O
         */
        public static <I, O> SingleConverter<I, Flowable<O>> retryAfter(final Function<Single<I>, Flowable<O>> operation, final int delay, final TimeUnit unit) {
            return retryWhen(operation, new Function<Flowable<Throwable>, Publisher<?>>() {
                @Override
                public Publisher<?> apply(Flowable<Throwable> errors) {
                    return errors.flatMap(new Function<Throwable, Publisher<?>>() {
                        @Override
                        public Publisher<?> apply(Throwable error) {
                            return Flowable.timer(delay, unit);
                        }
                    });
                }
            });
        }

        /**
         * Retries a streaming gRPC call immediately.
         *
         * @param operation the gRPC operation to retry, typically from a generated reactive-grpc stub class
         * @param <I> I
         * @param <O> O
         */
        public static <I, O> SingleConverter<I, Flowable<O>> retryImmediately(final Function<Single<I>, Flowable<O>> operation) {
            return retryWhen(operation, new Function<Flowable<Throwable>, Publisher<?>>() {
                @Override
                public Publisher<?> apply(Flowable<Throwable> errors) {
                    return errors;
                }
            });
        }
    }

    /**
     * {@link GrpcRetry} functions for bi-directional streaming gRPC operations.
     */
    public static final class ManyToMany {
        private ManyToMany() { }

        /**
         * Retries a streaming gRPC call, using the same semantics as {@link Flowable#retryWhen(Function)}.
         *
         * For easier use, use the RetryWhen builder from
         * <a href="https://davidmoten.github.io/rxjava2-extras/apidocs/com/github/davidmoten/rx2/RetryWhen.html">RxJava2 Extras</a>.
         *
         * @param operation the gRPC operation to retry, typically from a generated reactive-grpc stub class
         * @param handler receives a Publisher of notifications with which a user can complete or error, aborting the retry
         * @param <I> I
         * @param <O> O
         *
         * @see Flowable#retryWhen(Function)
         */
        public static <I, O> FlowableTransformer<I, O> retryWhen(final Function<Flowable<I>, Flowable<O>> operation, final Function<? super Flowable<Throwable>, ? extends Publisher<?>> handler) {
            return new FlowableTransformer<I, O>() {
                @Override
                public Flowable<O> apply(final Flowable<I> request) {
                    return Flowable.defer(new Callable<Publisher<O>>() {
                        @Override
                        public Publisher<O> call() throws Exception {
                            return operation.apply(request);
                        }
                    }).retryWhen(handler);
                }
            };
        }

        /**
         * Retries a streaming gRPC call with a fixed delay between retries.
         *
         * @param operation the gRPC operation to retry, typically from a generated reactive-grpc stub class
         * @param delay the delay between retries
         * @param unit the units to use for {@code delay}
         * @param <I> I
         * @param <O> O
         */
        public static <I, O> FlowableTransformer<I, O> retryAfter(final Function<Flowable<I>, Flowable<O>> operation, final int delay, final TimeUnit unit) {
            return retryWhen(operation, new Function<Flowable<Throwable>, Publisher<?>>() {
                @Override
                public Publisher<?> apply(Flowable<Throwable> errors) {
                    return errors.flatMap(new Function<Throwable, Publisher<?>>() {
                        @Override
                        public Publisher<?> apply(Throwable error) {
                            return Flowable.timer(delay, unit);
                        }
                    });
                }
            });
        }

        /**
         * Retries a streaming gRPC call immediately.
         *
         * @param operation the gRPC operation to retry, typically from a generated reactive-grpc stub class
         * @param <I> I
         * @param <O> O
         */
        public static <I, O> FlowableTransformer<I, O> retryImmediately(final Function<Flowable<I>, Flowable<O>> operation) {
            return retryWhen(operation, new Function<Flowable<Throwable>, Publisher<?>>() {
                @Override
                public Publisher<?> apply(Flowable<Throwable> errors) {
                    return errors;
                }
            });
        }
    }

    /**
     * {@link GrpcRetry} functions for streaming request gRPC operations.
     */
    public static final class ManyToOne {
        private ManyToOne() { }

        /**
         * Retries a streaming gRPC call, using the same semantics as {@link Flowable#retryWhen(Function)}.
         *
         * For easier use, use the RetryWhen builder from
         * <a href="https://davidmoten.github.io/rxjava2-extras/apidocs/com/github/davidmoten/rx2/RetryWhen.html">RxJava2 Extras</a>.
         *
         * @param operation the gRPC operation to retry, typically from a generated reactive-grpc stub class
         * @param handler receives a Publisher of notifications with which a user can complete or error, aborting the retry
         * @param <I> I
         * @param <O> O
         *
         * @see Flowable#retryWhen(Function)
         */
        public static <I, O> FlowableConverter<I, Single<O>> retryWhen(final Function<Flowable<I>, Single<O>> operation, final Function<? super Flowable<Throwable>, ? extends Publisher<?>> handler) {
            return new FlowableConverter<I, Single<O>>() {
                @Override
                public Single<O> apply(final Flowable<I> request) {
                    return Single.defer(new Callable<SingleSource<O>>() {
                        @Override
                        public SingleSource<O> call() throws Exception {
                            return operation.apply(request);
                        }
                    }).retryWhen(handler);
                }
            };
        }

        /**
         * Retries a streaming gRPC call with a fixed delay between retries.
         *
         * @param operation the gRPC operation to retry, typically from a generated reactive-grpc stub class
         * @param delay the delay between retries
         * @param unit the units to use for {@code delay}
         * @param <I> I
         * @param <O> O
         */
        public static <I, O> FlowableConverter<I, Single<O>> retryAfter(final Function<Flowable<I>, Single<O>> operation, final int delay, final TimeUnit unit) {
            return retryWhen(operation, new Function<Flowable<Throwable>, Publisher<?>>() {
                @Override
                public Publisher<?> apply(Flowable<Throwable> errors) {
                    return errors.flatMap(new Function<Throwable, Publisher<?>>() {
                        @Override
                        public Publisher<?> apply(Throwable error) {
                            return Flowable.timer(delay, unit);
                        }
                    });
                }
            });
        }

        /**
         * Retries a streaming gRPC call immediately.
         *
         * @param operation the gRPC operation to retry, typically from a generated reactive-grpc stub class
         * @param <I> I
         * @param <O> O
         */
        public static <I, O> FlowableConverter<I, Single<O>> retryImmediately(final Function<Flowable<I>, Single<O>> operation) {
            return retryWhen(operation, new Function<Flowable<Throwable>, Publisher<?>>() {
                @Override
                public Publisher<?> apply(Flowable<Throwable> errors) {
                    return errors;
                }
            });
        }
    }
}
