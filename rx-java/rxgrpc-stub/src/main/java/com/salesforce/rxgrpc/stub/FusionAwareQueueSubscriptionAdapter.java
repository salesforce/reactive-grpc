/*
 *  Copyright (c) 2019, Salesforce.com, Inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */
package com.salesforce.rxgrpc.stub;

import com.salesforce.reactivegrpc.common.FusionModeAwareSubscription;
import com.salesforce.reactivegrpc.common.AbstractUnimplementedQueue;
import io.reactivex.exceptions.Exceptions;
import io.reactivex.internal.fuseable.QueueSubscription;

/**
 * Implementation of FusionModeAwareSubscription which encapsulate
 * {@link QueueSubscription} from RxJava internals and allows treat it as a {@link java.util.Queue}.
 *
 * @param <T> T
 */
class FusionAwareQueueSubscriptionAdapter<T> extends AbstractUnimplementedQueue<T> implements QueueSubscription<T>, FusionModeAwareSubscription {

    private final QueueSubscription<T> delegate;
    private final int                  mode;

    FusionAwareQueueSubscriptionAdapter(QueueSubscription<T> delegate, int mode) {
        this.delegate = delegate;
        this.mode = mode;
    }

    @Override
    public int mode() {
        return mode;
    }

    @Override
    public int requestFusion(int mode) {
        return delegate.requestFusion(mode);
    }

    @Override
    public void request(long l) {
        delegate.request(l);
    }

    @Override
    public void cancel() {
        delegate.cancel();
    }

    @Override
    public T poll() {
        try {
            return delegate.poll();
        } catch (Exception e) {
            throw Exceptions.propagate(e);
        }
    }

    @Override
    public boolean offer(T t) {
        return delegate.offer(t);
    }

    @Override
    public boolean offer(T v1, T v2) {
        return delegate.offer(v1, v2);
    }

    @Override
    public boolean isEmpty() {
        return delegate.isEmpty();
    }

    @Override
    public void clear() {
        delegate.clear();
    }
}
