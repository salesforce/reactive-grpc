/*
 *  Copyright (c) 2019, Salesforce.com, Inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.reactorgrpc.stub;

import org.reactivestreams.Subscription;
import reactor.core.CoreSubscriber;
import reactor.core.Scannable;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;

/**
 * SubscribeOnlyOnceLifter throws an exception if a user attempts to subscribe more than once to a
 * {@link reactor.core.publisher.Flux}.
 *
 * @param <T> T
 */
public class SubscribeOnlyOnceLifter<T> extends AtomicBoolean implements BiFunction<Scannable, CoreSubscriber<? super T>, CoreSubscriber<? super T>> {

    @Override
    public CoreSubscriber<? super T> apply(Scannable scannable, CoreSubscriber<? super T> coreSubscriber) {
        return new CoreSubscriber<T>() {
            @Override
            public void onSubscribe(Subscription subscription) {
                if (!compareAndSet(false, true)) {
                    throw new NullPointerException("You cannot directly subscribe to a gRPC service multiple times " +
                            "concurrently. Use Flux.share() instead.");
                } else {
                    coreSubscriber.onSubscribe(subscription);
                }
            }

            @Override
            public void onNext(T t) {
                coreSubscriber.onNext(t);
            }

            @Override
            public void onError(Throwable throwable) {
                coreSubscriber.onError(throwable);
            }

            @Override
            public void onComplete() {
                coreSubscriber.onComplete();
            }
        };
    }

}
