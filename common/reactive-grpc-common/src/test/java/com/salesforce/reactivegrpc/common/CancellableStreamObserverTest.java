/*  Copyright (c) 2019, Salesforce.com, Inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */


package com.salesforce.reactivegrpc.common;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import io.grpc.Status;
import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.CallStreamObserver;
import io.reactivex.Flowable;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subscribers.TestSubscriber;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;


@SuppressWarnings("unchecked")
public class CancellableStreamObserverTest {
    @Test
    public void statusExceptionTriggersHandler() {
        CallStreamObserver delegate = mock(CallStreamObserver.class);
        final AtomicBoolean called = new AtomicBoolean(false);

        AbstractStreamObserverAndPublisher observer = new AbstractStreamObserverAndPublisher(new ArrayBlockingQueue(1), null, new Runnable() {
            @Override
            public void run() {
                called.set(true);
            }
        }) { };

        observer.onSubscribe(delegate);

        TestSubscriber test = Flowable.fromPublisher(observer)
                                      .test();

        StatusException exception = Status.CANCELLED.asException();
        observer.onError(exception);

        test.awaitTerminalEvent();
        test.assertError(exception);

        assertThat(called.get()).isTrue();
        assertThat(observer.outputFused).isFalse();
    }

    @Test
    public void statusRuntimeExceptionTriggersHandler() {
        CallStreamObserver delegate = mock(CallStreamObserver.class);
        final AtomicBoolean called = new AtomicBoolean(false);

        AbstractStreamObserverAndPublisher observer = new AbstractStreamObserverAndPublisher(new ArrayBlockingQueue(1), null, new Runnable() {
            @Override
            public void run() {
                called.set(true);
            }
        }) { };

        observer.onSubscribe(delegate);

        TestSubscriber test = Flowable.fromPublisher(observer)
                                      .test();

        StatusRuntimeException exception = Status.CANCELLED.asRuntimeException();
        observer.onError(exception);

        test.awaitTerminalEvent();
        test.assertError(exception);

        assertThat(called.get()).isTrue();
        assertThat(observer.outputFused).isFalse();
    }

    @Test
    public void statusExceptionTriggersHandlerFuseable() {
        CallStreamObserver delegate = mock(CallStreamObserver.class);
        final AtomicBoolean called = new AtomicBoolean(false);

        AbstractStreamObserverAndPublisher observer = new TestStreamObserverAndPublisherWithFusion(new ArrayBlockingQueue(1), null, new Runnable() {
            @Override
            public void run() {
                called.set(true);
            }
        });

        observer.onSubscribe(delegate);

        TestSubscriber test = Flowable.fromPublisher(observer)
                                      .observeOn(Schedulers.trampoline())
                                      .test();

        StatusException exception = Status.CANCELLED.asException();
        observer.onError(exception);

        test.awaitTerminalEvent();
        test.assertError(exception);

        assertThat(called.get()).isTrue();
        assertThat(observer.outputFused).isTrue();
    }

    @Test
    public void statusRuntimeExceptionTriggersHandlerFuseable() {
        CallStreamObserver delegate = mock(CallStreamObserver.class);
        final AtomicBoolean called = new AtomicBoolean(false);

        AbstractStreamObserverAndPublisher observer = new TestStreamObserverAndPublisherWithFusion(new ArrayBlockingQueue(1), null, new Runnable() {
            @Override
            public void run() {
                called.set(true);
            }
        });

        observer.onSubscribe(delegate);

        TestSubscriber test = Flowable.fromPublisher(observer)
                                      .observeOn(Schedulers.trampoline())
                                      .test();

        StatusRuntimeException exception = Status.CANCELLED.asRuntimeException();
        observer.onError(exception);

        test.awaitTerminalEvent();
        test.assertError(exception);

        assertThat(called.get()).isTrue();
        assertThat(observer.outputFused).isTrue();
    }

}
