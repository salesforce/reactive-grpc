/*
 *  Copyright (c) 2017, salesforce.com, inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.reactivegrpc.common;

import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.Runnables;
import io.grpc.Status;
import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.CallStreamObserver;
import io.grpc.stub.ClientCallStreamObserver;
import io.grpc.stub.ServerCallStreamObserver;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * ReactivePublisherBackpressureOnReadyHandler bridges the manual flow control idioms of Reactive Streams and gRPC. This class takes
 * messages off of a {@link org.reactivestreams.Publisher} and feeds them into a {@link CallStreamObserver}
 * while respecting backpressure. This class is the inverse of {@link ReactiveStreamObserverPublisher}.
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
public class ReactivePublisherBackpressureOnReadyHandler<T> implements Subscriber<T>, Runnable {
    private CallStreamObserver<T> requestStream;
    private Subscription subscription;
    private AtomicBoolean canceled = new AtomicBoolean(false);
    private CountDownLatch subscribed = new CountDownLatch(1);
    private Runnable cancelRequestStream = Runnables.doNothing();

    // Guard against spurious onReady() calls caused by a race between onNext() and onReady(). If the transport
    // toggles isReady() from false to true while onNext() is executing, but before onNext() checks isReady(),
    // request(1) would be called twice - once by onNext() and once by the onReady() scheduled during onNext()'s
    // execution.
    private final AtomicBoolean wasReady = new AtomicBoolean(false);

    public ReactivePublisherBackpressureOnReadyHandler(final ClientCallStreamObserver<T> requestStream) {
        this.requestStream = Preconditions.checkNotNull(requestStream);
        requestStream.setOnReadyHandler(this);
        cancelRequestStream = new Runnable() {
            @Override
            public void run() {
                requestStream.cancel("Cancelled", Status.CANCELLED.asException());
            }
        };
    }

    public ReactivePublisherBackpressureOnReadyHandler(ServerCallStreamObserver<T> requestStream) {
        this.requestStream = Preconditions.checkNotNull(requestStream);
        requestStream.setOnReadyHandler(this);
        requestStream.setOnCancelHandler(new Runnable() {
            @Override
            public void run() {
                subscription.cancel();
            }
        });
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
        cancelRequestStream.run();
    }

    public boolean isCanceled() {
        return canceled.get();
    }

    @Override
    public void onSubscribe(Subscription subscription) {
        if (this.subscription != null) {
            subscription.cancel();
        } else {
            this.subscription = Preconditions.checkNotNull(subscription);
            subscribed.countDown();
        }
    }

    @Override
    public void onNext(T t) {
        if (!isCanceled()) {
            requestStream.onNext(Preconditions.checkNotNull(t));
            if (requestStream.isReady()) {
                // keep the pump going
                subscription.request(1);
            } else {
                // note that back-pressure has begun
                wasReady.set(false);
            }
        }
    }

    @Override
    public void onError(Throwable throwable) {
        requestStream.onError(prepareError(Preconditions.checkNotNull(throwable)));
    }

    @Override
    public void onComplete() {
        if (!isCanceled()) {
            requestStream.onCompleted();
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
