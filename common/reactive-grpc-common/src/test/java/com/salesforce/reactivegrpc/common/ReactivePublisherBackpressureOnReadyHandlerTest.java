/*
 *  Copyright (c) 2017, salesforce.com, inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.reactivegrpc.common;

import io.grpc.stub.ClientCallStreamObserver;
import org.junit.Test;
import org.reactivestreams.Subscription;

import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
public class ReactivePublisherBackpressureOnReadyHandlerTest {
    @Test
    public void runPrimesThePump() {
        ClientCallStreamObserver<Object> obs = mock(ClientCallStreamObserver.class);
        ReactivePublisherBackpressureOnReadyHandler<Object> handler = new ReactivePublisherBackpressureOnReadyHandler<Object>(obs);
        Subscription sub = mock(Subscription.class);

        handler.onSubscribe(sub);

        handler.run();
        verify(sub).request(1);
    }

    @Test
    public void onNextKeepsPumpRunning() {
        ClientCallStreamObserver<Object> obs = mock(ClientCallStreamObserver.class);
        when(obs.isReady()).thenReturn(true);

        ReactivePublisherBackpressureOnReadyHandler<Object> handler = new ReactivePublisherBackpressureOnReadyHandler<Object>(obs);
        Subscription sub = mock(Subscription.class);

        handler.onSubscribe(sub);

        Object obj = new Object();
        handler.onNext(obj);

        verify(obs).onNext(obj);
        verify(sub).request(1);
    }

    @Test
    public void onNextStopsPump() {
        ClientCallStreamObserver<Object> obs = mock(ClientCallStreamObserver.class);
        when(obs.isReady()).thenReturn(false);

        ReactivePublisherBackpressureOnReadyHandler<Object> handler = new ReactivePublisherBackpressureOnReadyHandler<Object>(obs);
        Subscription sub = mock(Subscription.class);

        handler.onSubscribe(sub);

        Object obj = new Object();
        handler.onNext(obj);

        verify(obs).onNext(obj);
        verify(sub, never()).request(1);
    }
    
    @Test
    public void onNextWontThrowAndPropagatesThrowableToOnError() {
        ClientCallStreamObserver<Object> obs = mock(ClientCallStreamObserver.class);
        doThrow(new IllegalStateException("won't be propagated to handler caller")).when(obs).onNext(any());
        ReactivePublisherBackpressureOnReadyHandler<Object> handler = new ReactivePublisherBackpressureOnReadyHandler<Object>(obs);
        Subscription sub = mock(Subscription.class);
        handler.onSubscribe(sub);
        
        handler.onNext(new Object());
        verify(obs).onError(any(Throwable.class));
    }
    
    @Test
    public void onErrorWontThrow() {
        ClientCallStreamObserver<Object> obs = mock(ClientCallStreamObserver.class);
        doThrow(new IllegalStateException("won't be propagated to handler caller")).when(obs).onError(any(Throwable.class));
        ReactivePublisherBackpressureOnReadyHandler<Object> handler = new ReactivePublisherBackpressureOnReadyHandler<Object>(obs);
        Subscription sub = mock(Subscription.class);
        handler.onSubscribe(sub);
        
        handler.onError(new RuntimeException());
    }
    
    @Test
    public void onCompleteWontThrowAndPropagatesThrowableToOnError() {
        ClientCallStreamObserver<Object> obs = mock(ClientCallStreamObserver.class);
        doThrow(new IllegalStateException("won't be propagated to handler caller")).when(obs).onCompleted();
        ReactivePublisherBackpressureOnReadyHandler<Object> handler = new ReactivePublisherBackpressureOnReadyHandler<Object>(obs);
        Subscription sub = mock(Subscription.class);
        handler.onSubscribe(sub);
        
        handler.onComplete();
        verify(obs).onError(any(Throwable.class));
    }
    
    @Test
    public void onSubscribeCancelsSecondSubscription() {
        ClientCallStreamObserver<Object> obs = mock(ClientCallStreamObserver.class);
        ReactivePublisherBackpressureOnReadyHandler<Object> handler = new ReactivePublisherBackpressureOnReadyHandler<Object>(obs);
        Subscription sub1 = mock(Subscription.class);
        Subscription sub2 = mock(Subscription.class);

        handler.onSubscribe(sub1);
        handler.onSubscribe(sub2);
        
        verify(sub2).cancel();
    }
    
}
