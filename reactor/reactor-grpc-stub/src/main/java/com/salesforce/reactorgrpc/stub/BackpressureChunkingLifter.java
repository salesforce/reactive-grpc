/*
 *  Copyright (c) 2017, salesforce.com, inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.reactorgrpc.stub;

import com.salesforce.reactivegrpc.common.ReactiveBackpressureChunker;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import reactor.core.CoreSubscriber;
import reactor.core.Scannable;
import reactor.util.context.Context;

import java.util.function.BiFunction;

/**
 * Implements {@link ReactiveBackpressureChunker} as a liftable operator for {@link reactor.core.publisher.Operators#lift(BiFunction)}.
 *
 * @param <T>
 */
public class BackpressureChunkingLifter<T> extends ReactiveBackpressureChunker<T> implements BiFunction<Scannable, CoreSubscriber<? super T>, CoreSubscriber<? super T>> {

    public BackpressureChunkingLifter() {
        super(ReactiveBackpressureChunker.DEFAULT_CHUNK_SIZE);
    }

    @Override
    public CoreSubscriber<? super T> apply(Scannable scannable, CoreSubscriber<? super T> downstream) {
        Subscriber<? super T> delegate = apply(downstream);

        return new CoreSubscriber<T>() {
            @Override
            public Context currentContext() {
                return downstream.currentContext();
            }

            @Override
            public void onSubscribe(Subscription subscription) {
                delegate.onSubscribe(subscription);
            }

            @Override
            public void onNext(T t) {
                delegate.onNext(t);
            }

            @Override
            public void onError(Throwable throwable) {
                delegate.onError(throwable);
            }

            @Override
            public void onComplete() {
                delegate.onComplete();
            }
        };
    }
}
