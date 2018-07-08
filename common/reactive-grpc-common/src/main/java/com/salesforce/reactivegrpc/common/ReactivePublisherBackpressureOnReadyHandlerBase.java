/*
 *  Copyright (c) 2017, salesforce.com, inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.reactivegrpc.common;

import com.google.common.base.Preconditions;
import io.grpc.Status;
import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.CallStreamObserver;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * ReactivePublisherBackpressureOnReadyHandler bridges the manual flow control idioms of Reactive Streams and gRPC. This
 * class takes messages off of a {@link org.reactivestreams.Publisher} and feeds them into a {@link CallStreamObserver}
 * while respecting backpressure. This class is the inverse of {@link ReactiveStreamObserverPublisherBase}.
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
public abstract class ReactivePublisherBackpressureOnReadyHandlerBase<T> implements Subscriber<T>, Runnable {
    private CallStreamObserver<T> requestStream;
    private Subscription subscription;
    private AtomicBoolean canceled = new AtomicBoolean(false);
    private CountDownLatch subscribed = new CountDownLatch(1);

    // Guard against spurious onReady() calls caused by a race between onNext() and onReady(). If the transport
    // toggles isReady() from false to true while onNext() is executing, but before onNext() checks isReady(),
    // request(1) would be called twice - once by onNext() and once by the onReady() scheduled during onNext()'s
    // execution.
    private final AtomicBoolean wasReady = new AtomicBoolean(false);

    public ReactivePublisherBackpressureOnReadyHandlerBase(final CallStreamObserver<T> requestStream) {
        this.requestStream = checkNotNull(requestStream);
        requestStream.setOnReadyHandler(this);
    }

    @Override
    public void run() {
        try {
            subscribed.await();
        } catch (InterruptedException e) {

        }
        Preconditions.checkState(subscription != null, "onSubscribe() not yet called");
        if (!isCanceled() && requestStream.isReady() && wasReady.compareAndSet(false, true)) {
            // restart the pump
            subscription.request(1);
        }
    }

    public void cancel() {
        canceled.set(true);
        if (subscription != null) {
            subscription.cancel();
            subscription = null;
        }
    }

    public boolean isCanceled() {
        return canceled.get();
    }

    @Override
    public void onSubscribe(Subscription subscription) {
        checkNotNull(subscription);
        if (this.subscription != null) {
            subscription.cancel();
        } else {
            this.subscription = subscription;
            subscribed.countDown();
        }
    }

    // Calling onSubscribe, onNext, onError or onComplete MUST return normally except when any provided parameter is
    // null in which case it MUST throw a java.lang.NullPointerException to the caller, for all other situations the
    // only legal way for a Subscriber to signal failure is by cancelling its Subscription. In the case that this rule
    // is violated, any associated Subscription to the Subscriber MUST be considered as cancelled, and the caller MUST
    // raise this error condition in a fashion that is adequate for the runtime environment.
    // https://github.com/reactive-streams/reactive-streams-jvm/blob/v1.0.2/README.md#user-content-2.13

    @Override
    public void onNext(T t) {
        checkNotNull(t);
        if (!isCanceled()) {
            try {
                requestStream.onNext(t);
                if (requestStream.isReady()) {
                    // keep the pump going
                    subscription.request(1);
                } else {
                    // note that back-pressure has begun
                    wasReady.set(false);
                }
            } catch (Throwable throwable) {
                cancel();
                requestStream.onError(prepareError(throwable));
            }
        }
    }

    @Override
    public void onError(Throwable throwable) {
        if (!isCanceled()) {
            checkNotNull(throwable);
            try {
                requestStream.onError(prepareError(throwable));
            } catch (Throwable ignore) {
                cancel();
            }
        }
    }

    @Override
    public void onComplete() {
        if (!isCanceled()) {
            try {
                requestStream.onCompleted();
            } catch (Throwable throwable) {
                cancel();
                requestStream.onError(prepareError(throwable));
            }
        }
    }

    protected void cancelSubscription() {
        subscription.cancel();
    }

    private static Throwable prepareError(Throwable throwable) {
        if (throwable instanceof StatusException || throwable instanceof StatusRuntimeException) {
            return throwable;
        } else {
            return Status.fromThrowable(throwable).asException();
        }
    }
}
