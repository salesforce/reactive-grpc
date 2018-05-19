/*
 *  Copyright (c) 2017, salesforce.com, inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.rxgrpc.stub;

import com.google.common.util.concurrent.MoreExecutors;
import com.salesforce.reactivegrpc.common.ReactiveProducerConsumerStreamObserver;
import com.salesforce.reactivegrpc.common.ReactiveStreamObserverPublisher;
import io.reactivex.Flowable;
import io.reactivex.schedulers.Schedulers;
import org.reactivestreams.Publisher;

import static com.salesforce.reactivegrpc.common.ReactiveConstants.PRODUCER_STREAM_PREFETCH;

/**
 * RxProducerConsumerStreamObserver configures client-side manual flow control for when the client is both producing
 * and consuming streams of messages.
 *
 * @param <TRequest>
 * @param <TResponse>
 */
public class RxProducerConsumerStreamObserver<TRequest, TResponse> extends ReactiveProducerConsumerStreamObserver<TRequest, TResponse> {

    public RxProducerConsumerStreamObserver(Publisher<TRequest> rxProducer) {
        super(rxProducer);
    }

    @Override
    public Publisher<TResponse> getReactiveConsumerFromPublisher(ReactiveStreamObserverPublisher<TResponse> publisher) {
        return Flowable.unsafeCreate(publisher)
                .observeOn(Schedulers.from(MoreExecutors.directExecutor()), false, PRODUCER_STREAM_PREFETCH);
    }
}
