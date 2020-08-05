/*
 *  Copyright (c) 2019, Salesforce.com, Inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.reactivegrpc.common;

import io.grpc.stub.CallStreamObserver;
import io.grpc.stub.ServerCallStreamObserver;

/**
 * The gRPC server-side implementation of {@link AbstractSubscriberAndProducer}.
 *
 * @param <T> T
 */
public abstract class AbstractSubscriberAndServerProducer<T>
        extends AbstractSubscriberAndProducer<T> {

    @Override
    public void subscribe(CallStreamObserver<T> downstream) {
        super.subscribe(downstream);
        ((ServerCallStreamObserver<?>) downstream).setOnCancelHandler(AbstractSubscriberAndServerProducer.super::cancel);
    }
}
