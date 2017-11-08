/*
 *  Copyright (c) 2017, salesforce.com, inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.rxgrpc.stub;

import com.google.common.base.Preconditions;
import com.salesforce.reactivegrpccommon.ReactiveExecutor;
import com.salesforce.reactivegrpccommon.ReactiveStreamObserverPublisher;
import io.grpc.Status;
import io.grpc.stub.ClientCallStreamObserver;
import io.grpc.stub.ClientResponseObserver;
import io.reactivex.Flowable;
import io.reactivex.schedulers.Schedulers;

import java.util.concurrent.CountDownLatch;

/**
 * RxConsumerStreamObserver configures client-side manual flow control for the consuming end of a message stream.
 *
 * @param <TRequest>
 * @param <TResponse>
 */
public class RxConsumerStreamObserver<TRequest, TResponse> implements ClientResponseObserver<TRequest, TResponse> {
    private ReactiveStreamObserverPublisher<TResponse> publisher;
    private Flowable<TResponse> rxConsumer;
    private CountDownLatch beforeStartCalled = new CountDownLatch(1);

    public Flowable<TResponse> getRxConsumer() {
        try {
            beforeStartCalled.await();
        } catch (InterruptedException e) {
            throw Status.INTERNAL.withCause(e).asRuntimeException();
        }
        return rxConsumer;
    }


    @Override
    public void beforeStart(ClientCallStreamObserver<TRequest> requestStream) {
        publisher = new ReactiveStreamObserverPublisher<>(Preconditions.checkNotNull(requestStream));

        rxConsumer = Flowable.unsafeCreate(publisher)
                .observeOn(Schedulers.from(ReactiveExecutor.getSerializingExecutor()));
        beforeStartCalled.countDown();
    }

    @Override
    public void onNext(TResponse value) {
        Preconditions.checkState(publisher != null, "beforeStart() not yet called");
        publisher.onNext(Preconditions.checkNotNull(value));
    }

    @Override
    public void onError(Throwable throwable) {
        Preconditions.checkState(publisher != null, "beforeStart() not yet called");
        publisher.onError(Preconditions.checkNotNull(throwable));
    }

    @Override
    public void onCompleted() {
        Preconditions.checkState(publisher != null, "beforeStart() not yet called");
        publisher.onCompleted();
    }
}