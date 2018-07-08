/*
 *  Copyright (c) 2017, salesforce.com, inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.reactivegrpc.common;

import com.google.common.base.Preconditions;
import io.grpc.stub.CallStreamObserver;
import io.grpc.stub.StreamObserver;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.concurrent.CountDownLatch;

/**
 * ReactiveStreamObserverPublisher bridges the manual flow control idioms of gRPC and Reactive Streams. This class takes
 * messages off of a {@link StreamObserver} and feeds them into a {@link Publisher} while respecting backpressure. This
 * class is the inverse of {@link ReactivePublisherBackpressureOnReadyHandlerBase}.
 * <p>
 * When a {@link Publisher} is subscribed to by a {@link Subscriber}, the {@code Publisher} hands the {@code Subscriber}
 * a {@link Subscription}. When the {@code Subscriber} wants more messages from the {@code Publisher}, the
 * {@code Subscriber} calls {@link Subscription#request(long)}.
 * <p>
 * gRPC also uses the {@link io.grpc.stub.CallStreamObserver#request(int)} idiom to request more messages from the stream.
 * <p>
 * To bridge the two idioms: this class implements a {@code Publisher} which delegates calls to {@code request()} to
 * a {@link io.grpc.stub.CallStreamObserver} set in the constructor. When a message is generated as a response, the message is
 * delegated in the reverse so the {@code Publisher} can announce it to the Reactive Streams implementation.
 *
 * @param <T>
 */
public abstract class ReactiveStreamObserverPublisherBase<T> implements Publisher<T>, StreamObserver<T> {
    private CallStreamObserver callStreamObserver;
    private Subscriber<? super T> subscriber;
    private volatile boolean isCanceled;

    // A gRPC server can sometimes send messages before subscribe() has been called and the consumer may not have
    // finished setting up the consumer pipeline. Use a countdown latch to prevent messages from processing before
    // subscribe() has been called.
    private CountDownLatch subscribed = new CountDownLatch(1);

    public ReactiveStreamObserverPublisherBase(CallStreamObserver callStreamObserver) {
        Preconditions.checkNotNull(callStreamObserver);
        this.callStreamObserver = callStreamObserver;
        callStreamObserver.disableAutoInboundFlowControl();
    }

    @Override
    public void subscribe(Subscriber<? super T> subscriber) {
        Preconditions.checkNotNull(subscriber);
        subscriber.onSubscribe(createSubscription());
        this.subscriber = subscriber;

        subscribed.countDown();
    }

    /**
     * Overridden by client and server implementations to define cancellation.
     */
    protected abstract ReactiveStreamObserverPublisherSubscriptionBase createSubscription();

    /**
     * Base class for client and server subscription implementations. Client and server implementations must provide
     * their own implementations of cancel().
     */
    protected abstract class ReactiveStreamObserverPublisherSubscriptionBase implements Subscription {
        private static final int MAX_REQUEST_RETRIES = 20;

        @Override
        public void request(long l) {
            // Some Reactive Streams implementations use Long.MAX_VALUE to indicate "all messages"; gRPC uses Integer.MAX_VALUE.
            int i = (int) Math.min(l, Integer.MAX_VALUE);

            // Very rarely, request() gets called before the client has finished setting up its stream. If this
            // happens, wait momentarily and try again.
            for (int j = 0; j < MAX_REQUEST_RETRIES; j++) {
                try {
                    callStreamObserver.request(i);
                    break;
                } catch (IllegalStateException ex) {
                    if (j == MAX_REQUEST_RETRIES - 1) {
                        throw ex;
                    }
                    try {
                        Thread.sleep(1);
                    } catch (InterruptedException e) {
                        // no-op
                    }
                }
            }
        }
    }

    @Override
    public void onNext(T value) {
        if (!isCanceled()) {
            try {
                subscribed.await();
            } catch (InterruptedException e) {

            }
            subscriber.onNext(Preconditions.checkNotNull(value));
        }
    }

    @Override
    public void onError(Throwable t) {
        if (!isCanceled()) {
            try {
                subscribed.await();
            } catch (InterruptedException e) {

            }
            subscriber.onError(Preconditions.checkNotNull(t));
            // Release the subscriber, we don't need a reference to it anymore
            subscriber = null;
            callStreamObserver = null;
        }
    }

    @Override
    public void onCompleted() {
        if (!isCanceled()) {
            try {
                subscribed.await();
            } catch (InterruptedException e) {

            }
            subscriber.onComplete();
            // Release the subscriber, we don't need a reference to it anymore
            subscriber = null;
            callStreamObserver = null;
        }
    }

    public boolean isCanceled() {
        return isCanceled;
    }

    protected void cancel() {
        isCanceled = true;
    }

    protected void freeSubscriber() {
        subscriber = null;
    }
}