/*  Copyright (c) 2019, Salesforce.com, Inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */


package com.salesforce.reactivegrpc.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import io.grpc.Status;
import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.CallStreamObserver;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subscribers.TestSubscriber;

@SuppressWarnings("unchecked")
public class CancellableStreamObserverRx3Test {
	@Test
	public void statusExceptionTriggersHandler() throws InterruptedException {
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
		test.await(10, TimeUnit.SECONDS);
		test.assertError(exception);

        assertThat(called.get()).isTrue();
        assertThat(observer.outputFused).isFalse();
    }

	@Test
	public void statusRuntimeExceptionTriggersHandler() throws InterruptedException {
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

		test.await(30, TimeUnit.SECONDS);
		test.assertError(exception);

        assertThat(called.get()).isTrue();
        assertThat(observer.outputFused).isFalse();
    }

    @Test
    public void statusExceptionTriggersHandlerFuseable() throws InterruptedException {
        CallStreamObserver delegate = mock(CallStreamObserver.class);
        final AtomicBoolean called = new AtomicBoolean(false);

        AbstractStreamObserverAndPublisher observer = new TestStreamObserverAndPublisherWithFusionRx3(new ArrayBlockingQueue(1), null, new Runnable() {
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

		test.await(30, TimeUnit.SECONDS);
		test.assertError(exception);

        assertThat(called.get()).isTrue();
        assertThat(observer.outputFused).isTrue();
    }

    @Test
    public void statusRuntimeExceptionTriggersHandlerFuseable() throws InterruptedException {
        CallStreamObserver delegate = mock(CallStreamObserver.class);
        final AtomicBoolean called = new AtomicBoolean(false);

        AbstractStreamObserverAndPublisher observer = new TestStreamObserverAndPublisherWithFusionRx3(new ArrayBlockingQueue(1), null, new Runnable() {
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

		test.await(30, TimeUnit.SECONDS);
		test.assertError(exception);

        assertThat(called.get()).isTrue();
        assertThat(observer.outputFused).isTrue();
    }

}
