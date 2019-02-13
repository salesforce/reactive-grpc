/*
 *  Copyright (c) 2017, salesforce.com, inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */
package com.salesforce.rxgrpc.stub;

import io.reactivex.exceptions.Exceptions;
import io.reactivex.internal.fuseable.SimpleQueue;

import java.util.Collection;
import java.util.Iterator;
import java.util.Queue;

/**
 * Adapts the RxJava {@code SimpleQueue} interface to a common java {@link Queue}.
 * @param <T>
 */
final class SimpleQueueAdapter<T> implements Queue<T>, SimpleQueue<T> {

    final SimpleQueue<T> simpleQueue;

    SimpleQueueAdapter(SimpleQueue<T> queue) {
        simpleQueue = queue;
    }

    @Override
    public T poll() {
        try {
            return simpleQueue.poll();
        }
        catch (Exception e) {
            throw Exceptions.propagate(e);
        }
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

    @Override
    public T peek() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean add(T t) {
        throw new UnsupportedOperationException();
    }

    @Override
    public T remove() {
        throw new UnsupportedOperationException();
    }

    @Override
    public T element() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean contains(Object o) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Iterator<T> iterator() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object[] toArray() {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T1> T1[] toArray(T1[] a) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean remove(Object o) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean addAll(Collection<? extends T> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int size() {
        throw new UnsupportedOperationException();
    }
}
