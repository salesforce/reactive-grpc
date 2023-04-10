/*
 *  Copyright (c) 2019, Salesforce.com, Inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.rx3grpc.stub;

import com.salesforce.reactivegrpc.common.Function;
import org.reactivestreams.Subscription;

import com.salesforce.reactivegrpc.common.AbstractSubscriberAndServerProducer;

import io.reactivex.rxjava3.core.FlowableSubscriber;
import io.reactivex.rxjava3.operators.QueueSubscription;

/**
 * The gRPC server-side implementation of {@link com.salesforce.reactivegrpc.common.AbstractSubscriberAndProducer}.
 *
 * @param <T> T
 */
public class RxSubscriberAndServerProducer<T>
        extends AbstractSubscriberAndServerProducer<T>
        implements FlowableSubscriber<T> {

    public RxSubscriberAndServerProducer(Function<Throwable, Throwable> prepareError) {
        super(prepareError);
    }

    @Override
    protected Subscription fuse(Subscription s) {
        if (s instanceof QueueSubscription) {
            @SuppressWarnings("unchecked")
            QueueSubscription<T> f = (QueueSubscription<T>) s;

            int m = f.requestFusion(QueueSubscription.ANY);

            if (m != QueueSubscription.NONE) {
                return new FusionAwareQueueSubscriptionAdapter<T>(f, m);
            }
        }

        return s;
    }
}
