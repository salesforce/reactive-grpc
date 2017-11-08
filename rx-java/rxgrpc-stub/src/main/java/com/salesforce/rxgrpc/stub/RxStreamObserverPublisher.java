/*
 *  Copyright (c) 2017, salesforce.com, inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.rxgrpc.stub;

import com.google.common.base.Preconditions;
import io.grpc.Status;
import io.grpc.stub.CallStreamObserver;
import io.grpc.stub.ClientCallStreamObserver;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.concurrent.CountDownLatch;

/**
 * RxStreamObserverPublisher bridges the manual flow control idioms of gRPC and RxJava. This class takes
 * messages off of a {@link StreamObserver} and feeds them into a {@link Publisher} while respecting backpressure. This
 * class is the inverse of {@link RxFlowableBackpressureOnReadyHandler}.
 * <p>
 * When a {@link Publisher} is subscribed to by a {@link Subscriber}, the {@code Publisher} hands the {@code Subscriber}
 * a {@link Subscription}. When the {@code Subscriber} wants more messages from the {@code Publisher}, the
 * {@code Subscriber} calls {@link Subscription#request(long)}.
 * <p>
 * gRPC also uses the {@link CallStreamObserver#request(int)} idiom to request more messages from the stream.
 * <p>
 * To bridge the two idioms: this class implements a {@code Publisher} which delegates calls to {@code request()} to
 * a {@link CallStreamObserver} set in the constructor. When a message is generated as a response, the message is
 * delegated in the reverse so the {@code Publisher} can announce it to RxJava.
 *
 * @param <T>
 */
public class RxStreamObserverPublisher<T> implements Publisher<T>, StreamObserver<T> {
    private CallStreamObserver callStreamObserver;
    private Subscriber<? super T> subscriber;
    private volatile boolean isCanceled;

    // A gRPC server can sometimes send messages before subscribe() has been called and the consumer may not have
    // finished setting up the consumer pipeline. Use a countdown latch to prevent messages from processing before
    // subscribe() has been called.
    private CountDownLatch subscribed = new CountDownLatch(1);

    public RxStreamObserverPublisher(CallStreamObserver callStreamObserver) {
        Preconditions.checkNotNull(callStreamObserver);
        this.callStreamObserver = callStreamObserver;
        callStreamObserver.disableAutoInboundFlowControl();
    }

    @Override
    public void subscribe(Subscriber<? super T> subscriber) {
        Preconditions.checkNotNull(subscriber);
        subscriber.onSubscribe(new Subscription() {
            @Override
            public void request(long l) {
                // RxJava uses Long.MAX_VALUE to indicate "all messages"; gRPC uses Integer.MAX_VALUE.
                int i = (int) Long.min(l, Integer.MAX_VALUE);

                // Very rarely, request() gets called before the client has finished setting up its stream. If this
                // happens, wait momentarily and try again.
                try {
                    callStreamObserver.request(i);
                } catch (IllegalStateException ex) {
                    try {
                        Thread.sleep(2);
                    } catch (InterruptedException e) {
                        // no-op
                    }
                    callStreamObserver.request(i);
                }
            }

            @Override
            public void cancel() {
                // Don't cancel twice if the server is already canceled
                if (callStreamObserver instanceof ServerCallStreamObserver && ((ServerCallStreamObserver) callStreamObserver).isCancelled()) {
                    return;
                }

                isCanceled = true;
                if (callStreamObserver instanceof ClientCallStreamObserver) {
                    ((ClientCallStreamObserver) callStreamObserver).cancel("Client canceled request", null);
                } else {
                    callStreamObserver.onError(Status.CANCELLED.withDescription("Server canceled request").asRuntimeException());
                }
            }
        });
        this.subscriber = subscriber;

        subscribed.countDown();
    }

    @Override
    public void onNext(T value) {
        try {
            subscribed.await();
        } catch (InterruptedException e) {

        }
        subscriber.onNext(Preconditions.checkNotNull(value));
    }

    @Override
    public void onError(Throwable t) {
        try {
            subscribed.await();
        } catch (InterruptedException e) {

        }

        subscriber.onError(Preconditions.checkNotNull(t));
        // Release the subscriber, we don't need a reference to it anymore
        subscriber = null;
    }

    @Override
    public void onCompleted() {
        try {
            subscribed.await();
        } catch (InterruptedException e) {

        }
        subscriber.onComplete();
        // Release the subscriber, we don't need a reference to it anymore
        subscriber = null;
    }

    public boolean isCanceled() {
        return isCanceled;
    }
}
