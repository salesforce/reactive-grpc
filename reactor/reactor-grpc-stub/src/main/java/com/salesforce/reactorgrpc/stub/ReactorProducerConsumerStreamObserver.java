/*
 *  Copyright (c) 2017, salesforce.com, inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.reactorgrpc.stub;

import com.salesforce.reactivegrpc.common.ReactiveProducerConsumerStreamObserver;
import com.salesforce.reactivegrpc.common.ReactiveStreamObserverPublisher;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import static com.salesforce.reactivegrpc.common.ReactiveConstants.PRODUCER_STREAM_PREFETCH;

/**
 * ReactorProducerConsumerStreamObserver configures client-side manual flow control for when the client is both producing
 * and consuming streams of messages.
 *
 * @param <TRequest>
 * @param <TResponse>
 */
public class ReactorProducerConsumerStreamObserver<TRequest, TResponse> extends ReactiveProducerConsumerStreamObserver<TRequest, TResponse> {

    public ReactorProducerConsumerStreamObserver(Publisher<TRequest> rxProducer) {
        super(rxProducer);
    }

    @Override
    public Publisher<TResponse> getReactiveConsumerFromPublisher(ReactiveStreamObserverPublisher<TResponse> publisher) {
        return Flux.from(publisher).publishOn(Schedulers.immediate(), PRODUCER_STREAM_PREFETCH);
    }

}
