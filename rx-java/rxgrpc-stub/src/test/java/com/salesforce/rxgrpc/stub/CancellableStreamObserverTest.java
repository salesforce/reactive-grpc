/*
 *  Copyright (c) 2017, salesforce.com, inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.rxgrpc.stub;

import io.grpc.Status;
import io.grpc.stub.ClientResponseObserver;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

@SuppressWarnings("ALL")
public class CancellableStreamObserverTest {
    @Test
    public void statusExceptionTriggersHandler() {
        ClientResponseObserver<Object, Object> delegate = mock(ClientResponseObserver.class);
        AtomicBoolean called = new AtomicBoolean(false);

        CancellableStreamObserver<Object, Object> observer = new CancellableStreamObserver<>(delegate, () -> called.set(true));

        observer.onError(Status.CANCELLED.asException());

        assertThat(called.get()).isTrue();
    }

    @Test
    public void statusRuntimeExceptionTriggersHandler() {
        ClientResponseObserver<Object, Object> delegate = mock(ClientResponseObserver.class);
        AtomicBoolean called = new AtomicBoolean(false);

        CancellableStreamObserver<Object, Object> observer = new CancellableStreamObserver<>(delegate, () -> called.set(true));

        observer.onError(Status.CANCELLED.asRuntimeException());

        assertThat(called.get()).isTrue();
    }

    @Test
    public void otherExceptionDoesNotTriggersHandler() {
        ClientResponseObserver<Object, Object> delegate = mock(ClientResponseObserver.class);
        AtomicBoolean called = new AtomicBoolean(false);

        CancellableStreamObserver<Object, Object> observer = new CancellableStreamObserver<>(delegate, () -> called.set(true));

        observer.onError(Status.INTERNAL.asRuntimeException());

        assertThat(called.get()).isFalse();
    }
}
