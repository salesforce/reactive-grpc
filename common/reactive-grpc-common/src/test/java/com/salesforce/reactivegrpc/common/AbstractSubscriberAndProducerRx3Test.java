/*  Copyright (c) 2019, Salesforce.com, Inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */
package com.salesforce.reactivegrpc.common;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Tag;
import org.reactivestreams.Subscription;

import io.grpc.StatusException;
import io.grpc.stub.CallStreamObserver;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.CompletableSource;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.functions.Action;
import io.reactivex.rxjava3.functions.Consumer;
import io.reactivex.rxjava3.functions.Function;
import io.reactivex.rxjava3.functions.LongConsumer;
import io.reactivex.rxjava3.plugins.RxJavaPlugins;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class AbstractSubscriberAndProducerRx3Test {

    private final Queue<Throwable> unhandledThrowable = new ConcurrentLinkedQueue<Throwable>();

    private static final ExecutorService executorService  =
        Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

	@BeforeEach
	public void setUp() {
		RxJavaPlugins.setErrorHandler(new Consumer<Throwable>() {
			@Override
			public void accept(Throwable throwable) {
				unhandledThrowable.offer(throwable);
			}
		});
	}

    @RepeatedTest(2)
    public void shouldSupportOnlySingleSubscribersTest() throws InterruptedException {
        final TestCallStreamObserver<Integer> downstream = new TestCallStreamObserver<Integer>(executorService);
        for (int i = 0; i < 1000; i++) {
            final AtomicReference<Throwable> throwableAtomicReference = new AtomicReference<Throwable>();
            final TestSubscriberProducerRx3<Integer> producer = new TestSubscriberProducerRx3<Integer>();
            final CountDownLatch latch = new CountDownLatch(1);
            final CountDownLatch throwingLatch = new CountDownLatch(1);
            executorService.execute(new Runnable() {
                @Override
                public void run() {
                    latch.countDown();
                    try {
                        producer.subscribe(downstream);

                    } catch (Throwable t) {
                        Assertions.assertThat(throwableAtomicReference.getAndSet(t)).isNull();
                        throwingLatch.countDown();
                    }
                }
            });
            latch.await();
            try {
                producer.subscribe(downstream);
            } catch (Throwable t) {
                Assertions.assertThat(throwableAtomicReference.getAndSet(t)).isNull();
                throwingLatch.countDown();
            }

            throwingLatch.await();

            Assertions.assertThat(throwableAtomicReference.get())
                      .isExactlyInstanceOf(IllegalStateException.class)
                      .hasMessage("TestSubscriberProducerRx3 does not support multiple subscribers");
        }
    }

    @RepeatedTest(2)
    public void shouldSupportOnlySingleSubscriptionTest() throws InterruptedException {
        for (int i = 0; i < 1000; i++) {
            final CountDownLatch cancelLatch = new CountDownLatch(1);
            final Subscription upstream = new Subscription() {
                AtomicBoolean once = new AtomicBoolean();
                @Override
                public void request(long l) { }

                @Override
                public void cancel() {
                    Assertions.assertThat(once.getAndSet(true)).isFalse();
                    cancelLatch.countDown();
                }
            };
            final TestSubscriberProducerRx3 producer = new TestSubscriberProducerRx3();
            final CountDownLatch latch = new CountDownLatch(1);
            executorService.execute(new Runnable() {
                @Override
                public void run() {
                    latch.countDown();
                    producer.onSubscribe(upstream);
                }
            });
            latch.await();
            producer.onSubscribe(upstream);

            Assertions.assertThat(cancelLatch.await(1, TimeUnit.MINUTES)).isTrue();
        }
    }

    @RepeatedTest(2)
    public void regularModeWithRacingTest() {
        final AtomicLong requested = new AtomicLong();
        final AtomicBoolean pingPing = new AtomicBoolean();
        List<Integer> integers = Flowable.range(0, 10000000)
                                         .toList()
                                         .blockingGet();

        TestSubscriberProducerRx3<Integer> producer = Flowable.fromIterable(integers)
                                                           .hide()
                                                           .subscribeOn(Schedulers.io())
                                                           .observeOn(Schedulers.io(), true)
                                                           .doOnRequest(new LongConsumer() {
                                                               @Override
                                                               public void accept(long r) {
                                                                   requested.addAndGet(r);
                                                                   boolean state = pingPing.getAndSet(true);
                                                                   Assertions.assertThat(state).isFalse();
                                                               }
                                                           })
                                                           .doOnNext(new Consumer<Integer>() {
                                                               @Override
                                                               public void accept(Integer integer) {
                                                                   boolean state = pingPing.getAndSet(false);
                                                                   Assertions.assertThat(state).isTrue();
                                                               }
                                                           })
                                                           .hide()
                                                           .subscribeWith(new TestSubscriberProducerRx3<Integer>());

        TestCallStreamObserver<Integer> downstream = new TestCallStreamObserver<Integer>(
            executorService);
        producer.subscribe(downstream);

        racePauseResuming(downstream, 10000);

        Assertions.assertThat(downstream.awaitTerminal(1, TimeUnit.MINUTES)).isTrue();
        Assertions.assertThat(downstream.e).isNull();
        Assertions.assertThat(producer).hasFieldOrPropertyWithValue("sourceMode", 0);
        Assertions.assertThat(unhandledThrowable).isEmpty();
        Assertions.assertThat(requested.get()).isEqualTo(10000000 + 1);
        Assertions.assertThat(downstream.collected)
                  .isEqualTo(integers);
    }

    @Tag("unstable")
    @RepeatedTest(2)
    public void asyncModeWithRacingTest() {
        List<Integer> integers = Flowable.range(0, 10000000)
                                         .toList()
                                         .blockingGet();

        TestSubscriberProducerRx3<Integer> producer = Flowable.fromIterable(integers)
                                                           .hide()
                                                           .subscribeOn(Schedulers.io())
                                                           .observeOn(Schedulers.io(), true)
                                                           .subscribeWith(new TestSubscriberProducerRx3<Integer>());

        TestCallStreamObserver<Integer> downstream = new TestCallStreamObserver<Integer>(executorService);
        producer.subscribe(downstream);

        racePauseResuming(downstream, 10000);

        Assertions.assertThat(downstream.awaitTerminal(1, TimeUnit.MINUTES)).isTrue();
        Assertions.assertThat(downstream.e).isNull();
        Assertions.assertThat(producer).hasFieldOrPropertyWithValue("sourceMode", 2);
        Assertions.assertThat(downstream.collected)
                  .isEqualTo(integers);
        Assertions.assertThat(unhandledThrowable).isEmpty();
    }

    @RepeatedTest(2)
    public void syncModeWithRacingTest() throws InterruptedException {
        List<Integer> integers = Flowable.range(0, 10000000)
                                         .toList()
                                         .blockingGet();

        final CountDownLatch startedLatch = new CountDownLatch(1);
        final TestSubscriberProducerRx3<Integer> producer = Flowable.fromIterable(integers)
                                                           .subscribeWith(new TestSubscriberProducerRx3<Integer>());

        final TestCallStreamObserver<Integer> downstream =
            new TestCallStreamObserver<Integer>(executorService);
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                producer.subscribe(downstream);
                startedLatch.countDown();
            }
        });

        startedLatch.await();

        racePauseResuming(downstream, 10000);

        Assertions.assertThat(downstream.awaitTerminal(1, TimeUnit.MINUTES)).isTrue();
        Assertions.assertThat(downstream.e).isNull();
        Assertions.assertThat(producer).hasFieldOrPropertyWithValue("sourceMode", 1);
        Assertions.assertThat(downstream.collected)
                  .isEqualTo(integers);
        Assertions.assertThat(unhandledThrowable).isEmpty();
    }

    @RepeatedTest(2)
    public void regularModeWithRacingAndOnErrorTest() {
        final AtomicBoolean pingPing = new AtomicBoolean();
        List<Integer> integers = Flowable.range(0, 10000000)
                                         .toList()
                                         .blockingGet();

        ArrayList<Integer> copy = new ArrayList<Integer>(integers);

        copy.add(null);

        TestSubscriberProducerRx3<Integer> producer = Flowable.fromIterable(copy)
                                                           .hide()
                                                           .subscribeOn(Schedulers.io())
                                                           .observeOn(Schedulers.io(), true)
                                                           .doOnRequest(new LongConsumer() {
                                                               @Override
                                                               public void accept(long r) {
                                                                   boolean state = pingPing.getAndSet(true);
                                                                   Assertions.assertThat(state).isFalse();
                                                               }
                                                           })
                                                           .doOnNext(new Consumer<Integer>() {
                                                               @Override
                                                               public void accept(Integer integer) {
                                                                   boolean state = pingPing.getAndSet(false);
                                                                   Assertions.assertThat(state).isTrue();
                                                               }
                                                           })
                                                           .hide()
                                                           .subscribeWith(new TestSubscriberProducerRx3<Integer>());

        TestCallStreamObserver<Integer> downstream = new TestCallStreamObserver<Integer>(
            executorService);
        producer.subscribe(downstream);

        racePauseResuming(downstream, 10000);

        Assertions.assertThat(downstream.awaitTerminal(1, TimeUnit.MINUTES)).isTrue();
        Assertions.assertThat(downstream.e)
                  .isExactlyInstanceOf(StatusException.class)
                  .hasCauseInstanceOf(NullPointerException.class);
        Assertions.assertThat(producer).hasFieldOrPropertyWithValue("sourceMode", 0);
        Assertions.assertThat(unhandledThrowable).isEmpty();
        Assertions.assertThat(downstream.collected)
                  .isEqualTo(integers);
    }

    @Tag("unstable")
    @RepeatedTest(2)
    public void asyncModeWithRacingAndOnErrorTest() {

        List<Integer> integers = Flowable.range(0, 10000000)
                                         .toList()
                                         .blockingGet();

        ArrayList<Integer> copy = new ArrayList<Integer>(integers);

        copy.add(null);

        TestSubscriberProducerRx3<Integer> producer = Flowable.fromIterable(copy)
                                                           .hide()
                                                           .observeOn(Schedulers.computation(), true)
                                                           .subscribeWith(new TestSubscriberProducerRx3<Integer>());

        TestCallStreamObserver<Integer> downstream = new TestCallStreamObserver<Integer>(
            executorService);
        producer.subscribe(downstream);

        racePauseResuming(downstream, 10000);

        Assertions.assertThat(downstream.awaitTerminal(1, TimeUnit.MINUTES)).isTrue();
        Assertions.assertThat(downstream.e)
                  .isExactlyInstanceOf(StatusException.class)
                  .hasCauseInstanceOf(NullPointerException.class);
        Assertions.assertThat(producer).hasFieldOrPropertyWithValue("sourceMode", 2);
        Assertions.assertThat(downstream.collected)
                  .isEqualTo(integers);
        Assertions.assertThat(unhandledThrowable).isEmpty();
    }

    @Tag("unstable")
    @RepeatedTest(2)
    public void asyncModeWithRacingAndErrorTest() throws InterruptedException {
        final CountDownLatch cancellationLatch = new CountDownLatch(1);
        List<Integer> integers = Flowable.range(0, 10000000)
                                         .toList()
                                         .blockingGet();

        TestSubscriberProducerRx3<Integer> producer = Flowable.fromIterable(integers)
                                                           .doOnCancel(new Action() {
                                                               @Override
                                                               public void run() {
                                                                   cancellationLatch.countDown();
                                                               }
                                                           })
                                                           .hide()
                                                           .subscribeOn(Schedulers.io())
                                                           .observeOn(Schedulers.io(), true)
                                                           .map(new Function<Integer, Integer>() {
                                                               @Override
                                                               public Integer apply(Integer i) {
                                                                   if (i == 9999999) {
                                                                       throw new NullPointerException();
                                                                   }

                                                                   return i;
                                                               }
                                                           })
                                                           .subscribeWith(new TestSubscriberProducerRx3<Integer>());

        TestCallStreamObserver<Integer> downstream = new TestCallStreamObserver<Integer>(
            executorService);
        producer.subscribe(downstream);

        racePauseResuming(downstream, 10000);

        Assertions.assertThat(downstream.awaitTerminal(10, TimeUnit.SECONDS)).isTrue();
        Assertions.assertThat(cancellationLatch.await(10, TimeUnit.SECONDS)).isTrue();
        Assertions.assertThat(downstream.e)
                  .isExactlyInstanceOf(StatusException.class)
                  .hasCauseInstanceOf(NullPointerException.class);
        Assertions.assertThat(producer).hasFieldOrPropertyWithValue("sourceMode", 2);
        Assertions.assertThat(downstream.collected)
                  .hasSize(integers.size() - 1)
                  .isEqualTo(integers.subList(0, integers.size() - 1));
        Assertions.assertThat(unhandledThrowable).isEmpty();
    }

    @Tag("unstable")
    @RepeatedTest(2)
    public void syncModeWithRacingAndErrorTest() throws InterruptedException {
        final CountDownLatch cancellationLatch = new CountDownLatch(1);
        List<Integer> integers = Flowable.range(0, 1000)
                                         .toList()
                                         .blockingGet();

        final CountDownLatch startedLatch = new CountDownLatch(1);
        final TestSubscriberProducerRx3<Integer> producer = Flowable.fromIterable(new SlowingIterable<Integer>(integers))
                                                                 .map(new Function<Integer, Integer>() {
                                                                     @Override
                                                                     public Integer apply(Integer i) {
                                                                         if (i == 999) {
                                                                             throw new NullPointerException();
                                                                         }

                                                                         return i;
                                                                     }
                                                                 })
                                                                 .subscribeWith(new TestSubscriberProducerRx3<Integer>() {
                                                                     @Override
                                                                     public void cancel() {
                                                                         super.cancel();
                                                                         cancellationLatch.countDown();
                                                                     }
                                                                 });

        final TestCallStreamObserver<Integer> downstream =
            new TestCallStreamObserver<Integer>(executorService);
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                producer.subscribe(downstream);
                startedLatch.countDown();
            }
        });

        startedLatch.await();

        racePauseResuming(downstream, 100);

        Assertions.assertThat(downstream.awaitTerminal(1, TimeUnit.MINUTES)).isTrue();
        Assertions.assertThat(cancellationLatch.await(1, TimeUnit.MINUTES)).isTrue();
        Assertions.assertThat(downstream.e)
                  .isExactlyInstanceOf(StatusException.class)
                  .hasCauseInstanceOf(NullPointerException.class);
        Assertions.assertThat(producer).hasFieldOrPropertyWithValue("sourceMode", 1);
        Assertions.assertThat(downstream.collected)
                  .hasSize(integers.size() - 1)
                  .isEqualTo(integers.subList(0, integers.size() - 1));
        Assertions.assertThat(unhandledThrowable).isEmpty();
    }

    @RepeatedTest(2)
    public void regularModeWithRacingAndOnErrorOverOnNextTest()
        throws InterruptedException {
        final AtomicLong requested = new AtomicLong();
        final AtomicLong produced = new AtomicLong();
        final CountDownLatch cancellationLatch = new CountDownLatch(1);
        List<Integer> integers = Flowable.range(0, 10000000)
                                         .toList()
                                         .blockingGet();

        TestSubscriberProducerRx3<Integer> producer = Flowable.fromIterable(integers)
                                                           .doOnCancel(new Action() {
                                                               @Override
                                                               public void run() {
                                                                   cancellationLatch.countDown();
                                                               }
                                                           })
                                                           .hide()
                                                           .subscribeOn(Schedulers.io())
                                                           .observeOn(Schedulers.io(), true)
                                                           .doOnNext(new Consumer<Integer>() {
                                                               @Override
                                                               public void accept(Integer __) {
                                                                   produced.getAndIncrement();
                                                               }
                                                           })
                                                           .doOnRequest(new LongConsumer() {
                                                               @Override
                                                               public void accept(long r) {
                                                                   requested.addAndGet(r);
                                                               }
                                                           })
                                                           .hide()
                                                           .subscribeWith(new TestSubscriberProducerRx3<Integer>());

        TestCallStreamObserver<Integer> downstream = new TestCallStreamObserver<Integer>(
            executorService);
        producer.subscribe(downstream);

        racePauseResuming(downstream, 100);

        downstream.throwOnNext();

        Assertions.assertThat(downstream.awaitTerminal(1, TimeUnit.MINUTES)).isTrue();
        Assertions.assertThat(cancellationLatch.await(1, TimeUnit.MINUTES)).isTrue();
        Assertions.assertThat(downstream.e)
                  .isExactlyInstanceOf(StatusException.class)
                  .hasCauseInstanceOf(OnNextTestException.class);
        Assertions.assertThat(producer).hasFieldOrPropertyWithValue("sourceMode", 0);
        Assertions.assertThat(requested.get()).isEqualTo(produced.get());
        Assertions.assertThat(downstream.collected)
                  .isSubsetOf(integers);
        Assertions.assertThat(unhandledThrowable).isEmpty();
    }

    @Tag("unstable")
    @RepeatedTest(2)
    public void asyncModeWithRacingAndOnErrorOverOnNextTest()
        throws InterruptedException {
        final CountDownLatch cancellationLatch = new CountDownLatch(1);
        List<Integer> integers = Flowable.range(0, 10000000)
                                         .toList()
                                         .blockingGet();

        TestSubscriberProducerRx3<Integer> producer = Flowable.fromIterable(integers)
                                                           .doOnCancel(new Action() {
                                                               @Override
                                                               public void run() {
                                                                   cancellationLatch.countDown();
                                                               }
                                                           })
                                                           .hide()
                                                           .subscribeOn(Schedulers.io())
                                                           .observeOn(Schedulers.io(), true)
                                                           .subscribeWith(new TestSubscriberProducerRx3<Integer>());

        TestCallStreamObserver<Integer> downstream = new TestCallStreamObserver<Integer>(
            executorService);
        producer.subscribe(downstream);

        racePauseResuming(downstream, 100);

        downstream.throwOnNext();

        Assertions.assertThat(downstream.awaitTerminal(1, TimeUnit.MINUTES)).isTrue();
        Assertions.assertThat(cancellationLatch.await(1, TimeUnit.MINUTES)).isTrue();
        Assertions.assertThat(downstream.e)
                  .isExactlyInstanceOf(StatusException.class)
                  .hasCauseInstanceOf(OnNextTestException.class);
        Assertions.assertThat(producer).hasFieldOrPropertyWithValue("sourceMode", 2);
        Assertions.assertThat(downstream.collected)
                  .isSubsetOf(integers);
        Assertions.assertThat(unhandledThrowable).isEmpty();
    }

    @RepeatedTest(2)
    public void syncModeWithRacingAndOnErrorOverOnNextTest() throws InterruptedException {
        final CountDownLatch cancellationLatch = new CountDownLatch(1);
        List<Integer> integers = Flowable.range(0, 100000)
                                         .toList()
                                         .blockingGet();

        final CountDownLatch startedLatch = new CountDownLatch(1);
        final TestSubscriberProducerRx3<Integer> producer = Flowable.fromIterable(new SlowingIterable<Integer>(integers))
                                                                 .subscribeWith(new TestSubscriberProducerRx3<Integer>() {
                                                                     @Override
                                                                     public void cancel() {
                                                                         super.cancel();
                                                                         cancellationLatch.countDown();
                                                                     }
                                                                 });

        final TestCallStreamObserver<Integer> downstream =
            new TestCallStreamObserver<Integer>(executorService);
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                producer.subscribe(downstream);
                startedLatch.countDown();
            }
        });

        startedLatch.await();

        racePauseResuming(downstream, 100);

        downstream.throwOnNext();

        Assertions.assertThat(downstream.awaitTerminal(1, TimeUnit.MINUTES)).isTrue();
        Assertions.assertThat(cancellationLatch.await(1, TimeUnit.MINUTES)).isTrue();
        Assertions.assertThat(downstream.e)
                  .isExactlyInstanceOf(StatusException.class)
                  .hasCauseInstanceOf(OnNextTestException.class);
        Assertions.assertThat(producer).hasFieldOrPropertyWithValue("sourceMode", 1);
        Assertions.assertThat(downstream.collected)
                  .isSubsetOf(integers);
        Assertions.assertThat(unhandledThrowable).isEmpty();
    }

    @RepeatedTest(2)
    public void regularModeWithRacingAndCancellationTest() throws InterruptedException {
        final AtomicLong requested = new AtomicLong();
        final AtomicLong produced = new AtomicLong();
        final CountDownLatch cancellationLatch = new CountDownLatch(1);
        List<Integer> integers = Flowable.range(0, 10000000)
                                         .toList()
                                         .blockingGet();

        TestSubscriberProducerRx3<Integer> producer = Flowable.fromIterable(integers)
                                                           .hide()
                                                           .subscribeOn(Schedulers.io())
                                                           .observeOn(Schedulers.io(), true)
                                                           .doOnNext(new Consumer<Integer>() {
                                                               @Override
                                                               public void accept(Integer __) {
                                                                   produced.incrementAndGet();
                                                               }
                                                           })
                                                           .doOnCancel(new Action() {
                                                               @Override
                                                               public void run() {
                                                                   cancellationLatch.countDown();
                                                               }
                                                           })
                                                           .doOnRequest(new LongConsumer() {
                                                               @Override
                                                               public void accept(long r) {
                                                                   requested.addAndGet(r);
                                                               }
                                                           })
                                                           .hide()
                                                           .subscribeWith(new TestSubscriberProducerRx3<Integer>());

        TestCallStreamObserver<Integer> downstream = new TestCallStreamObserver<Integer>(
            executorService);
        producer.subscribe(downstream);

        racePauseResuming(downstream, 100);

        producer.cancel();

        Assertions.assertThat(cancellationLatch.await(1, TimeUnit.MINUTES)).isTrue();
        Assertions.assertThat(downstream.done.getCount()).isEqualTo(1);
        Assertions.assertThat(downstream.e).isNull();
        Assertions.assertThat(requested.get()).isBetween(produced.get(), produced.get() + 1);
        Assertions.assertThat(producer).hasFieldOrPropertyWithValue("sourceMode", 0);
        Assertions.assertThat(downstream.collected)
                  .isSubsetOf(integers);
        Assertions.assertThat(unhandledThrowable).isEmpty();
    }

    @Tag("unstable")
    @RepeatedTest(2)
    public void asyncModeWithRacingAndCancellationTest() throws InterruptedException {
        final CountDownLatch cancellationLatch = new CountDownLatch(1);

        List<Integer> integers = Flowable.range(0, 10000000)
                                         .toList()
                                         .blockingGet();

        TestSubscriberProducerRx3<Integer> producer = Flowable.fromIterable(integers)
                                                           .hide()
                                                           .doOnCancel(new Action() {
                                                               @Override
                                                               public void run() {
                                                                   cancellationLatch.countDown();
                                                               }
                                                           })
                                                           .subscribeOn(Schedulers.io())
                                                           .observeOn(Schedulers.io(), true)
                                                           .subscribeWith(new TestSubscriberProducerRx3<Integer>());

        TestCallStreamObserver<Integer> downstream = new TestCallStreamObserver<Integer>(
            executorService);
        producer.subscribe(downstream);

        racePauseResuming(downstream, 100);

        producer.cancel();

        Assertions.assertThat(cancellationLatch.await(1, TimeUnit.MINUTES)).isTrue();

        Assertions.assertThat(downstream.done.getCount()).isEqualTo(1);
        Assertions.assertThat(downstream.e).isNull();
        Assertions.assertThat(producer).hasFieldOrPropertyWithValue("sourceMode", 2);
        Assertions.assertThat(downstream.collected)
                  .isSubsetOf(integers);
        Assertions.assertThat(unhandledThrowable).isEmpty();
    }

    @RepeatedTest(2)
    public void syncModeWithRacingAndCancellationTest() throws InterruptedException {
        final CountDownLatch cancellationLatch = new CountDownLatch(1);

        List<Integer> integers = Flowable.range(0, 100000)
                                         .toList()
                                         .blockingGet();

        final CountDownLatch startedLatch = new CountDownLatch(1);
        final TestSubscriberProducerRx3<Integer> producer = Flowable.fromIterable(new SlowingIterable<Integer>(integers))
                                                                 .subscribeWith(new TestSubscriberProducerRx3<Integer>() {
                                                                     @Override
                                                                     public void cancel() {
                                                                         super.cancel();
                                                                         cancellationLatch.countDown();
                                                                     }
                                                                 });

        final TestCallStreamObserver<Integer> downstream =
            new TestCallStreamObserver<Integer>(executorService);
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                producer.subscribe(downstream);
                startedLatch.countDown();
            }
        });

        startedLatch.await();

        racePauseResuming(downstream, 100);

        producer.cancel();

        Assertions.assertThat(cancellationLatch.await(1, TimeUnit.MINUTES)).isTrue();

        Assertions.assertThat(downstream.done.getCount()).isEqualTo(1);
        Assertions.assertThat(downstream.e).isNull();
        Assertions.assertThat(producer).hasFieldOrPropertyWithValue("sourceMode", 1);
        Assertions.assertThat(downstream.collected)
                  .isSubsetOf(integers);
        Assertions.assertThat(unhandledThrowable).isEmpty();
    }

    private static void racePauseResuming(final TestCallStreamObserver<?> downstream, int times) {
        Observable.range(0, times)
                  .concatMapCompletable(new Function<Integer, CompletableSource>() {
                      @Override
                      public CompletableSource apply(Integer i) {
                          return Completable
                              .fromAction(new Action() {
                                  @Override
                                  public void run() {
                                      downstream.resume();
                                  }
                              })
                              .subscribeOn(Schedulers.computation())
                              .andThen(Completable
                                  .fromAction(
                                      new Action() {
                                          @Override
                                          public void run() {
                                              downstream.pause();
                                          }
                                      }
                                  )
                                  .subscribeOn(Schedulers.computation())
                              );
                      }
                  })
                  .blockingAwait();

        downstream.pause();
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                downstream.resume();
            }
        });
    }

    static class SlowingIterable<T> implements Iterable<T> {

        private final Iterable<T> iterable;

        SlowingIterable(Iterable<T> iterable) {

            this.iterable = iterable;
        }

        @Override
        public Iterator<T> iterator() {
            return new SlowingIterator<T>(iterable.iterator());
        }

        static class SlowingIterator<T> implements Iterator<T> {

            private final Iterator<T> delegate;

            SlowingIterator(Iterator<T> delegate) {
                this.delegate = delegate;
            }

            @Override
            public boolean hasNext() {
                return delegate.hasNext();
            }

            @Override
            public T next() {
                LockSupport.parkNanos(10);
                return delegate.next();
            }

            @Override
            public void remove() {
                delegate.remove();
            }
        }
    }

    private static class OnNextTestException extends RuntimeException {

    }

    private static class TestCallStreamObserver<T> extends CallStreamObserver<T> {
        final ExecutorService executorService;
        List<T> collected = new ArrayList<T>();
        Throwable e;
        Runnable onReadyHandler;
        volatile boolean ready;
        volatile boolean throwOnNext = false;

        CountDownLatch done = new CountDownLatch(1);

        TestCallStreamObserver(ExecutorService service) {
            executorService = service;
        }

        @Override
        public boolean isReady() {
            return ready;
        }

        @Override
        public void onNext(T value) {
            collected.add(value);

            if (throwOnNext) {
                throw new OnNextTestException();
            }
        }

        @Override
        public void onError(Throwable t) {
            e = t;
            done.countDown();
        }

        @Override
        public void onCompleted() {
            done.countDown();
        }

        @Override
        public void setOnReadyHandler(Runnable onReadyHandler) {
            this.onReadyHandler = onReadyHandler;
        }

        void pause() {
            ready = false;
        }

        void resume() {
            ready = true;
            executorService.execute(onReadyHandler);
        }

        void throwOnNext() {
            throwOnNext = true;
        }

        boolean awaitTerminal(long timeout, TimeUnit timeUnit) {
            try {
                return done.await(timeout, timeUnit);
            }
            catch (InterruptedException ex) {
                ex.printStackTrace();
                return false;
            }
        }





        /// NO_OPS



        @Override
        public void disableAutoInboundFlowControl() {}

        @Override
        public void request(int count) {}

        @Override
        public void setMessageCompression(boolean enable) {}
    }
}
