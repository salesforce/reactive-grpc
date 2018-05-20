/*
 *  Copyright (c) 2017, salesforce.com, inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.rxgrpc.stub;

import com.salesforce.reactivegrpc.common.ReactiveConsumerStreamObserver;
import com.salesforce.reactivegrpc.common.ReactiveStreamObserverPublisher;
import io.reactivex.Flowable;
import org.reactivestreams.Publisher;

/**
 * RxConsumerStreamObserver configures client-side manual flow control for the consuming end of a message stream.
 *
 * @param <TRequest>
 * @param <TResponse>
 */
public class RxConsumerStreamObserver<TRequest, TResponse> extends ReactiveConsumerStreamObserver<TRequest, TResponse> {

    @Override
    public Publisher<TResponse>  getReactiveConsumerFromPublisher(ReactiveStreamObserverPublisher<TResponse>  publisher) {
        return Flowable.unsafeCreate(publisher).lift(new BackpressureChunkingOperator<TResponse>());
    }
}