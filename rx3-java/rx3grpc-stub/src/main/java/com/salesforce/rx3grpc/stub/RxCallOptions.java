/*
 *  Copyright (c) 2019, Salesforce.com, Inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.rx3grpc.stub;

import com.salesforce.reactivegrpc.common.AbstractStreamObserverAndPublisher;

import io.grpc.CallOptions;

/**
 * RX Call options.
 */
public final class RxCallOptions {

    private RxCallOptions() {
    }

    /**
     * Sets Prefetch size of queue.
     */
    public static final io.grpc.CallOptions.Key<Integer> CALL_OPTIONS_PREFETCH =
        io.grpc.CallOptions.Key.createWithDefault("reactivegrpc.internal.PREFETCH",
            Integer.valueOf(AbstractStreamObserverAndPublisher.DEFAULT_CHUNK_SIZE));

    /**
     * Sets Low Tide of prefetch queue.
     */
    public static final io.grpc.CallOptions.Key<Integer> CALL_OPTIONS_LOW_TIDE =
        io.grpc.CallOptions.Key.createWithDefault("reactivegrpc.internal.LOW_TIDE",
            Integer.valueOf(AbstractStreamObserverAndPublisher.TWO_THIRDS_OF_DEFAULT_CHUNK_SIZE));


    /**
     * Utility function to get prefetch option.
     */
    public static int getPrefetch(final CallOptions options) {
        return options == null ? CALL_OPTIONS_PREFETCH.getDefault() : options.getOption(CALL_OPTIONS_PREFETCH);
    }

    /**
     * Utility function to get low tide option together with validation.
     */
    public static int getLowTide(final CallOptions options) {
        int prefetch = getPrefetch(options);
        int lowTide = options == null ? CALL_OPTIONS_LOW_TIDE.getDefault() : options.getOption(CALL_OPTIONS_LOW_TIDE);
        if (lowTide >= prefetch) {
            throw new IllegalArgumentException(CALL_OPTIONS_LOW_TIDE + " must be less than " + CALL_OPTIONS_PREFETCH);
        }
        return lowTide;
    }
}
