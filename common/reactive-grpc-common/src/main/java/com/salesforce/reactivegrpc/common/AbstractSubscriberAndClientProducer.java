/*
 *  Copyright (c) 2017, salesforce.com, inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.reactivegrpc.common;

import io.grpc.Status;
import io.grpc.stub.ClientCallStreamObserver;

/**
 * The gRPC client-side implementation of {@link AbstractSubscriberAndProducer}.
 *
 * @param <T>
 */
public abstract class AbstractSubscriberAndClientProducer<T>
        extends AbstractSubscriberAndProducer<T> {

    @Override
    public void cancel() {
        if (!isCanceled()) {
            super.cancel();
            ((ClientCallStreamObserver<?>) downstream).cancel("Cancelled", Status.CANCELLED.asException());
        }
    }
}
