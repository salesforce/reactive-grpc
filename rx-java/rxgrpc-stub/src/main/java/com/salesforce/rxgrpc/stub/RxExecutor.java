/*
 *  Copyright (c) 2017, salesforce.com, inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.rxgrpc.stub;

import io.grpc.internal.GrpcUtil;
import io.grpc.internal.SerializingExecutor;

import java.util.concurrent.Executor;

/**
 * RxExecutor holds a shared executor used by RxGrpc to marshall messages between RxJava and gRPC streams.
 */
public final class RxExecutor {
    private RxExecutor() {

    }

    private static Executor executor = GrpcUtil.SHARED_CHANNEL_EXECUTOR.create();

    /**
     * Get the shared executor.
     */
    public static Executor getSerializingExecutor() {
        return new SerializingExecutor(executor);
    }
}
