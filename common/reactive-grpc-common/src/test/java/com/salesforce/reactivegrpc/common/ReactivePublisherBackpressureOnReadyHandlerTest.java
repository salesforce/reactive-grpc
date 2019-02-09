///*
// *  Copyright (c) 2017, salesforce.com, inc.
// *  All rights reserved.
// *  Licensed under the BSD 3-Clause license.
// *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
// */
//
//package com.salesforce.reactivegrpc.common;
//
//import io.grpc.stub.ClientCallStreamObserver;
//import org.junit.Test;
//import org.reactivestreams.Subscription;
//
//import static org.mockito.Mockito.*;
//
//@SuppressWarnings("unchecked")
//public class ReactivePublisherBackpressureOnReadyHandlerTest {
//    @Test
//    public void runPrimesThePump() {
//        ClientCallStreamObserver<Object> obs = mock(ClientCallStreamObserver.class);
//        when(obs.isReady()).thenReturn(true);
//        AbstractReactivePublisherBackpressureOnReadyHandlerClientAnd<Object>
//                handler = new AbstractReactivePublisherBackpressureOnReadyHandlerClientAnd<Object>(obs);
//        Subscription sub = mock(Subscription.class);
//
//        handler.onSubscribe(sub);
//
//        handler.run();
//        verify(sub).request(1);
//    }
//
//    @Test
//    public void onNextKeepsPumpRunning() {
//        ClientCallStreamObserver<Object> obs = mock(ClientCallStreamObserver.class);
//        when(obs.isReady()).thenReturn(true);
//
//        AbstractReactivePublisherBackpressureOnReadyHandlerClientAnd<Object>
//                handler = new AbstractReactivePublisherBackpressureOnReadyHandlerClientAnd<Object>(obs);
//        Subscription sub = mock(Subscription.class);
//
//        handler.onSubscribe(sub);
//        // in case ClientCallStreamObserver is ready, we have to request 1
//        // otherwise, calling onNext violates RS spec (rule 1.9 -> https://github.com/reactive-streams/reactive-streams-jvm#1.9)
//        verify(sub).request(1);
//
//        Object obj = new Object();
//        handler.onNext(obj);
//
//        verify(obs).onNext(obj);
//        verify(sub, times(2)).request(1);
//    }
//
//    @Test
//    public void onNextStopsPump() {
//        ClientCallStreamObserver<Object> obs = mock(ClientCallStreamObserver.class);
//        when(obs.isReady()).thenReturn(false);
//
//        AbstractReactivePublisherBackpressureOnReadyHandlerClientAnd<Object>
//                handler = new AbstractReactivePublisherBackpressureOnReadyHandlerClientAnd<Object>(obs);
//        Subscription sub = mock(Subscription.class);
//
//        handler.onSubscribe(sub);
//
//        Object obj = new Object();
//        handler.onNext(obj);
//
//        verify(obs).onNext(obj);
//        verify(sub, never()).request(1);
//    }
//
//    @Test
//    public void exceptionInOnNextCancelsUpstreamSubscription() {
//        ClientCallStreamObserver<Object> obs = mock(ClientCallStreamObserver.class);
//        doThrow(new IllegalStateException("won't be propagated to handler caller")).when(obs).onNext(any());
//        AbstractReactivePublisherBackpressureOnReadyHandlerClientAnd<Object>
//                handler = new AbstractReactivePublisherBackpressureOnReadyHandlerClientAnd<Object>(obs);
//        Subscription sub = mock(Subscription.class);
//        handler.onSubscribe(sub);
//
//        handler.onNext(new Object());
//        verify(obs).cancel(anyString(), any(Throwable.class));
//        verify(obs).onError(any(Throwable.class));
//    }
//
//    @Test
//    public void exceptionInOnOnErrorCancelsUpstreamSubscription() {
//        ClientCallStreamObserver<Object> obs = mock(ClientCallStreamObserver.class);
//        doThrow(new IllegalStateException("won't be propagated to handler caller")).when(obs).onError(any(Throwable.class));
//        AbstractReactivePublisherBackpressureOnReadyHandlerClientAnd<Object>
//                handler = new AbstractReactivePublisherBackpressureOnReadyHandlerClientAnd<Object>(obs);
//        Subscription sub = mock(Subscription.class);
//        handler.onSubscribe(sub);
//
//        handler.onError(new RuntimeException());
//        verify(obs).cancel(anyString(), any(Throwable.class));
//    }
//
//    @Test
//    public void exceptionInOnCompleteCancelsUpstreamSubscription() {
//        ClientCallStreamObserver<Object> obs = mock(ClientCallStreamObserver.class);
//        doThrow(new IllegalStateException("won't be propagated to handler caller")).when(obs).onCompleted();
//        AbstractReactivePublisherBackpressureOnReadyHandlerClientAnd<Object>
//                handler = new AbstractReactivePublisherBackpressureOnReadyHandlerClientAnd<Object>(obs);
//        Subscription sub = mock(Subscription.class);
//        handler.onSubscribe(sub);
//
//        handler.onComplete();
//        verify(obs).cancel(anyString(), any(Throwable.class));
//        verify(obs).onError(any(Throwable.class));
//    }
//
//    @Test
//    public void onSubscribeCancelsSecondSubscription() {
//        ClientCallStreamObserver<Object> obs = mock(ClientCallStreamObserver.class);
//        AbstractReactivePublisherBackpressureOnReadyHandlerClientAnd<Object>
//                handler = new AbstractReactivePublisherBackpressureOnReadyHandlerClientAnd<Object>(obs);
//        Subscription sub1 = mock(Subscription.class);
//        Subscription sub2 = mock(Subscription.class);
//
//        handler.onSubscribe(sub1);
//        handler.onSubscribe(sub2);
//
//        verify(sub2).cancel();
//    }
//
//}
