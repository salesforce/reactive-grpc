/*
 *  Copyright (c) 2017, salesforce.com, inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.rxgrpc.stub;

import com.salesforce.reactivegrpc.common.AbstractSubscriberAndServerProducer;
import io.reactivex.FlowableSubscriber;
import io.reactivex.internal.fuseable.QueueSubscription;
import org.reactivestreams.Subscription;

/**
 * The gRPC server-side implementation of {@link com.salesforce.reactivegrpc.common.AbstractSubscriberAndProducer}.
 *
 * @param <T>
 */
public class RxSubscriberAndServerProducer<T>
        extends AbstractSubscriberAndServerProducer<T>
        implements FlowableSubscriber<T> {

    @Override
    protected void fuse(Subscription s) {
        if (s instanceof QueueSubscription) {
            @SuppressWarnings("unchecked")
            QueueSubscription<T> f = (QueueSubscription<T>) s;

            int m = f.requestFusion(QueueSubscription.ANY);

            if (m == QueueSubscription.SYNC) {
                sourceMode = QueueSubscription.SYNC;
                queue = new SimpleQueueAdapter<T>(f);
                done = true;
            } else if (m == QueueSubscription.ASYNC) {
                sourceMode = QueueSubscription.ASYNC;
                queue = new SimpleQueueAdapter<T>(f);
            }
        }
    }
}
