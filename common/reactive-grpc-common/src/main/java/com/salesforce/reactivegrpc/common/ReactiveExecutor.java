/*
 *  Copyright (c) 2017, salesforce.com, inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.reactivegrpc.common;

import io.grpc.internal.GrpcUtil;
import io.grpc.internal.SerializingExecutor;

import java.util.concurrent.Executor;

/**
 * ReactiveExecutor holds a shared executor used by the Reactive Streams implementation to marshall messages between Reactive and gRPC streams.
 */
public final class ReactiveExecutor {
    private ReactiveExecutor() {

    }

    private static Executor executor = GrpcUtil.SHARED_CHANNEL_EXECUTOR.create();

    /**
     * Get the shared executor.
     */
    public static Executor getSerializingExecutor() {
        return new SerializingExecutor(executor);
    }
}
