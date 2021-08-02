/*
 *  Copyright (c) 2019, Salesforce.com, Inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.rx3grpc.stub;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import io.reactivex.rxjava3.core.SingleObserver;
import io.reactivex.rxjava3.disposables.Disposable;

@SuppressWarnings("unchecked")
public class SubscribeOnlyOnceTest {
    @Test
    public void subscribeOnlyOnceFlowableOperatorErrorsWhenMultipleSubscribe() {
        SubscribeOnlyOnceFlowableOperator<Object> op = new SubscribeOnlyOnceFlowableOperator<Object>();
        Subscriber<Object> innerSub = mock(Subscriber.class);
        final Subscription subscription = mock(Subscription.class);

        final Subscriber<Object> outerSub = op.apply(innerSub);

        outerSub.onSubscribe(subscription);
        assertThatThrownBy(new ThrowableAssert.ThrowingCallable() {
            @Override
            public void call() {
                outerSub.onSubscribe(subscription);
            }
        })
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("cannot directly subscribe to a gRPC service multiple times");

        verify(innerSub, times(1)).onSubscribe(subscription);
    }

    @Test
    public void subscribeOnlyOnceSingleOperatorErrorsWhenMultipleSubscribe() {
        SubscribeOnlyOnceSingleOperator<Object> op = new SubscribeOnlyOnceSingleOperator<Object>();
        SingleObserver<Object> innerSub = mock(SingleObserver.class);
        final Disposable disposable = mock(Disposable.class);

        final SingleObserver<Object> outerSub = op.apply(innerSub);

        outerSub.onSubscribe(disposable);
        assertThatThrownBy(new ThrowableAssert.ThrowingCallable() {
            @Override
            public void call() {
                outerSub.onSubscribe(disposable);
            }
        })
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("cannot directly subscribe to a gRPC service multiple times");

        verify(innerSub, times(1)).onSubscribe(disposable);
    }
}
