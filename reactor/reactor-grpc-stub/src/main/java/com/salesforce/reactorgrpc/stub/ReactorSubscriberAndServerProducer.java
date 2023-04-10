/*
 *  Copyright (c) 2019, Salesforce.com, Inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.reactorgrpc.stub;

import com.salesforce.reactivegrpc.common.AbstractSubscriberAndServerProducer;
import com.salesforce.reactivegrpc.common.Function;
import org.reactivestreams.Subscription;
import reactor.core.CoreSubscriber;
import reactor.core.Fuseable;

/**
 * The gRPC server-side implementation of {@link com.salesforce.reactivegrpc.common.AbstractSubscriberAndProducer}.
 *
 * @param <T> T
 */
public class ReactorSubscriberAndServerProducer<T>
        extends AbstractSubscriberAndServerProducer<T>
        implements CoreSubscriber<T> {

    public ReactorSubscriberAndServerProducer(Function<Throwable, Throwable> prepareError) {
        super(prepareError);
    }

    @Override
    protected Subscription fuse(Subscription s) {
        if (s instanceof Fuseable.QueueSubscription) {
            @SuppressWarnings("unchecked")
            Fuseable.QueueSubscription<T> f = (Fuseable.QueueSubscription<T>) s;

            int m = f.requestFusion(Fuseable.ANY);

            if (m != Fuseable.NONE) {
                return new FusionAwareQueueSubscriptionAdapter<T>(f, m);
            }
        }

        return s;
    }
}
