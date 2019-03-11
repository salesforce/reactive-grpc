/*
 *  Copyright (c) 2019, Salesforce.com, Inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.reactorgrpc.stub;

import com.salesforce.reactivegrpc.common.FusionModeAwareSubscription;
import reactor.core.Fuseable;

/**
 * Implementation of FusionModeAwareSubscription which encapsulate
 * {@link Fuseable.QueueSubscription} from Reactor-Core internals and allows.
 *
 * @param <T> T
 */
class FusionAwareQueueSubscriptionAdapter<T> implements Fuseable.QueueSubscription<T>, FusionModeAwareSubscription {

    private final Fuseable.QueueSubscription<T> delegate;
    private final int                           mode;

    FusionAwareQueueSubscriptionAdapter(Fuseable.QueueSubscription<T> delegate, int mode) {
        this.delegate = delegate;
        this.mode = mode;
    }

    @Override
    public int mode() {
        return mode;
    }

    @Override
    public T poll() {
        return delegate.poll();
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
    public int requestFusion(int requestedMode) {
        return delegate.requestFusion(requestedMode);
    }

    @Override
    public int size() {
        return delegate.size();
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
