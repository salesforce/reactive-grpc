/*
 *  Copyright (c) 2019, Salesforce.com, Inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.rx3grpc.stub;

import java.util.concurrent.atomic.AtomicBoolean;

import io.reactivex.rxjava3.core.SingleObserver;
import io.reactivex.rxjava3.core.SingleOperator;
import io.reactivex.rxjava3.disposables.Disposable;

/**
 * SubscribeOnlyOnceSingleOperator throws an exception if a user attempts to subscribe more than once to a
 * {@link io.reactivex.rxjava3.core.Single}.
 *
 * @param <T> T
 */
public class SubscribeOnlyOnceSingleOperator<T> implements SingleOperator<T, T> {
    private AtomicBoolean subscribedOnce = new AtomicBoolean(false);

    @Override
    public SingleObserver<? super T> apply(final SingleObserver<? super T> observer) {
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
