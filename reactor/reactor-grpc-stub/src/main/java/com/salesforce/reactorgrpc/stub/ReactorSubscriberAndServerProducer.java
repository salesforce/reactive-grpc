/*
 *  Copyright (c) 2017, salesforce.com, inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.reactorgrpc.stub;

import com.salesforce.reactivegrpc.common.AbstractSubscriberAndProducer;
import com.salesforce.reactivegrpc.common.AbstractSubscriberAndServerProducer;
import org.reactivestreams.Subscription;
import reactor.core.CoreSubscriber;
import reactor.core.Fuseable;

/**
 * The gRPC server-side implementation of {@link AbstractSubscriberAndProducer}.
 *
 * @param <T>
 */
public class ReactorSubscriberAndServerProducer<T>
        extends AbstractSubscriberAndServerProducer<T>
        implements CoreSubscriber<T> {

    @Override
    protected void fuse(Subscription s) {
        if (s instanceof Fuseable.QueueSubscription) {
            @SuppressWarnings("unchecked")
            Fuseable.QueueSubscription<T> f = (Fuseable.QueueSubscription<T>) s;

            int m = f.requestFusion(Fuseable.ANY);

            if (m == Fuseable.SYNC) {
                sourceMode = Fuseable.SYNC;
                queue = f;
                done = true;
            } else if (m == Fuseable.ASYNC) {
                sourceMode = Fuseable.ASYNC;
                queue = f;
            }
        }
    }
}
