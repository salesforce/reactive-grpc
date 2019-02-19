/*  Copyright (c) 2019, Salesforce.com, Inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.reactivegrpc.common;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.Nullable;

import io.grpc.stub.ClientCallStreamObserver;
import io.grpc.stub.StreamObserver;

public class AsyncRangeCallStreamObserver extends ClientCallStreamObserver<Long> {

    final Queue<Integer>  requestsQueue = new ConcurrentLinkedQueue<Integer>();
    final AtomicLong      requested     = new AtomicLong();

    final ExecutorService      executorService;
    final StreamObserver<Long> subscriber;

    final long max;


    volatile boolean cancelled;

    long index = 0;

    public AsyncRangeCallStreamObserver(
            ExecutorService executorService,
            StreamObserver<Long> subscriber,
            long max) {
        this.executorService = executorService;
        this.subscriber = subscriber;
        this.max = max;
    }

    @Override
    public void cancel(@Nullable String message, @Nullable Throwable cause) {
        cancelled = true;
    }

    @Override
    public boolean isReady() {
        return true;
    }

    @Override
    public void onNext(Long value) {

    }

    @Override
    public void onError(Throwable t) {

    }

    @Override
    public void onCompleted() {

    }

    @Override
    public void setOnReadyHandler(Runnable onReadyHandler) {

    }

    @Override
    public void disableAutoInboundFlowControl() {

    }

    @Override
    public void request(int count) {
        requestsQueue.add(count);

        long n = count;

        long initialRequested;

        do {
            initialRequested = requested.get();

            if (initialRequested == Long.MAX_VALUE) {
                return;
            }

            n = initialRequested + n;

            if (n <= 0) {
                n = Long.MAX_VALUE;
            }

        } while (!requested.compareAndSet(initialRequested, n));

        if (initialRequested > 0) {
            return;
        }

        executorService.execute(new Runnable() {
            @Override
            public void run() {
                int sent = 0;

                while (true) {

                    for (; sent < requested.get() && index < max; sent++, index++) {

                        if (cancelled) {
                            return;
                        }

                        subscriber.onNext(index);
                    }

                    if (cancelled) {
                        return;
                    }

                    if (index == max) {
                        subscriber.onCompleted();
                        return;
                    }

                    if (requested.addAndGet(-sent) == 0) {
                        return;
                    }

                    sent = 0;
                }
            }
        });
    }

    @Override
    public void setMessageCompression(boolean enable) {

    }
}
