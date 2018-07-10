/*
 *  Copyright (c) 2017, salesforce.com, inc.
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
 * TODO.
 */
public final class GrpcRetry {
    private GrpcRetry() { }

    /**
     * TODO.
     */
    public static final class OneToMany {
        private OneToMany() { }

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
     * TODO.
     */
    public static final class ManyToMany {
        private ManyToMany() { }

        public static <I, O> FlowableConverter<I, Flowable<O>> retryWhen(final Function<Flowable<I>, Flowable<O>> operation, final Function<? super Flowable<Throwable>, ? extends Publisher<?>> handler) {
            return new FlowableConverter<I, Flowable<O>>() {
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

        public static <I, O> FlowableConverter<I, Flowable<O>> retryAfter(final Function<Flowable<I>, Flowable<O>> operation, final int delay, final TimeUnit unit) {
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

        public static <I, O> FlowableConverter<I, Flowable<O>> retryImmediately(final Function<Flowable<I>, Flowable<O>> operation) {
            return retryWhen(operation, new Function<Flowable<Throwable>, Publisher<?>>() {
                @Override
                public Publisher<?> apply(Flowable<Throwable> errors) {
                    return errors;
                }
            });
        }
    }

    /**
     * TODO.
     */
    public static final class ManyToOne {
        private ManyToOne() { }

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
