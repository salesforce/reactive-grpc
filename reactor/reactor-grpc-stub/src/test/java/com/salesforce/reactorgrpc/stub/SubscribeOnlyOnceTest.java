/*
 *  Copyright (c) 2017, salesforce.com, inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.reactorgrpc.stub;

import org.junit.Test;
import org.reactivestreams.Subscription;
import reactor.core.CoreSubscriber;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

public class SubscribeOnlyOnceTest {
    @Test
    public void subscribeOnlyOnceLifterErrorsWhenMultipleSubscribe() throws Exception {
        SubscribeOnlyOnceLifter<Object> op = new SubscribeOnlyOnceLifter<>();
        CoreSubscriber<Object> innerSub = mock(CoreSubscriber.class);
        Subscription subscription = mock(Subscription.class);

        CoreSubscriber<Object> outerSub = op.apply(null, innerSub);

        outerSub.onSubscribe(subscription);
        assertThatThrownBy(() -> outerSub.onSubscribe(subscription))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("cannot directly subscribe to a gRPC service multiple times");

        verify(innerSub, times(1)).onSubscribe(subscription);
    }
}
