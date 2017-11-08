/*
 *  Copyright (c) 2017, salesforce.com, inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.rxgrpc.stub;

import io.reactivex.SingleObserver;
import io.reactivex.SingleOperator;
import io.reactivex.disposables.Disposable;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * SubscribeOnlyOnceSingleOperator throws an exception if a user attempts to subscribe more than once to a
 * {@link io.reactivex.Single}.
 *
 * @param <T>
 */
public class SubscribeOnlyOnceSingleOperator<T> implements SingleOperator<T, T> {
    private AtomicBoolean subscribedOnce = new AtomicBoolean(false);

    @Override
    public SingleObserver<? super T> apply(SingleObserver<? super T> observer) throws Exception {
        return new SingleObserver<T>() {
            @Override
            public void onSubscribe(Disposable d) {
                if (subscribedOnce.getAndSet(true)) {
                    throw new NullPointerException("You cannot directly subscribe to a gRPC service multiple times " +
                            "concurrently. Use Flowable.share() instead.");
                } else {
                    observer.onSubscribe(d);
                }
            }

            @Override
            public void onSuccess(T t) {
                observer.onSuccess(t);
            }

            @Override
            public void onError(Throwable e) {
                observer.onError(e);
            }
        };
    }
}
