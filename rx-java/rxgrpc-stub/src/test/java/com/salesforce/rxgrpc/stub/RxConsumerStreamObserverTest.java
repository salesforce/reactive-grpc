/*
 *  Copyright (c) 2017, salesforce.com, inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.rxgrpc.stub;

import io.grpc.stub.ClientCallStreamObserver;
import io.reactivex.Flowable;
import io.reactivex.subscribers.TestSubscriber;
import org.junit.Test;
import org.reactivestreams.Subscriber;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@SuppressWarnings("unchecked")
public class RxConsumerStreamObserverTest {
    @Test
    public void rxConsumerIsSet() {
        ClientCallStreamObserver<Object> obs = mock(ClientCallStreamObserver.class);
        RxConsumerStreamObserver rxObs = new RxConsumerStreamObserver();

        rxObs.beforeStart(obs);

        assertThat(rxObs.getRxConsumer()).isNotNull();
    }

    @Test
    public void onNextDelegates() {
        ClientCallStreamObserver<Object> obs = mock(ClientCallStreamObserver.class);
        RxConsumerStreamObserver rxObs = new RxConsumerStreamObserver();
        Subscriber<Object> sub = mock(Subscriber.class);

        rxObs.beforeStart(obs);
        rxObs.getRxConsumer().subscribe(sub);

        TestSubscriber<Object> testSubscriber = ((Flowable<Object>)rxObs.getRxConsumer()).test();

        Object obj = new Object();
        rxObs.onNext(obj);
        rxObs.onCompleted();

        testSubscriber.awaitTerminalEvent(3, TimeUnit.SECONDS);
        testSubscriber.assertValues(obj);
    }

    @Test
    public void onErrorDelegates() {
        ClientCallStreamObserver<Object> obs = mock(ClientCallStreamObserver.class);
        RxConsumerStreamObserver rxObs = new RxConsumerStreamObserver();
        Subscriber<Object> sub = mock(Subscriber.class);

        rxObs.beforeStart(obs);
        rxObs.getRxConsumer().subscribe(sub);

        TestSubscriber<Object> testSubscriber = ((Flowable<Object>)rxObs.getRxConsumer()).test();

        Throwable obj = new Exception();
        rxObs.onError(obj);

        testSubscriber.awaitTerminalEvent(3, TimeUnit.SECONDS);
        testSubscriber.assertError(obj);
    }
}
