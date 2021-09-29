/*
 *  Copyright (c) 2019, Salesforce.com, Inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */
package com.salesforce.rx3grpc.stub;

import com.salesforce.reactivegrpc.common.AbstractUnimplementedQueue;

import io.reactivex.rxjava3.operators.SimplePlainQueue;

/**
 * Adapts the RxJava {@code SimpleQueue} interface to a common java {@link java.util.Queue}.
 * @param <T> T
 */
final class SimpleQueueAdapter<T> extends AbstractUnimplementedQueue<T> implements SimplePlainQueue<T> {

    private final SimplePlainQueue<T> simpleQueue;

    SimpleQueueAdapter(SimplePlainQueue<T> queue) {
        simpleQueue = queue;
    }

    @Override
    public T poll() {
        return simpleQueue.poll();
    }

    @Override
    public boolean isEmpty() {
        return simpleQueue.isEmpty();
    }

    @Override
    public void clear() {
        simpleQueue.clear();
    }

    @Override
    public boolean offer(T t) {
        return simpleQueue.offer(t);
    }

    @Override
    public boolean offer(T t1, T t2) {
        return simpleQueue.offer(t1, t2);
    }

    @Override
    public boolean equals(Object o) {
        return simpleQueue.equals(o);
    }

    @Override
    public int hashCode() {
        return simpleQueue.hashCode();
    }
}
