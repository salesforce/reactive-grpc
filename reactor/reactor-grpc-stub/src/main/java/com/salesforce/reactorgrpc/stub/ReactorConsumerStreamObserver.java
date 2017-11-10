/*
 *  Copyright (c) 2017, salesforce.com, inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.reactorgrpc.stub;

import com.salesforce.reactivegrpccommon.ReactiveConsumerStreamObserver;
import com.salesforce.reactivegrpccommon.ReactiveExecutor;
import com.salesforce.reactivegrpccommon.ReactiveStreamObserverPublisher;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

/**
 * ReactorConsumerStreamObserver configures client-side manual flow control for the consuming end of a message stream.
 *
 * @param <TRequest>
 * @param <TResponse>
 */
public class ReactorConsumerStreamObserver<TRequest, TResponse> extends ReactiveConsumerStreamObserver<TRequest, TResponse> {

    @Override
    public Publisher<TResponse> getReactiveConsumerFromPublisher(ReactiveStreamObserverPublisher<TResponse> publisher) {
        return Flux.from(publisher).publishOn(Schedulers.fromExecutor(ReactiveExecutor.getSerializingExecutor()));
    }

}