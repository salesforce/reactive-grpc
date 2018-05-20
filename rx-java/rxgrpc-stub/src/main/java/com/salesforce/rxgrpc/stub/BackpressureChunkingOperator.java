/*
 *  Copyright (c) 2017, salesforce.com, inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.rxgrpc.stub;

import com.salesforce.reactivegrpc.common.ReactiveBackpressureChunker;
//import com.salesforce.reactivegrpc.common.ReactiveBackpressureChunker2;
import io.reactivex.FlowableOperator;

/**
 *  Implements {@link ReactiveBackpressureChunker} as a {@link FlowableOperator}.
 *
 * @param <T>
 */
public class BackpressureChunkingOperator<T> extends ReactiveBackpressureChunker<T> implements FlowableOperator<T, T> {
    public BackpressureChunkingOperator() {
        super(DEFAULT_CHUNK_SIZE);
    }
}
