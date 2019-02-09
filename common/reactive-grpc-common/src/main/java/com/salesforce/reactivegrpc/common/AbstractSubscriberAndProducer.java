/*
 *  Copyright (c) 2017, salesforce.com, inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.reactivegrpc.common;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import io.grpc.Status;
import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.CallStreamObserver;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * ReactivePublisherBackpressureOnReadyHandler bridges the manual flow control idioms of Reactive Streams and gRPC. This
 * class takes messages off of a {@link org.reactivestreams.Publisher} and feeds them into a {@link CallStreamObserver}
 * while respecting backpressure. This class is the inverse of {@link AbstractStreamObserverAndPublisher}.
 * <p>
 * When a gRPC publisher's transport wants more data to transmit, the {@link CallStreamObserver}'s onReady handler is
 * signaled. This handler must keep transmitting messages until {@link CallStreamObserver#isReady()} ceases to be true.
 * <p>
 * When a {@link org.reactivestreams.Publisher} is subscribed to by a {@link Subscriber}, the
 * {@code Publisher} hands the {@code Subscriber} a {@link Subscription}. When the {@code Subscriber}
 * wants more messages from the {@code Publisher}, the {@code Subscriber} calls {@link Subscription#request(long)}.
 * <p>
 * To bridge the two idioms: when gRPC wants more messages, the {@code onReadyHandler} is called and {@link #run()}
 * calls the {@code Subscription}'s {@code request()} method, asking the {@code Publisher} to produce another message.
 * Since this class is also registered as the {@code Publisher}'s {@code Subscriber}, the {@link #onNext(Object)}
 * method is called. {@code onNext()} passes the message to gRPC's {@link CallStreamObserver#onNext(Object)} method,
 * and then calls {@code request()} again if {@link CallStreamObserver#isReady()} is true. The loop of
 * request->pass->check is repeated until {@code isReady()} returns false, indicating that the outbound transmit buffer
 * is full and that backpressure must be applied.
 *
 * @param <T>
 */
public abstract class AbstractSubscriberAndProducer<T> implements Subscriber<T>, Runnable {

    private static final int STATE_UNSUBSCRIBED = 0;
    private static final int STATE_NOT_READY    = 1;
    private static final int STATE_READY        = 2;
    private static final int STATE_CANCELLED    = 3;

    volatile CallStreamObserver<T> downstream;

    boolean   done;
    Throwable throwable;

    Subscription subscription;

//    volatile T element;

//    private CountDownLatch subscribed = new CountDownLatch(1);

    // Guard against spurious onReady() calls caused by a race between onNext() and onReady(). If the transport
    // toggles isReady() from false to true while onNext() is executing, but before onNext() checks isReady(),
    // request(1) would be called twice - state by onNext() and state by the onReady() scheduled during onNext()'s
    // execution.
    // STATE = 0 Unsubscribed
    // STATE = 1 Was not ready
    // STATE = 2 Was ready
    // STATE = 3 Cancelled
    private volatile int state;
    @SuppressWarnings("rawtypes")
    private static final AtomicIntegerFieldUpdater<AbstractSubscriberAndProducer> STATE =
        AtomicIntegerFieldUpdater.newUpdater(AbstractSubscriberAndProducer.class, "state");

    volatile int wip;
    @SuppressWarnings("rawtypes")
    protected static final AtomicIntegerFieldUpdater<AbstractSubscriberAndProducer> WIP =
            AtomicIntegerFieldUpdater.newUpdater(AbstractSubscriberAndProducer.class, "wip");

    public void subscribe(final CallStreamObserver<T> downstream) {
        this.downstream = checkNotNull(downstream);
        downstream.setOnReadyHandler(this);

        drain();
    }

    @Override
    public void run() {
        if (state == 1 && STATE.compareAndSet(this, STATE_NOT_READY, STATE_READY) || state == 2) {
            subscription.request(1);
        }
    }

    public void cancel() {
        if (!isCanceled() && STATE.getAndSet(this, STATE_CANCELLED) != 0) {
            subscription.cancel();
            subscription = null;
        }
    }

    public boolean isCanceled() {
        return state == STATE_CANCELLED;
    }

    @Override
    public void onSubscribe(Subscription subscription) {
        checkNotNull(subscription);

        if (state == 0 && STATE.compareAndSet(this, STATE_UNSUBSCRIBED, STATE_NOT_READY)) {
            CallStreamObserver<T> downstream = this.downstream;
            this.subscription = subscription;

            if (downstream != null && downstream.isReady() && STATE.compareAndSet(this, STATE_NOT_READY, STATE_READY)) {
                subscription.request(1);
            }

            return;
        }

        subscription.cancel();
    }

    @Override
    public void onNext(T t) {
        checkNotNull(t);
        if (!isCanceled()) {
            CallStreamObserver<T> stream = downstream;

            try {
                stream.onNext(t);

                if (stream.isReady()) {
                    // keep the pump going
                    subscription.request(1);
                } else {
                    // note that back-pressure has begun
                    STATE.compareAndSet(this, STATE_NOT_READY, STATE_READY);
                }
            } catch (Throwable throwable) {
                cancel();
                stream.onError(prepareError(throwable));
            }
        }
    }

    @Override
    public void onError(Throwable t) {
        if (!isCanceled()) {
            checkNotNull(t);

            done = true;
            throwable = t;

            drain();
        }
    }

    @Override
    public void onComplete() {
        if (!isCanceled()) {
            done = true;

            drain();
        }
    }

    void drain() {
        if (WIP.getAndIncrement(this) != 0) {
            return;
        }

        int missed = 1;
        CallStreamObserver<? super T> a = downstream;

        for (;;) {
            if (a != null) {

                if (done) {
                    Throwable t = throwable;

                    if (t != null) {
                        try {
                            a.onError(prepareError(t));
                        } catch (Throwable ignore) {
                            cancel();
                        }
                    } else {
                        try {
                            a.onCompleted();
                        } catch (Throwable throwable) {
                            cancel();
                            a.onError(prepareError(throwable));
                        }
                    }

                    return;
                }
            }

            missed = WIP.addAndGet(this, -missed);
            if (missed == 0) {
                break;
            }
        }
    }

    private static Throwable prepareError(Throwable throwable) {
        if (throwable instanceof StatusException || throwable instanceof StatusRuntimeException) {
            return throwable;
        } else {
            return Status.fromThrowable(throwable).asException();
        }
    }

}
