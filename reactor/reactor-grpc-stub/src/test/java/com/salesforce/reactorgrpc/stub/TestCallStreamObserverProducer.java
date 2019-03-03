/*
 *  Copyright (c) 2019, Salesforce.com, Inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */
package com.salesforce.reactorgrpc.stub;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import javax.annotation.Nullable;

import io.grpc.stub.ClientCallStreamObserver;
import io.grpc.stub.StreamObserver;
import reactor.core.publisher.Operators;

/**
 * This class is an implementation of GRPC based Range Publisher. Note, implementation
 * sends data on the specified ExecutorService e.g simulates observerOn behaviours
 */
public class TestCallStreamObserverProducer extends ClientCallStreamObserver<Integer> {

    private static final long serialVersionUID = 2587302975077663557L;

    final Queue<Integer>                  requestsQueue = new ConcurrentLinkedQueue<Integer>();
    final int                             end;
    final ExecutorService                 executorService;
    final StreamObserver<? super Integer> actual;

    int index;

    volatile long requested;
    public static final AtomicLongFieldUpdater<TestCallStreamObserverProducer> REQUESTED =
        AtomicLongFieldUpdater.newUpdater(TestCallStreamObserverProducer.class, "requested");

    volatile boolean cancelled;

    TestCallStreamObserverProducer(ExecutorService executorService, StreamObserver<? super Integer> actual, int end) {
        this.end = end;
        this.actual = actual;
        this.executorService = executorService;
    }

    void slowPath(long r) {
        long e = 0;
        int f = end;
        int i = index;
        StreamObserver<? super Integer> a = actual;

        for (;;) {

            while (e != r && i != f) {
                if (cancelled) {
                    return;
                }

                a.onNext(i);

                e++;
                i++;
            }

            if (i == f) {
                if (!cancelled) {
                    a.onCompleted();
                }
                return;
            }

            r = requested;
            if (e == r) {
                index = i;
                r = REQUESTED.addAndGet(this, -e);
                if (r == 0L) {
                    return;
                }
                e = 0L;
            }
        }
    }

    @Override
    public void request(final int n) {
        if (Operators.validate(n)) {
            requestsQueue.add(n);
            if (add(this, n) == 0L) {
                executorService.execute(() -> slowPath(n));
            }
        }
    }

    static long add(TestCallStreamObserverProducer o, long n) {
        for (;;) {
            long r = REQUESTED.get(o);
            if (r == Long.MAX_VALUE) {
                return Long.MAX_VALUE;
            }
            long u = Operators.addCap(r, n);
            if ((REQUESTED).compareAndSet(o, r, u)) {
                return r;
            }
        }
    }

    @Override
    public boolean isReady() {
        return true;
    }

    @Override
    public void cancel(@Nullable String message, @Nullable Throwable cause) {
        cancelled = true;
    }

    @Override
    public void onNext(Integer value) {

    }

    @Override
    public void onError(Throwable t) {

    }

    @Override
    public void onCompleted() {

    }

    @Override
    public void setMessageCompression(boolean enable) {

    }

    @Override
    public void setOnReadyHandler(Runnable onReadyHandler) {

    }

    @Override
    public void disableAutoInboundFlowControl() {

    }
}

