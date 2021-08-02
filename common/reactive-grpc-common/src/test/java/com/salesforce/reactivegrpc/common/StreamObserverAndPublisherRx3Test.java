/*
 *  Copyright (c) 2019, Salesforce.com, Inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */
package com.salesforce.reactivegrpc.common;

import static com.salesforce.reactivegrpc.common.AbstractStreamObserverAndPublisher.DEFAULT_CHUNK_SIZE;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;

import io.grpc.stub.CallStreamObserver;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.internal.fuseable.QueueFuseable;
import io.reactivex.rxjava3.internal.fuseable.QueueSubscription;
import io.reactivex.rxjava3.plugins.RxJavaPlugins;
import io.reactivex.rxjava3.subscribers.TestSubscriber;

public class StreamObserverAndPublisherRx3Test {

    static final int PART_OF_CHUNK = DEFAULT_CHUNK_SIZE * 2 / 3;

    static final ExecutorService executorService =
        Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    static final ExecutorService requestExecutorService =
        Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

    final Queue<Throwable> unhandledThrowable = new ConcurrentLinkedQueue<Throwable>();


    @BeforeEach
    public void setUp() {
        RxJavaPlugins.setErrorHandler(new io.reactivex.rxjava3.functions.Consumer<Throwable>() {
            @Override
            public void accept(Throwable throwable) {
                unhandledThrowable.offer(throwable);
            }
        });
    }

    @RepeatedTest(2)
    public void multithreadingRegularTest() throws InterruptedException {
        TestStreamObserverAndPublisher<Integer> processor =
            new TestStreamObserverAndPublisher<Integer>(null);
        int countPerThread = 10000;
        TestCallStreamObserverRx3Producer observer =
            new TestCallStreamObserverRx3Producer(executorService, processor, countPerThread);
        processor.onSubscribe(observer);
        final TestSubscriber<Integer> testSubscriber = Flowable
            .fromPublisher(processor)
            .test(0);

        for (int i = 0; i < countPerThread; i++) {
            requestExecutorService.execute(new Runnable() {
                @Override
                public void run() {
                    LockSupport.parkNanos(10);
                    testSubscriber.request(1);
                }
            });
        }

        Assertions.assertThat(testSubscriber.await(1, TimeUnit.MINUTES)).isTrue();
        testSubscriber.assertValueCount(countPerThread);

        Assertions.assertThat(processor.outputFused).isFalse();
        int prop1 = (countPerThread / PART_OF_CHUNK) - (DEFAULT_CHUNK_SIZE / PART_OF_CHUNK) + 1;
        Assertions.assertThat(observer.requestsQueue.size()).isBetween(prop1, prop1 + 1);

        Integer i = observer.requestsQueue.poll();

        Assertions.assertThat(i).isEqualTo(DEFAULT_CHUNK_SIZE);

        while ((i = observer.requestsQueue.poll()) != null) {
            Assertions.assertThat(i).isEqualTo(PART_OF_CHUNK);
        }
    }

    //@RepeatedTest(2)
    public void multithreadingFussedTest() throws InterruptedException {

        TestStreamObserverAndPublisher<Integer> processor =
            new TestStreamObserverAndPublisher<Integer>(null);
        int countPerThread = 1000000;
        TestCallStreamObserverRx3Producer observer =
            new TestCallStreamObserverRx3Producer(executorService, processor, countPerThread);
        processor.onSubscribe(observer);
        final TestSubscriber<Integer> testSubscriber = Flowable
            .fromPublisher(processor)
            .subscribeWith(new TestSubscriber<Integer>());

        for (int i = 0; i < countPerThread; i++) {
            requestExecutorService.execute(new Runnable() {
                @Override
                public void run() {
                    LockSupport.parkNanos(10);
                    testSubscriber.request(1);
                }
            });
        }

        Assertions.assertThat(testSubscriber.await(1, TimeUnit.MINUTES)).isTrue();
        testSubscriber.assertValueCount(countPerThread);

        Assertions.assertThat(processor.outputFused).isTrue();
        Assertions.assertThat(observer.requestsQueue.size()).isBetween((countPerThread - DEFAULT_CHUNK_SIZE) / PART_OF_CHUNK + 1, (countPerThread - DEFAULT_CHUNK_SIZE) / PART_OF_CHUNK + 2);

        Integer i = observer.requestsQueue.poll();

        Assertions.assertThat(i).isEqualTo(DEFAULT_CHUNK_SIZE);

        while ((i = observer.requestsQueue.poll()) != null) {
            Assertions.assertThat(i).isEqualTo(PART_OF_CHUNK);
        }
    }

	public class TestRx3Subscriber extends TestSubscriber<Integer> {

		public TestRx3Subscriber(int i) {
			super(i);
		}

		public int errorCount() {
			return errors.size();
		}

		public final boolean awaitTerminalEvent() {
			try {
				await();
				return true;
			} catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
				return false;
			}
		}

		public final TestRx3Subscriber assertError(Class<? extends Throwable> clazz, String message) {
			super.assertError(clazz);
			int s = errors.size();
			if (s == 0) {
				throw fail("No errors");
			} else if (s == 1) {
				Throwable e = errors.get(0);
				String errorMessage = e.getMessage();
				if (!(message.equals(errorMessage))) {
					throw fail("Error message differs; exptected: " + message + " but was: " + errorMessage);
				}
			} else {
				throw fail("Multiple errors");
			}
			return this;
		}

	}

	@RepeatedTest(2)
	public void shouldSupportOnlySingleSubscriberTest() throws InterruptedException {
		for (int i = 0; i < 1000; i++) {
			final TestRx3Subscriber downstream1 = new TestRx3Subscriber(0);
			final TestRx3Subscriber downstream2 = new TestRx3Subscriber(0);
			final TestStreamObserverAndPublisher<Integer> processor = new TestStreamObserverAndPublisher<Integer>(null);
			final CountDownLatch latch = new CountDownLatch(1);
			executorService.execute(new Runnable() {
				@Override
				public void run() {
					latch.countDown();
					processor.subscribe(downstream1);
					processor.onCompleted();
				}
			});
			latch.await();
			processor.subscribe(downstream2);
			processor.onCompleted();

			downstream1.awaitTerminalEvent();
			downstream2.awaitTerminalEvent();

			if (downstream1.errorCount() > 0) {
				downstream1.assertError(IllegalStateException.class,
						"TestStreamObserverAndPublisher allows only a single Subscriber");
			} else {
				downstream2.assertError(IllegalStateException.class,
						"TestStreamObserverAndPublisher allows only a single Subscriber");
			}
		}
	}

    @RepeatedTest(2)
    public void shouldSupportOnlySingleSubscriptionTest() throws InterruptedException {
        for (int i = 0; i < 1000; i++) {
            final AtomicReference<Throwable> throwableAtomicReference = new AtomicReference<Throwable>();
            final TestStreamObserverAndPublisher<Integer> processor = new TestStreamObserverAndPublisher<Integer>(null);
            final TestCallStreamObserverRx3Producer upstream = new TestCallStreamObserverRx3Producer(executorService, processor, 100000000);
            final CountDownLatch latch = new CountDownLatch(1);
            final CountDownLatch throwingLatch = new CountDownLatch(1);
            executorService.execute(new Runnable() {
                @Override
                public void run() {
                    latch.countDown();
                    try {
                        processor.onSubscribe(upstream);

                    } catch (Throwable t) {
                        Assertions.assertThat(throwableAtomicReference.getAndSet(t)).isNull();
                        throwingLatch.countDown();
                    }
                }
            });
            latch.await();
            try {
                processor.onSubscribe(upstream);
            } catch (Throwable t) {
                Assertions.assertThat(throwableAtomicReference.getAndSet(t)).isNull();
                throwingLatch.countDown();
            }

            throwingLatch.await();

            Assertions.assertThat(upstream.requestsQueue).isEmpty();
            Assertions.assertThat(throwableAtomicReference.get())
                      .isExactlyInstanceOf(IllegalStateException.class)
                      .hasMessage("TestStreamObserverAndPublisher supports only a single subscription");
        }
    }

    @RepeatedTest(2)
    public void shouldSupportOnlySinglePrefetchTest() throws InterruptedException {
        for (int i = 0; i < 10; i++) {
            final TestSubscriber<Integer> downstream = new TestSubscriber<Integer>(0);
            final TestStreamObserverAndPublisher<Integer> processor = new TestStreamObserverAndPublisher<Integer>(null);
            final TestCallStreamObserverRx3Producer upstream = new TestCallStreamObserverRx3Producer(executorService, processor, 100000000);
            processor.onSubscribe(upstream);
            upstream.requested = 1; // prevents running elements sending but allows
            // checking how much elements requested at first
            processor.subscribe(downstream);

            for (int j = 0; j < 1000; j++) {
                final CountDownLatch latch = new CountDownLatch(1);
                executorService.execute(new Runnable() {
                    @Override
                    public void run() {
                        latch.countDown();
                        downstream.request(1);
                    }
                });
                latch.await();
                downstream.request(1);
            }

            Assertions.assertThat(upstream.requestsQueue)
                      .hasSize(1)
                      .containsOnly(DEFAULT_CHUNK_SIZE);
        }
    }

    static class FussedTestSubscriber<T> extends TestSubscriber<T> {
        public FussedTestSubscriber() {
            super(0);
            //initialFusionMode = QueueSubscription.ANY;
        }
    }

    static class TestStreamObserverAndPublisher<T>
        extends AbstractStreamObserverAndPublisher<T>
        implements QueueSubscription<T> {

        public TestStreamObserverAndPublisher(
            Consumer<CallStreamObserver<?>> onSubscribe) {
            super(new ConcurrentLinkedQueue<T>(), onSubscribe);
        }

        @Override
        public int requestFusion(int requestedMode) {
            if ((requestedMode & QueueFuseable.ASYNC) != 0) {
                outputFused = true;
                return QueueFuseable.ASYNC;
            }
            return QueueFuseable.NONE;
        }

        @Override
        public boolean offer(T t, T t1) {
            throw new UnsupportedOperationException();
        }
    }
}
