/*
 *  Copyright (c) 2017, salesforce.com, inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.reactivegrpc.common;

import java.util.Collection;
import java.util.Iterator;
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
public abstract class AbstractStreamObserverAndPublisher<T>
        implements Publisher<T>, StreamObserver<T>, Subscription, Queue<T>  {

    static final String NOT_SUPPORTED_MESSAGE = "Although QueueSubscription extends Queue it is purely internal" +
            " and only guarantees support for poll/clear/size/isEmpty." +
            " Instances shouldn't be used/exposed as Queue outside of Reactor operators.";


    public static final int DEFAULT_CHUNK_SIZE = 16;

    static final int UNSUBSCRIBED_STATE = 0;
    static final int SUBSCRIBED_ONCE_STATE = 1;
    static final int PREFETCHED_ONCE_STATE = 2;

    protected volatile boolean outputFused;

    final Queue<T> queue;
    final int prefetch;

    final Consumer<CallStreamObserver<?>> onSubscribe;


    volatile boolean done;
    Throwable error;

    volatile Subscriber<? super T> downstream;

    volatile boolean cancelled;

    volatile CallStreamObserver<?> subscription;


    volatile Runnable onTerminate;
    @SuppressWarnings("rawtypes")
    static final AtomicReferenceFieldUpdater<AbstractStreamObserverAndPublisher, Runnable> ON_TERMINATE =
            AtomicReferenceFieldUpdater.newUpdater(AbstractStreamObserverAndPublisher.class, Runnable.class, "onTerminate");

    volatile int state;
    @SuppressWarnings("rawtypes")
    protected static final AtomicIntegerFieldUpdater<AbstractStreamObserverAndPublisher> STATE =
            AtomicIntegerFieldUpdater.newUpdater(AbstractStreamObserverAndPublisher.class, "state");

    volatile int wip;
    @SuppressWarnings("rawtypes")
    protected static final AtomicIntegerFieldUpdater<AbstractStreamObserverAndPublisher> WIP =
            AtomicIntegerFieldUpdater.newUpdater(AbstractStreamObserverAndPublisher.class, "wip");

    volatile long requested;
    @SuppressWarnings("rawtypes")
    protected static final AtomicLongFieldUpdater<AbstractStreamObserverAndPublisher> REQUESTED =
            AtomicLongFieldUpdater.newUpdater(AbstractStreamObserverAndPublisher.class, "requested");

    int produced;

    public AbstractStreamObserverAndPublisher(
            Queue<T> queue,
            Consumer<CallStreamObserver<?>> onSubscribe) {
        this(queue, DEFAULT_CHUNK_SIZE, onSubscribe);
    }

    public AbstractStreamObserverAndPublisher(
            Queue<T> queue,
            Consumer<CallStreamObserver<?>> onSubscribe,
            Runnable onTerminate) {
        this(queue, DEFAULT_CHUNK_SIZE, onSubscribe, onTerminate);
    }

    public AbstractStreamObserverAndPublisher(
            Queue<T> queue,
            int prefetch,
            Consumer<CallStreamObserver<?>> onSubscribe) {
        this(queue, prefetch, onSubscribe, null);
    }

    public AbstractStreamObserverAndPublisher(
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
        upstream.disableAutoInboundFlowControl();
        subscription = upstream;
        if (onSubscribe != null) {
            onSubscribe.accept(upstream);
        }
    }

    void doTerminate() {
        Runnable r = onTerminate;
        if (r != null && ON_TERMINATE.compareAndSet(this, r, null)) {
            r.run();
        }
    }

    void drainRegular(Subscriber<? super T> a) {
        int missed = 1;

        final Queue<T> q = queue;
        int sent = produced;

        for (;;) {
            long requested = this.requested;

            while (requested != sent) {
                boolean d = done;

                T t = q.poll();
                boolean empty = t == null;

                if (checkTerminated(d, empty, a, q)) {
                    return;
                }

                if (empty) {
                    break;
                }

                a.onNext(t);

                sent++;
            }

            if (requested == sent) {
                if (checkTerminated(done, q.isEmpty(), a, q)) {
                    return;
                }
            }

            if (sent == prefetch) {
                if (requested != Long.MAX_VALUE) {
                    REQUESTED.addAndGet(this, -sent);
                }
                subscription.request(sent);
                sent = 0;
            }

            int w = wip;
            if (missed == w) {
                produced = sent;
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

    void drainFused(Subscriber<? super T> a) {
        int missed = 1;

        final Queue<T> q = queue;

        for (;;) {

            if (cancelled) {
                q.clear();
                downstream = null;
                return;
            }

            boolean d = done;

            a.onNext(null);

            if (d) {
                downstream = null;

                Throwable ex = error;
                if (ex != null) {
                    a.onError(ex);
                } else {
                    a.onComplete();
                }
                return;
            }

            missed = WIP.addAndGet(this, -missed);
            if (missed == 0) {
                break;
            }
        }
    }

    void drain() {
        if (WIP.getAndIncrement(this) != 0) {
            return;
        }

        int missed = 1;

        for (;;) {
            Subscriber<? super T> a = downstream;
            if (a != null) {

                if (outputFused) {
                    drainFused(a);
                } else {
                    drainRegular(a);
                }
                return;
            }

            missed = WIP.addAndGet(this, -missed);
            if (missed == 0) {
                break;
            }
        }
    }

    boolean checkTerminated(boolean d, boolean empty, Subscriber<? super T> a, Queue<T> q) {
        if (cancelled) {
            q.clear();
            downstream = null;
            return true;
        }
        if (d && empty) {
            Throwable e = error;
            downstream = null;
            if (e != null) {
                a.onError(e);
            } else {
                a.onComplete();
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

        Queue<T> q = this.queue;

        while (!q.offer(t)) {
            LockSupport.parkNanos(10);
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
        if (state == UNSUBSCRIBED_STATE && STATE.compareAndSet(this, UNSUBSCRIBED_STATE, SUBSCRIBED_ONCE_STATE)) {

            actual.onSubscribe(this);
            this.downstream = actual;
            if (cancelled) {
                this.downstream = null;
            } else {
                drain();
            }
        } else {
            actual.onSubscribe(Operators.EmptySubscription.INSTANCE);
            actual.onError(new IllegalStateException("UnicastProcessor allows only a single Subscriber"));
        }
    }

    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void request(long n) {
        if (Operators.validate(n)) {

            Operators.addCap(REQUESTED, this, n);

            if (state == SUBSCRIBED_ONCE_STATE && STATE.compareAndSet(this, SUBSCRIBED_ONCE_STATE, PREFETCHED_ONCE_STATE)){
                subscription.request(prefetch);
            }

            drain();
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
            }
            else {
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

    @Override
    public T peek() {
        throw new UnsupportedOperationException(NOT_SUPPORTED_MESSAGE);
    }

    @Override
    public boolean add( T t) {
        throw new UnsupportedOperationException(NOT_SUPPORTED_MESSAGE);
    }

    @Override
    public boolean offer(T t) {
        throw new UnsupportedOperationException(NOT_SUPPORTED_MESSAGE);
    }

    @Override
    public T remove() {
        throw new UnsupportedOperationException(NOT_SUPPORTED_MESSAGE);
    }

    @Override
    public T element() {
        throw new UnsupportedOperationException(NOT_SUPPORTED_MESSAGE);
    }

    @Override
    public boolean contains(Object o) {
        throw new UnsupportedOperationException(NOT_SUPPORTED_MESSAGE);
    }

    @Override
    public Iterator<T> iterator() {
        throw new UnsupportedOperationException(NOT_SUPPORTED_MESSAGE);
    }

    @Override
    public Object[] toArray() {
        throw new UnsupportedOperationException(NOT_SUPPORTED_MESSAGE);
    }

    @Override
    public <T1> T1[] toArray(T1[] a) {
        throw new UnsupportedOperationException(NOT_SUPPORTED_MESSAGE);
    }

    @Override
    public boolean remove(Object o) {
        throw new UnsupportedOperationException(NOT_SUPPORTED_MESSAGE);
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        throw new UnsupportedOperationException(NOT_SUPPORTED_MESSAGE);
    }

    @Override
    public boolean addAll(Collection<? extends T> c) {
        throw new UnsupportedOperationException(NOT_SUPPORTED_MESSAGE);
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        throw new UnsupportedOperationException(NOT_SUPPORTED_MESSAGE);
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        throw new UnsupportedOperationException(NOT_SUPPORTED_MESSAGE);
    }
}