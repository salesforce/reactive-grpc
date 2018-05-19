/*
 *  Copyright (c) 2017, salesforce.com, inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.reactivegrpc.common;

/**
 * Shared constants used by reactive grpc implementations.
 */
public final class ReactiveConstants {
    private ReactiveConstants() { }

    /**
     * Prefetch (call request()) this many messages from a stream producer when starting a streaming gRPC operation.
     */
    public static final int PRODUCER_STREAM_PREFETCH = 8;
}
