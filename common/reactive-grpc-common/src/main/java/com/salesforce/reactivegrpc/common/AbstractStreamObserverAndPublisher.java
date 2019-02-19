/*
 *  Copyright (c) 2017, salesforce.com, inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.reactivegrpc.common;

import java.util.Queue;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.concurrent.locks.LockSupport;

import io.grpc.stub.CallStreamObserver;
import io.grpc.stub.StreamObserver;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import static com.google.common.base.Preconditions.checkNotNull;

// TODO FIX DOCS
/**
 * ReactiveStreamObserverPublisher bridges the manual flow control idioms of gRPC and Reactive Streams. This class takes
 * messages off of a {@link StreamObserver} and feeds them into a {@link Publisher} while respecting backpressure. This
 * class is the inverse of {@link AbstractSubscriberAndProducer}.
 * <p>
 * When a {@link Publisher} is subscribed to by a {@link Subscriber}, the {@code Publisher} hands the {@code Subscriber}
 * a {@link Subscription}. When the {@code Subscriber} wants more messages from the {@code Publisher}, the
 * {@code Subscriber} calls {@link Subscription#request(long)}.
 * <p>
 * gRPC also uses the {@link CallStreamObserver#request(int)} idiom to request more messages from the stream.
 * <p>
 * To bridge the two idioms: this class implements a {@code Publisher} which delegates calls to {@code request()} to
 * a {@link CallStreamObserver} set in the constructor. When a message is generated as a response, the message is
 * delegated in the reverse so the {@code Publisher} can announce it to the Reactive Streams implementation.
 *
 * @param <T>
 */
public abstract class AbstractStreamObserverAndPublisher<T> extends AbstractUnimplementedQueue<T>
        implements Publisher<T>, StreamObserver<T>, Subscription, Queue<T>  {

    private static final Subscription EMPTY_SUBSCRIPTION = new Subscription() {
        @Override
        public void cancel() {
            // deliberately no op
        }

        @Override
        public void request(long n) {
            // deliberately no op
        }
    };


    protected static final int DEFAULT_CHUNK_SIZE = 16;

    private static final int UNSUBSCRIBED_STATE    = 0;
    private static final int SUBSCRIBED_ONCE_STATE = 1;
    private static final int PREFETCHED_ONCE_STATE = 2;

    private static final int SPIN_LOCK_PARK_NANOS = 10;

    protected volatile boolean outputFused;

    private final Queue<T> queue;
    private final int prefetch;

    private final Consumer<CallStreamObserver<?>> onSubscribe;


    private volatile boolean done;
    private Throwable error;

    private volatile Subscriber<? super T> downstream;

    private volatile boolean cancelled;

    protected volatile CallStreamObserver<?> subscription;
    private static final AtomicReferenceFieldUpdater<AbstractStreamObserverAndPublisher, CallStreamObserver> SUBSCRIPTION =
        AtomicReferenceFieldUpdater.newUpdater(AbstractStreamObserverAndPublisher.class, CallStreamObserver.class, "subscription");

    private volatile Runnable onTerminate;
    private static final AtomicReferenceFieldUpdater<AbstractStreamObserverAndPublisher, Runnable> ON_TERMINATE =
            AtomicReferenceFieldUpdater.newUpdater(AbstractStreamObserverAndPublisher.class, Runnable.class, "onTerminate");

    private volatile int state;
    private static final AtomicIntegerFieldUpdater<AbstractStreamObserverAndPublisher> STATE =
            AtomicIntegerFieldUpdater.newUpdater(AbstractStreamObserverAndPublisher.class, "state");

    private volatile int wip;
    private static final AtomicIntegerFieldUpdater<AbstractStreamObserverAndPublisher> WIP =
            AtomicIntegerFieldUpdater.newUpdater(AbstractStreamObserverAndPublisher.class, "wip");

    private volatile long requested;
    private static final AtomicLongFieldUpdater<AbstractStreamObserverAndPublisher> REQUESTED =
            AtomicLongFieldUpdater.newUpdater(AbstractStreamObserverAndPublisher.class, "requested");

    private int produced;

    AbstractStreamObserverAndPublisher(
            Queue<T> queue,
            Consumer<CallStreamObserver<?>> onSubscribe) {
        this(queue, DEFAULT_CHUNK_SIZE, onSubscribe);
    }

    AbstractStreamObserverAndPublisher(
            Queue<T> queue,
            Consumer<CallStreamObserver<?>> onSubscribe,
            Runnable onTerminate) {
        this(queue, DEFAULT_CHUNK_SIZE, onSubscribe, onTerminate);
    }

    AbstractStreamObserverAndPublisher(
            Queue<T> queue,
            int prefetch,
            Consumer<CallStreamObserver<?>> onSubscribe) {
        this(queue, prefetch, onSubscribe, null);
    }

    AbstractStreamObserverAndPublisher(
            Queue<T> queue,
            int prefetch,
            Consumer<CallStreamObserver<?>> onSubscribe,
            Runnable onTerminate) {
        this.prefetch = prefetch;
        this.queue = queue;
        this.onSubscribe = onSubscribe;
        this.onTerminate = onTerminate;
    }

    protected void onSubscribe(final CallStreamObserver<?> upstream) {
        if (subscription == null && SUBSCRIPTION.compareAndSet(this, null, upstream)) {
            upstream.disableAutoInboundFlowControl();
            if (onSubscribe != null) {
                onSubscribe.accept(upstream);
            }
            return;
        }

        throw new IllegalStateException(getClass().getSimpleName() + " supports only a single subscription");
    }

    private void doTerminate() {
        Runnable r = onTerminate;
        if (r != null && ON_TERMINATE.compareAndSet(this, r, null)) {
            r.run();
        }
    }

    private void drainRegular(final Subscriber<? super T> subscriber) {
        int missed = 1;

        final CallStreamObserver<?> s = subscription;
        final Queue<T> q = queue;
        int sent = produced;
        long r;

        for (;;) {
            r = requested;
            while (r != sent) {
                boolean d = done;

                T t = q.poll();
                boolean empty = t == null;

                if (checkTerminated(d, empty, subscriber, q)) {
                    return;
                }

                if (empty) {
                    break;
                }

                subscriber.onNext(t);

                sent++;

                if (sent == prefetch) {
                    if (r != Long.MAX_VALUE) {
                        r = REQUESTED.addAndGet(this, -sent);
                    }

                    s.request(sent);
                    sent = 0;
                }
            }

            if (r == sent) {
                if (checkTerminated(done, q.isEmpty(), subscriber, q)) {
                    return;
                }
            }

            int w = wip;
            if (missed == w) {
                produced = sent;
                missed = WIP.addAndGet(this, -missed);
                if (missed == 0) {
                    break;
                }
            } else {
                missed = w;
            }
        }
    }

    private void drainFused(final Subscriber<? super T> subscriber) {
        int missed = 1;

        for (;;) {
            if (cancelled) {
                queue.clear();
                downstream = null;
                return;
            }

            boolean d = done;

            subscriber.onNext(null);

            if (d) {
                downstream = null;

                Throwable ex = error;
                if (ex != null) {
                    subscriber.onError(ex);
                } else {
                    subscriber.onComplete();
                }
                return;
            }

            missed = WIP.addAndGet(this, -missed);

            if (missed == 0) {
                break;
            }
        }
    }

    private void drain() {
        if (WIP.getAndIncrement(this) != 0) {
            return;
        }

        int missed = 1;

        for (;;) {
            final Subscriber<? super T> subscriber = downstream;
            if (subscriber != null) {
                if (outputFused) {
                    drainFused(subscriber);
                } else {
                    drainRegular(subscriber);
                }
                return;
            }

            missed = WIP.addAndGet(this, -missed);

            if (missed == 0) {
                break;
            }
        }
    }

    private boolean checkTerminated(boolean d, boolean empty, Subscriber<? super T> subscriber, Queue<T> q) {
        if (cancelled) {
            q.clear();
            downstream = null;
            return true;
        }

        if (d && empty) {
            Throwable e = error;
            downstream = null;
            if (e != null) {
                subscriber.onError(e);
            } else {
                subscriber.onComplete();
            }
            return true;
        }

        return false;
    }

    @Override
    public void onNext(T t) {
        if (done || cancelled) {
            return;
        }

        while (!queue.offer(t)) {
            LockSupport.parkNanos(SPIN_LOCK_PARK_NANOS);
        }

        drain();
    }

    @Override
    public void onError(Throwable t) {
        if (done || cancelled) {
            return;
        }

        error = t;
        done = true;

        doTerminate();

        drain();
    }

    @Override
    public void onCompleted() {
        if (done || cancelled) {
            return;
        }

        done = true;

        doTerminate();

        drain();
    }

    @Override
    public void subscribe(Subscriber<? super T> actual) {
        checkNotNull(actual);

        if (state == UNSUBSCRIBED_STATE && STATE.compareAndSet(this, UNSUBSCRIBED_STATE, SUBSCRIBED_ONCE_STATE)) {
            actual.onSubscribe(this);
            this.downstream = actual;
            if (cancelled) {
                this.downstream = null;
            } else {
                drain();
            }
        } else {
            actual.onSubscribe(EMPTY_SUBSCRIPTION);
            actual.onError(new IllegalStateException(getClass().getSimpleName() + " allows only a single Subscriber"));
        }
    }

    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void request(long n) {
        if (n > 0) {

            addCap(REQUESTED, this, n);

            if (state == SUBSCRIBED_ONCE_STATE && STATE.compareAndSet(this, SUBSCRIBED_ONCE_STATE, PREFETCHED_ONCE_STATE)) {
                subscription.request(prefetch);
            }

            drain();
        }
    }

    /**
     * Concurrent addition bound to Long.MAX_VALUE.
     * Any concurrent write will "happen before" this operation.
     *
     * @param <T> the parent instance type
     * @param updater  current field updater
     * @param instance current instance to update
     * @param toAdd    delta to add
     * @return value before addition or Long.MAX_VALUE
     */
    private static <T> long addCap(AtomicLongFieldUpdater<T> updater, T instance, long toAdd) {
        long r, u;
        for (;;) {
            r = updater.get(instance);
            if (r == Long.MAX_VALUE) {
                return Long.MAX_VALUE;
            }

            u = r + toAdd;
            if (u < 0L) {
                u =  Long.MAX_VALUE;
            }

            if (updater.compareAndSet(instance, r, u)) {
                return r;
            }
        }
    }

    @Override
    public void cancel() {
        if (cancelled) {
            return;
        }
        cancelled = true;

        doOnCancel();
        doTerminate();

        if (!outputFused) {
            if (WIP.getAndIncrement(this) == 0) {
                queue.clear();
                downstream = null;
            }
        }
    }

    protected void doOnCancel() { }

    @Override
    public T poll() {
        T v = queue.poll();
        if (v != null) {
            int p = produced + 1;
            if (p == prefetch) {
                produced = 0;
                subscription.request(p);
            } else {
                produced = p;
            }
        }
        return v;
    }

    @Override
    public int size() {
        return queue.size();
    }

    @Override
    public boolean isEmpty() {
        return queue.isEmpty();
    }

    @Override
    public void clear() {
        queue.clear();
    }
}