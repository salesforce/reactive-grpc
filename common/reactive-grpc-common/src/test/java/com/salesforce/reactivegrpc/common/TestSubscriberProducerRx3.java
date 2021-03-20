/*
 *  Copyright (c) 2019, Salesforce.com, Inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */
package com.salesforce.reactivegrpc.common;

import org.reactivestreams.Subscription;

import io.reactivex.rxjava3.core.FlowableSubscriber;
import io.reactivex.rxjava3.internal.fuseable.QueueSubscription;

/**
 * This class is a test-purpose implementation of the
 * {@link AbstractSubscriberAndProducer} class. Note, implementation supports fusion
 * from RxJava 2
 * @param <T>
 */
public class TestSubscriberProducerRx3<T> extends AbstractSubscriberAndProducer<T>
                                       implements FlowableSubscriber<T> {
    @Override
    protected Subscription fuse(Subscription s) {
        if (s instanceof QueueSubscription) {
            @SuppressWarnings("unchecked")
            QueueSubscription<T> f = (QueueSubscription<T>) s;

            int m = f.requestFusion(QueueSubscription.ANY);

            if (m != QueueSubscription.NONE) {
                return new FusionAwareQueueSubscriptionAdapterRx3<T>(f, m);
            }
        }

        return s;
    }
}
