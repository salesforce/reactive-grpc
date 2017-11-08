/*
 *  Copyright (c) 2017, salesforce.com, inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.rxgrpc.stub;

import io.grpc.stub.CallStreamObserver;
import io.grpc.stub.ClientCallStreamObserver;
import org.junit.Test;
import org.reactivestreams.Subscription;

import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
public class RxFlowableBackpressureOnReadyHandlerTest {
    @Test
    public void runPrimesThePump() {
        ClientCallStreamObserver<Object> obs = mock(ClientCallStreamObserver.class);
        RxFlowableBackpressureOnReadyHandler<Object> handler = new RxFlowableBackpressureOnReadyHandler<>(obs);
        Subscription sub = mock(Subscription.class);

        handler.onSubscribe(sub);

        handler.run();
        verify(sub).request(1);
    }

    @Test
    public void onNextKeepsPumpRunning() {
        ClientCallStreamObserver<Object> obs = mock(ClientCallStreamObserver.class);
        when(obs.isReady()).thenReturn(true);

        RxFlowableBackpressureOnReadyHandler<Object> handler = new RxFlowableBackpressureOnReadyHandler<>(obs);
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

        RxFlowableBackpressureOnReadyHandler<Object> handler = new RxFlowableBackpressureOnReadyHandler<>(obs);
        Subscription sub = mock(Subscription.class);

        handler.onSubscribe(sub);

        Object obj = new Object();
        handler.onNext(obj);

        verify(obs).onNext(obj);
        verify(sub, never()).request(1);
    }
}
