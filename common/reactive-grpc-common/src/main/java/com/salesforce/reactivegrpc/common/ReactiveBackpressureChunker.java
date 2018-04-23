/*
 *  Copyright (c) 2017, salesforce.com, inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.reactivegrpc.common;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

/**
 * {@code ReactiveBackpressureChunker} adapts Reactive Streams backpressure protocol to gRPC backpressure protocol.
 * <p>
 * When a reactive stream consumer wants to request the next message in a stream, it calls {@link Subscription#request(long)}.
 * To request an infinite stream, the consumer calls {@code request(MAX_VALUE)}.
 * <p>
 * This protocol causes trouble with gRPC. gRPC's automatic flow control uses request(1)/onNext() to get one message at
 * a time from the server. When gRPC receives a {@code request(MAX_VALUE)} call, it starts producing messages as fast
 * as it can, assuming the consumer actually has the capacity for MAX_VALUE messages right now. This deluge of messages
 * can overwhelm any stages in the downstream reactive stream that buffer messages, resulting in missing backpressure
 * exceptions.
 * <p>
 * {@code ReactiveBackpressureChunker} solves the impedance mismatch between Reactive Stream and gRPC backpressure
 * protocols by chunking large calls to {@link Subscription#request(long)} into many smaller {@code request()}s to
 * gRPC. Once a chunk is satisfied, another chunk is requested, on and on until the original request is satisfied.
 *
 * @param <T> the type of response message
 */
public class ReactiveBackpressureChunker<T> {
    public static final int DEFAULT_CHUNK_SIZE = 16;

    private final long chunkSize;

    public ReactiveBackpressureChunker(long chunkSize) {
        this.chunkSize = chunkSize;
    }

    public Subscriber<? super T> apply(final Subscriber<? super T> downstream) {
        return new Subscriber<T>() {
            private Subscription subscription;

            // The number of messages we have actually received from the sender
            private long have = 0;
            // The final number of messages we are trying to acquire
            private long want = 0;
            // The number of messages we have requested from the sender
            private long outstanding = 0;

            @Override
            public void onSubscribe(final Subscription subscription) {
                this.subscription = subscription;
                downstream.onSubscribe(new Subscription() {
                    @Override
                    public void request(long r) {
                        // Increase the number of messages we want
                        want += r;
                        maybeRequestMore();
                    }

                    @Override
                    public void cancel() {
                        subscription.cancel();
                    }
                });
            }

            @Override
            public void onNext(T t) {
                downstream.onNext(t);
                // Increment the number of messages we have
                have += 1;
                maybeRequestMore();
            }

            @Override
            public void onError(Throwable throwable) {
                downstream.onError(throwable);
            }

            @Override
            public void onComplete() {
                downstream.onComplete();
            }

            private void maybeRequestMore() {
                if (have < want) {
                    if (have >= outstanding) {
                        outstanding += chunkSize;
                        subscription.request(chunkSize);
                    }
                }
            }
        };
    }
}
