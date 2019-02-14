package com.salesforce.reactivegrpc.common;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.grpc.StatusException;
import io.grpc.stub.CallStreamObserver;
import io.reactivex.Flowable;
import io.reactivex.Scheduler;
import io.reactivex.exceptions.Exceptions;
import io.reactivex.functions.Action;
import io.reactivex.functions.Consumer;
import io.reactivex.plugins.RxJavaPlugins;
import io.reactivex.schedulers.Schedulers;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class AbstractSubscriberAndProducerTest {

    final Queue<Throwable> unhandledThrowable = new ConcurrentLinkedQueue<Throwable>();

    @Parameterized.Parameters
    public static Object[][] data() {
        return new Object[20][0];
    }

    public AbstractSubscriberAndProducerTest() {
    }


    static final ExecutorService executorService  =
        Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());


    @Before
    public void setUp() {
        RxJavaPlugins.setErrorHandler(new Consumer<Throwable>() {
            @Override
            public void accept(Throwable throwable) {
                unhandledThrowable.offer(throwable);
            }
        });
    }

    @Test
    public void regularModeWithRacingTest() {
        List<Integer> integers = Flowable.range(0, 10000000)
                                         .toList()
                                         .blockingGet();

        TestSubscriberProducer<Integer> producer = Flowable.fromIterable(integers)
                                                           .hide()
                                                           .subscribeOn(Schedulers.computation())
                                                           .hide()
                                                           .subscribeWith(new TestSubscriberProducer<Integer>());

        TestCallStreamObserver<Integer> downstream = new TestCallStreamObserver<Integer>(
            executorService);
        producer.subscribe(downstream);

        for (int i = 0; i < 10000; i++) {
            downstream.pause();
            downstream.resume();
        }

        Assertions.assertThat(downstream.awaitTerminal(10, TimeUnit.SECONDS)).isTrue();
        Assertions.assertThat(downstream.e).isNull();
        Assertions.assertThat(producer).hasFieldOrPropertyWithValue("sourceMode", 0);
        Assertions.assertThat(downstream.collected)
                  .isEqualTo(integers);
        Assertions.assertThat(unhandledThrowable).isEmpty();
    }

    @Test
    public void asyncModeWithRacingTest() {
        List<Integer> integers = Flowable.range(0, 10000000)
                                         .toList()
                                         .blockingGet();

        TestSubscriberProducer<Integer> producer = Flowable.fromIterable(integers)
                                                           .hide()
                                                           .observeOn(Schedulers.computation())
                                                           .subscribeWith(new TestSubscriberProducer<Integer>());

        TestCallStreamObserver<Integer> downstream = new TestCallStreamObserver<Integer>(executorService);
        producer.subscribe(downstream);

        for (int i = 0; i < 10000; i++) {
            downstream.pause();
            downstream.resume();
        }

        Assertions.assertThat(downstream.awaitTerminal(10, TimeUnit.SECONDS)).isTrue();
        Assertions.assertThat(downstream.e).isNull();
        Assertions.assertThat(producer).hasFieldOrPropertyWithValue("sourceMode", 2);
        Assertions.assertThat(downstream.collected)
                  .isEqualTo(integers);
        Assertions.assertThat(unhandledThrowable).isEmpty();
    }

    @Test
    public void syncModeWithRacingTest() throws InterruptedException {
        List<Integer> integers = Flowable.range(0, 10000000)
                                         .toList()
                                         .blockingGet();

        final CountDownLatch startedLatch = new CountDownLatch(1);
        final TestSubscriberProducer<Integer> producer = Flowable.fromIterable(integers)
                                                           .subscribeWith(new TestSubscriberProducer<Integer>());

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

        for (int i = 0; i < 10000; i++) {

            downstream.pause();
            downstream.resume();
        }

        Assertions.assertThat(downstream.awaitTerminal(10, TimeUnit.SECONDS)).isTrue();
        Assertions.assertThat(downstream.e).isNull();
        Assertions.assertThat(producer).hasFieldOrPropertyWithValue("sourceMode", 1);
        Assertions.assertThat(downstream.collected)
                  .isEqualTo(integers);
        Assertions.assertThat(unhandledThrowable).isEmpty();
    }

    @Test
    public void regularModeWithRacingAndErrorTest() {

        List<Integer> integers = Flowable.range(0, 10000000)
                                         .toList()
                                         .blockingGet();

        ArrayList<Integer> copy = new ArrayList<Integer>(integers);

        copy.add(null);

        TestSubscriberProducer<Integer> producer = Flowable.fromIterable(copy)
                                                           .hide()
                                                           .subscribeOn(Schedulers.computation())
                                                           .hide()
                                                           .subscribeWith(new TestSubscriberProducer<Integer>());

        TestCallStreamObserver<Integer> downstream = new TestCallStreamObserver<Integer>(
            executorService);
        producer.subscribe(downstream);

        for (int i = 0; i < 10000; i++) {
            downstream.pause();
            downstream.resume();
        }

        Assertions.assertThat(downstream.awaitTerminal(10, TimeUnit.SECONDS)).isTrue();
        Assertions.assertThat(downstream.e)
                  .isExactlyInstanceOf(StatusException.class)
                  .hasCauseInstanceOf(NullPointerException.class);
        Assertions.assertThat(producer).hasFieldOrPropertyWithValue("sourceMode", 0);
        Assertions.assertThat(downstream.collected)
                  .isEqualTo(integers);
        Assertions.assertThat(unhandledThrowable).isEmpty();
    }

    @Test
    public void asyncModeWithRacingAndErrorTest() {

        List<Integer> integers = Flowable.range(0, 10000000)
                                         .toList()
                                         .blockingGet();

        ArrayList<Integer> copy = new ArrayList<Integer>(integers);

        copy.add(null);

        TestSubscriberProducer<Integer> producer = Flowable.fromIterable(copy)
                                                           .hide()
                                                           .observeOn(Schedulers.computation())
                                                           .subscribeWith(new TestSubscriberProducer<Integer>());

        TestCallStreamObserver<Integer> downstream = new TestCallStreamObserver<Integer>(
            executorService);
        producer.subscribe(downstream);

        for (int i = 0; i < 10000; i++) {
            downstream.pause();
            downstream.resume();
        }

        Assertions.assertThat(downstream.awaitTerminal(10, TimeUnit.SECONDS)).isTrue();
        Assertions.assertThat(downstream.e)
                  .isExactlyInstanceOf(StatusException.class)
                  .hasCauseInstanceOf(NullPointerException.class);
        Assertions.assertThat(producer).hasFieldOrPropertyWithValue("sourceMode", 2);
        Assertions.assertThat(downstream.collected)
                  .isEqualTo(integers);
        Assertions.assertThat(unhandledThrowable).isEmpty();
    }

    @Test
    public void syncModeWithRacingAndErrorTest() throws InterruptedException {

        List<Integer> integers = Flowable.range(0, 10000000)
                                         .toList()
                                         .blockingGet();

        ArrayList<Integer> copy = new ArrayList<Integer>(integers);

        copy.add(null);

        final CountDownLatch startedLatch = new CountDownLatch(1);
        final TestSubscriberProducer<Integer> producer = Flowable.fromIterable(copy)
                                                           .subscribeWith(new TestSubscriberProducer<Integer>());

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

        for (int i = 0; i < 10000; i++) {
            downstream.pause();
            downstream.resume();
        }

        Assertions.assertThat(downstream.awaitTerminal(10, TimeUnit.SECONDS)).isTrue();
        Assertions.assertThat(downstream.e)
                  .isExactlyInstanceOf(StatusException.class)
                  .hasCauseInstanceOf(NullPointerException.class);
        Assertions.assertThat(producer).hasFieldOrPropertyWithValue("sourceMode", 1);
        Assertions.assertThat(downstream.collected)
                  .isEqualTo(integers);
        Assertions.assertThat(unhandledThrowable).isEmpty();
    }

    @Test
    public void regularModeWithRacingAndOnErrorOverOnNextTest() {

        List<Integer> integers = Flowable.range(0, 10000000)
                                         .toList()
                                         .blockingGet();

        TestSubscriberProducer<Integer> producer = Flowable.fromIterable(integers)
                                                           .hide()
                                                           .subscribeOn(Schedulers.computation())
                                                           .hide()
                                                           .subscribeWith(new TestSubscriberProducer<Integer>());

        TestCallStreamObserver<Integer> downstream = new TestCallStreamObserver<Integer>(
            executorService);
        producer.subscribe(downstream);

        for (int i = 0; i < 100; i++) {
            downstream.pause();
            downstream.resume();
        }

        downstream.throwOnNext();

        Assertions.assertThat(downstream.awaitTerminal(10, TimeUnit.SECONDS)).isTrue();
        Assertions.assertThat(downstream.e)
                  .isExactlyInstanceOf(StatusException.class)
                  .hasCauseInstanceOf(OnNextTestException.class);
        Assertions.assertThat(producer).hasFieldOrPropertyWithValue("sourceMode", 0);
        Assertions.assertThat(downstream.collected)
                  .isSubsetOf(integers);
        Assertions.assertThat(unhandledThrowable).isEmpty();
    }

    @Test
    public void asyncModeWithRacingAndOnErrorOverOnNextTest() {

        List<Integer> integers = Flowable.range(0, 10000000)
                                         .toList()
                                         .blockingGet();

        TestSubscriberProducer<Integer> producer = Flowable.fromIterable(integers)
                                                           .hide()
                                                           .observeOn(Schedulers.computation())
                                                           .subscribeWith(new TestSubscriberProducer<Integer>());

        TestCallStreamObserver<Integer> downstream = new TestCallStreamObserver<Integer>(
            executorService);
        producer.subscribe(downstream);

        for (int i = 0; i < 100; i++) {
            downstream.pause();
            downstream.resume();
        }

        downstream.throwOnNext();

        Assertions.assertThat(downstream.awaitTerminal(10, TimeUnit.SECONDS)).isTrue();
        Assertions.assertThat(downstream.e)
                  .isExactlyInstanceOf(StatusException.class)
                  .hasCauseInstanceOf(OnNextTestException.class);
        Assertions.assertThat(producer).hasFieldOrPropertyWithValue("sourceMode", 2);
        Assertions.assertThat(downstream.collected)
                  .isSubsetOf(integers);
        Assertions.assertThat(unhandledThrowable).isEmpty();
    }

    @Test
    public void syncModeWithRacingAndOnErrorOverOnNextTest() throws InterruptedException {

        List<Integer> integers = Flowable.range(0, 10000000)
                                         .toList()
                                         .blockingGet();

        final CountDownLatch startedLatch = new CountDownLatch(1);
        final TestSubscriberProducer<Integer> producer = Flowable.fromIterable(integers)
                                                                 .subscribeWith(new TestSubscriberProducer<Integer>());

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

        for (int i = 0; i < 100; i++) {
            downstream.pause();
            downstream.resume();
        }

        downstream.throwOnNext();

        Assertions.assertThat(downstream.awaitTerminal(10, TimeUnit.SECONDS)).isTrue();
        Assertions.assertThat(downstream.e)
                  .isExactlyInstanceOf(StatusException.class)
                  .hasCauseInstanceOf(OnNextTestException.class);
        Assertions.assertThat(producer).hasFieldOrPropertyWithValue("sourceMode", 1);
        Assertions.assertThat(downstream.collected)
                  .isSubsetOf(integers);
        Assertions.assertThat(unhandledThrowable).isEmpty();
    }

    @Test
    public void regularModeWithRacingAndCancellationTest() throws InterruptedException {
        final CountDownLatch cancellationLatch = new CountDownLatch(1);
        List<Integer> integers = Flowable.range(0, 10000000)
                                         .toList()
                                         .blockingGet();

        TestSubscriberProducer<Integer> producer = Flowable.fromIterable(integers)
                                                           .hide()
                                                           .subscribeOn(Schedulers.computation())
                                                           .hide()
                                                           .doOnCancel(new Action() {
                                                               @Override
                                                               public void run() {
                                                                   cancellationLatch.countDown();
                                                               }
                                                           })
                                                           .subscribeWith(new TestSubscriberProducer<Integer>());

        TestCallStreamObserver<Integer> downstream = new TestCallStreamObserver<Integer>(
            executorService);
        producer.subscribe(downstream);

        for (int i = 0; i < 100; i++) {
            downstream.pause();
            downstream.resume();
        }

        producer.cancel();

        Assertions.assertThat(cancellationLatch.await(10, TimeUnit.SECONDS)).isTrue();

        Assertions.assertThat(downstream.done.getCount()).isEqualTo(1);
        Assertions.assertThat(downstream.e).isNull();
        Assertions.assertThat(producer).hasFieldOrPropertyWithValue("sourceMode", 0);
        Assertions.assertThat(downstream.collected)
                  .isSubsetOf(integers);
        Assertions.assertThat(unhandledThrowable).isEmpty();
    }

    @Test
    public void asyncModeWithRacingAndCancellationTest() throws InterruptedException {
        final CountDownLatch cancellationLatch = new CountDownLatch(1);

        List<Integer> integers = Flowable.range(0, 10000000)
                                         .toList()
                                         .blockingGet();

        TestSubscriberProducer<Integer> producer = Flowable.fromIterable(integers)
                                                           .hide()
                                                           .doOnCancel(new Action() {
                                                               @Override
                                                               public void run() {
                                                                   cancellationLatch.countDown();
                                                               }
                                                           })
                                                           .observeOn(Schedulers.computation())
                                                           .subscribeWith(new TestSubscriberProducer<Integer>());

        TestCallStreamObserver<Integer> downstream = new TestCallStreamObserver<Integer>(
            executorService);
        producer.subscribe(downstream);

        for (int i = 0; i < 100; i++) {
            downstream.pause();
            downstream.resume();
        }

        producer.cancel();

        Assertions.assertThat(cancellationLatch.await(10, TimeUnit.SECONDS)).isTrue();

        Assertions.assertThat(downstream.done.getCount()).isEqualTo(1);
        Assertions.assertThat(downstream.e).isNull();
        Assertions.assertThat(producer).hasFieldOrPropertyWithValue("sourceMode", 2);
        Assertions.assertThat(downstream.collected)
                  .isSubsetOf(integers);
        Assertions.assertThat(unhandledThrowable).isEmpty();
    }

    @Test
    public void syncModeWithRacingAndCancellationTest() throws InterruptedException {
        final CountDownLatch cancellationLatch = new CountDownLatch(1);

        List<Integer> integers = Flowable.range(0, 10000000)
                                         .toList()
                                         .blockingGet();

        final CountDownLatch startedLatch = new CountDownLatch(1);
        final TestSubscriberProducer<Integer> producer = Flowable.fromIterable(integers)
                                                                 .subscribeWith(new TestSubscriberProducer<Integer>() {
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

        for (int i = 0; i < 100; i++) {
            downstream.pause();
            downstream.resume();
        }

        producer.cancel();

        Assertions.assertThat(cancellationLatch.await(10, TimeUnit.SECONDS)).isTrue();

        Assertions.assertThat(downstream.done.getCount()).isEqualTo(1);
        Assertions.assertThat(downstream.e).isNull();
        Assertions.assertThat(producer).hasFieldOrPropertyWithValue("sourceMode", 1);
        Assertions.assertThat(downstream.collected)
                  .isSubsetOf(integers);
        Assertions.assertThat(unhandledThrowable).isEmpty();
    }

    static class OnNextTestException extends RuntimeException {

    }

    static class TestCallStreamObserver<T> extends CallStreamObserver<T> {
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