package com.salesforce.reactivegrpc.common;

import io.reactivex.FlowableSubscriber;
import io.reactivex.internal.fuseable.QueueSubscription;
import org.reactivestreams.Subscription;

public class TestSubscriberProducer<T> extends AbstractSubscriberAndProducer<T>
                                       implements FlowableSubscriber<T> {
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
