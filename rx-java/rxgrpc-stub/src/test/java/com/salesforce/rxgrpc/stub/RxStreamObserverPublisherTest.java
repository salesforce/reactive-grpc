/*
 *  Copyright (c) 2017, salesforce.com, inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.rxgrpc.stub;

import io.grpc.stub.CallStreamObserver;
import org.junit.Test;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
public class RxStreamObserverPublisherTest {
    @Test
    public void onNextDelegates() {
        CallStreamObserver<Object> obs = mock(CallStreamObserver.class);
        Subscriber<Object> sub = mock(Subscriber.class);

        RxStreamObserverPublisher<Object> pub = new RxStreamObserverPublisher<>(obs);
        pub.subscribe(sub);

        Object obj = new Object();

        pub.onNext(obj);
        verify(sub).onNext(obj);
    }

    @Test
    public void onErrorDelegates() {
        CallStreamObserver<Object> obs = mock(CallStreamObserver.class);
        Subscriber<Object> sub = mock(Subscriber.class);

        RxStreamObserverPublisher<Object> pub = new RxStreamObserverPublisher<>(obs);
        pub.subscribe(sub);

        Throwable obj = new Exception();

        pub.onError(obj);
        verify(sub).onError(obj);
    }

    @Test
    public void onCompletedDelegates() {
        CallStreamObserver<Object> obs = mock(CallStreamObserver.class);
        Subscriber<Object> sub = mock(Subscriber.class);

        RxStreamObserverPublisher<Object> pub = new RxStreamObserverPublisher<>(obs);
        pub.subscribe(sub);

        pub.onCompleted();
        verify(sub).onComplete();
    }

    @Test
    public void requestDelegates() {
        CallStreamObserver<Object> obs = mock(CallStreamObserver.class);
        Subscriber<Object> sub = mock(Subscriber.class);

        AtomicReference<Subscription> subscription = new AtomicReference<>();
        doAnswer(invocationOnMock -> {
            subscription.set((Subscription)invocationOnMock.getArguments()[0]);
            return null;
        }).when(sub).onSubscribe(any(Subscription.class));

        RxStreamObserverPublisher<Object> pub = new RxStreamObserverPublisher<>(obs);
        pub.subscribe(sub);

        assertThat(subscription.get()).isNotNull();
        subscription.get().request(10);
        verify(obs).request(10);
    }
}
