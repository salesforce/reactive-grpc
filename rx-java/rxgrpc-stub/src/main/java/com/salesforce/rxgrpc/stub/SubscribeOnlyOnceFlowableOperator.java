/*
 *  Copyright (c) 2017, salesforce.com, inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.rxgrpc.stub;


import io.reactivex.FlowableOperator;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * SubscribeOnlyOnceFlowableOperator throws an exception if a user attempts to subscribe more than once to a
 * {@link io.reactivex.Flowable}.
 *
 * @param <T>
 */
public class SubscribeOnlyOnceFlowableOperator<T> implements FlowableOperator<T, T> {
    private AtomicBoolean subscribedOnce = new AtomicBoolean(false);

    @Override
    public Subscriber<? super T> apply(Subscriber<? super T> observer) throws Exception {
        return new Subscriber<T>() {
            @Override
            public void onSubscribe(Subscription subscription) {
                if (subscribedOnce.getAndSet(true)) {
                    throw new NullPointerException("You cannot directly subscribe to a gRPC service multiple times " +
                            "concurrently. Use Flowable.share() instead.");
                } else {
                    observer.onSubscribe(subscription);
                }
            }

            @Override
            public void onNext(T t) {
                observer.onNext(t);
            }

            @Override
            public void onError(Throwable throwable) {
                observer.onError(throwable);
            }

            @Override
            public void onComplete() {
                observer.onComplete();
            }
        };
    }
}
