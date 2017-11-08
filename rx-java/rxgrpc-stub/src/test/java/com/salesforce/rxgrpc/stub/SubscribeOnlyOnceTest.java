/*
 *  Copyright (c) 2017, salesforce.com, inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.rxgrpc.stub;

import io.reactivex.SingleObserver;
import io.reactivex.disposables.Disposable;
import org.junit.Test;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

public class SubscribeOnlyOnceTest {
    @Test
    public void subscribeOnlyOnceFlowableOperatorErrorsWhenMultipleSubscribe() throws Exception {
        SubscribeOnlyOnceFlowableOperator<Object> op = new SubscribeOnlyOnceFlowableOperator<>();
        Subscriber<Object> innerSub = mock(Subscriber.class);
        Subscription subscription = mock(Subscription.class);

        Subscriber<Object> outerSub = op.apply(innerSub);

        outerSub.onSubscribe(subscription);
        assertThatThrownBy(() -> outerSub.onSubscribe(subscription))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("cannot directly subscribe to a gRPC service multiple times");

        verify(innerSub, times(1)).onSubscribe(subscription);
    }

    @Test
    public void subscribeOnlyOnceSingleOperatorErrorsWhenMultipleSubscribe() throws Exception {
        SubscribeOnlyOnceSingleOperator<Object> op = new SubscribeOnlyOnceSingleOperator<>();
        SingleObserver<Object> innerSub = mock(SingleObserver.class);
        Disposable disposable = mock(Disposable.class);

        SingleObserver<Object> outerSub = op.apply(innerSub);

        outerSub.onSubscribe(disposable);
        assertThatThrownBy(() -> outerSub.onSubscribe(disposable))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("cannot directly subscribe to a gRPC service multiple times");

        verify(innerSub, times(1)).onSubscribe(disposable);
    }
}
