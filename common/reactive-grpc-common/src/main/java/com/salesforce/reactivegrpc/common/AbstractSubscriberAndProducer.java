/*
 *  Copyright (c) 2017, salesforce.com, inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.reactivegrpc.common;

import java.util.Queue;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import io.grpc.Status;
import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.CallStreamObserver;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import static com.google.common.base.Preconditions.checkNotNull;

// TODO: FIX ME ACCORDING TO THE LATEST CHANGES PLEEESE!
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

    /** Indicates the QueueSubscription can't support the requested mode. */
    private static final int NONE = 0;
    /** Indicates the QueueSubscription can perform sync-fusion. */
    private static final int SYNC = 1;
    /** Indicates the QueueSubscription can perform only async-fusion. */
    private static final int ASYNC = 2;
    /** Indicates the QueueSubscription should decide what fusion it performs (input only). */
    private static final int ANY = 3;
    /**
     * Indicates that the queue will be drained from another thread
     * thus any queue-exit computation may be invalid at that point.
     * <p>
     * For example, an {@code asyncSource.map().publishOn().subscribe()} sequence where {@code asyncSource}
     * is async-fuseable: publishOn may fuse the whole sequence into a single Queue. That in turn
     * could invoke the mapper function from its {@code poll()} method from another thread,
     * whereas the unfused sequence would have invoked the mapper on the previous thread.
     * If such mapper invocation is costly, it would escape its thread boundary this way.
     */
    private static final int THREAD_BARRIER = 4;




    private static final int UNSUBSCRIBED_STATE = 0;
    private static final int NOT_FUSED_STATE    = 1;
    private static final int NOT_READY_STATE    = 2;
    private static final int READY_STATE        = 3;
    private static final int CANCELLED_STATE    = 4;



    volatile Subscription subscription;
    Throwable throwable;

    protected boolean   done;
    protected Queue<T> queue;
    protected int      sourceMode;


    volatile CallStreamObserver<T> downstream;
    @SuppressWarnings("rawtypes")
    static final AtomicReferenceFieldUpdater<AbstractSubscriberAndProducer, CallStreamObserver> DOWNSTREAM =
        AtomicReferenceFieldUpdater.newUpdater(AbstractSubscriberAndProducer.class, CallStreamObserver.class, "downstream");


    // Guard against spurious onReady() calls caused by a race between onNext() and onReady(). If the transport
    // toggles isReady() from false to true while onNext() is executing, but before onNext() checks isReady(),
    // request(1) would be called twice - state by onNext() and state by the onReady() scheduled during onNext()'s
    // execution.
    // STATE = 0 Was not ready
    // STATE = 1 Was ready
    volatile int state;
    @SuppressWarnings("rawtypes")
    static final AtomicIntegerFieldUpdater<AbstractSubscriberAndProducer> STATE =
        AtomicIntegerFieldUpdater.newUpdater(AbstractSubscriberAndProducer.class, "state");

    volatile int wip;
    @SuppressWarnings("rawtypes")
    static final AtomicIntegerFieldUpdater<AbstractSubscriberAndProducer> WIP =
            AtomicIntegerFieldUpdater.newUpdater(AbstractSubscriberAndProducer.class, "wip");

    public void subscribe(final CallStreamObserver<T> downstream) {
        checkNotNull(downstream);

        if (this.downstream == null && DOWNSTREAM.compareAndSet(this, null, downstream)) {
            downstream.setOnReadyHandler(this);
        }
    }

    @Override
    public void run() {
        if (state == NOT_READY_STATE && STATE.compareAndSet(this, NOT_READY_STATE, READY_STATE)) {
            drain();
        }
    }

    public void cancel() {
        if (!isCanceled() && STATE.getAndSet(this, CANCELLED_STATE) > 1) {
            subscription.cancel();
            subscription = null;

            if (WIP.getAndIncrement(this) == 0) {
                if (queue != null) {
                    queue.clear();
                }
            }
        }
    }

    public boolean isCanceled() {
        return state == CANCELLED_STATE;
    }

    @Override
    public void onSubscribe(Subscription subscription) {
        checkNotNull(subscription);

        if (state == 0 && STATE.compareAndSet(this, UNSUBSCRIBED_STATE, NOT_FUSED_STATE)) {
            this.subscription = subscription;

            fuse(subscription);

            if (sourceMode == ASYNC) {
                subscription.request(1);
            }

            if (STATE.compareAndSet(this, NOT_FUSED_STATE, NOT_READY_STATE)) {
                final CallStreamObserver<T> downstream = this.downstream;

                if (downstream != null && downstream.isReady() && STATE.compareAndSet(this, NOT_READY_STATE, READY_STATE)) {
                    drain();
                }
            } else {
                subscription.cancel();
                this.subscription = null;
            }

            return;
        }

        subscription.cancel();
    }

    @Override
    public void onNext(T t) {
        if (sourceMode == ASYNC) {
            drain();
            return;
        }

        if (!isCanceled()) {
            checkNotNull(t);

            final CallStreamObserver<T> stream = downstream;

            try {
                stream.onNext(t);

                if (stream.isReady()) {
                    // keep the pump going
                    subscription.request(1);
                } else {
                    // note that back-pressure has begun
                    if (STATE.compareAndSet(this, READY_STATE, NOT_READY_STATE)) {
                        if (stream.isReady() && STATE.compareAndSet(this,
                                NOT_READY_STATE, READY_STATE)) {
                            // double check keep the pump going
                            subscription.request(1);
                        }
                    }
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

    protected abstract void fuse(Subscription subscription);

    void drain() {
        if (WIP.getAndIncrement(this) != 0) {
            return;
        }

        int missed = 1;
        final CallStreamObserver<? super T> a = downstream;

        for (;;) {
            if (a != null) {
                if (sourceMode == SYNC) {
                    runSync();

                    return;
                }
                else if (sourceMode == ASYNC) {
                    runAsync();

                    return;
                }
                else if (done) {
                    Throwable t = throwable;

                    if (t != null) {
                        try {
                            a.onError(prepareError(t));
                        }
                        catch (Throwable ignore) {
                            cancel();
                        }
                    }
                    else {
                        try {
                            a.onCompleted();
                        }
                        catch (Throwable throwable) {
                            cancel();
                            a.onError(prepareError(throwable));
                        }
                    }

                    return;
                }
                else {
                    subscription.request(1);
                }
            }

            missed = WIP.addAndGet(this, -missed);
            if (missed == 0) {
                break;
            }
        }
    }



    void runSync() {
        int missed = 1;

        final CallStreamObserver<? super T> a = downstream;
        final Queue<T> q = queue;

        for (;;) {

            while (a.isReady()) {
                T v;

                try {
                    v = q.poll();
                }
                catch (Throwable ex) {
                    try {
                        a.onError(prepareError(ex));
                    } catch (Throwable ignore) {
                        cancel();
                    }
                    return;
                }

                if (isCanceled()) {
                    q.clear();
                    return;
                }
                if (v == null) {
                    try {
                        a.onCompleted();
                    } catch (Throwable throwable) {
                        cancel();
                        a.onError(prepareError(throwable));
                    }
                    return;
                }

                a.onNext(v);
            }

            if (isCanceled()) {
                q.clear();
                return;
            }

            if (q.isEmpty()) {
                try {
                    a.onCompleted();
                } catch (Throwable throwable) {
                    cancel();
                    a.onError(prepareError(throwable));
                }
                return;
            }

            if (!a.isReady()) {
                if (STATE.compareAndSet(this, READY_STATE, NOT_READY_STATE)) {
                    if (a.isReady()) {
                        STATE.compareAndSet(this, NOT_READY_STATE, READY_STATE);
                    }
                }
            }

            int w = wip;
            if (missed == w) {
                missed = WIP.addAndGet(this, -missed);
                if (missed == 0) {
                    break;
                }
            }
            else {
                missed = w;
            }
        }
    }

    void runAsync() {
        int missed = 1;

        final Subscription s = subscription;
        final CallStreamObserver<? super T> a = downstream;
        final Queue<T> q = queue;

        long sent = 0;

        for (;;) {

            while (a.isReady()) {
                boolean d = done;
                T v;

                try {
                    v = q.poll();
                }
                catch (Throwable ex) {
                    s.cancel();
                    queue.clear();

                    try {
                        a.onError(prepareError(ex));
                    } catch (Throwable ignore) { }

                    return;
                }

                boolean empty = v == null;

                if (checkTerminated(d, empty, a)) {
                    return;
                }

                if (empty) {
                    break;
                }

                a.onNext(v);

                sent++;
            }

            if (checkTerminated(done, q.isEmpty(), a)) {
                return;
            }

            if (!a.isReady()) {
                if (STATE.compareAndSet(this, READY_STATE, NOT_READY_STATE)) {
                    if (a.isReady()) {
                        STATE.compareAndSet(this, NOT_READY_STATE, READY_STATE);
                    }
                }
            }

            int w = wip;
            if (missed == w) {
                if (sent > 0) {
                    s.request(sent);
                }
                missed = WIP.addAndGet(this, -missed);
                if (missed == 0) {
                    break;
                }
                sent = 0;
            }
            else {
                missed = w;
            }
        }
    }

    boolean checkTerminated(boolean d, boolean empty, CallStreamObserver<?> a) {
        if (isCanceled()) {
            queue.clear();
            return true;
        }

        if (d) {
            Throwable t = throwable;
            if (t != null) {
                queue.clear();
                try {
                    a.onError(prepareError(t));
                } catch (Throwable ignore) {
                    cancel();
                }
                return true;
            } else if (empty) {
                try {
                    a.onCompleted();
                } catch (Throwable throwable) {
                    cancel();
                    a.onError(prepareError(throwable));
                }
                return true;
            }
        }

        return false;
    }

    private static Throwable prepareError(Throwable throwable) {
        if (throwable instanceof StatusException || throwable instanceof StatusRuntimeException) {
            return throwable;
        } else {
            return Status.fromThrowable(throwable).asException();
        }
    }

}
