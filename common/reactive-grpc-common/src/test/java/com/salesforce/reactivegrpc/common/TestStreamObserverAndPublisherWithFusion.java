/*  Copyright (c) 2017, salesforce.com, inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.reactivegrpc.common;

import java.util.Queue;

import io.reactivex.internal.fuseable.QueueFuseable;
import io.reactivex.internal.fuseable.QueueSubscription;

public class TestStreamObserverAndPublisherWithFusion<T> extends AbstractStreamObserverAndPublisher<T>
        implements QueueSubscription<T> {

    public TestStreamObserverAndPublisherWithFusion(Queue queue, Consumer onSubscribe) {
        super(queue, onSubscribe);
    }

    public TestStreamObserverAndPublisherWithFusion(Queue queue,
            Consumer onSubscribe,
            Runnable onTerminate) {
        super(queue, onSubscribe, onTerminate);
    }

    public TestStreamObserverAndPublisherWithFusion(Queue queue,
            int prefetch,
            Consumer onSubscribe) {
        super(queue, prefetch, onSubscribe);
    }

    public TestStreamObserverAndPublisherWithFusion(Queue queue,
            int prefetch,
            Consumer onSubscribe,
            Runnable onTerminate) {
        super(queue, prefetch, onSubscribe, onTerminate);
    }

    @Override
    public int requestFusion(int requestedMode) {
        if ((requestedMode & QueueFuseable.ASYNC) != 0) {
            outputFused = true;
            return QueueFuseable.ASYNC;
        }
        return QueueFuseable.NONE;
    }

    @Override
    public boolean offer(T t, T t1) {
        throw new UnsupportedOperationException();
    }
}
