/*
 *  Copyright (c) 2019, Salesforce.com, Inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */
package com.salesforce.reactivegrpc.common;

import java.util.Collection;
import java.util.Iterator;
import java.util.Queue;

import io.reactivex.exceptions.Exceptions;
import io.reactivex.rxjava3.internal.fuseable.QueueSubscription;

/**
 * Implementation of FusionModeAwareSubscription which encapsulate
 * {@link QueueSubscription} from RxJava internals and allows treat it as a {@link Queue}.
 *
 * @param <T> generic type
 */
public class FusionAwareQueueSubscriptionAdapterRx3<T> implements Queue<T>, QueueSubscription<T>, FusionModeAwareSubscription {

    static final String NOT_SUPPORTED_MESSAGE = "Although FusionAwareQueueSubscriptionAdapter implements Queue it is" +
        " purely internal and only guarantees support for poll/clear/size/isEmpty." +
        " Instances shouldn't be used/exposed as Queue outside of RxGrpc operators.";

    private final QueueSubscription<T> delegate;
    private final int                  mode;

    public FusionAwareQueueSubscriptionAdapterRx3(QueueSubscription<T> delegate, int mode) {
        this.delegate = delegate;
        this.mode = mode;
    }

    @Override
    public int mode() {
        return mode;
    }

    @Override
    public int requestFusion(int mode) {
        return delegate.requestFusion(mode);
    }

    @Override
    public void request(long l) {
        delegate.request(l);
    }

    @Override
    public void cancel() {
        delegate.cancel();
    }

    @Override
    public T poll() {
        try {
            return delegate.poll();
        } catch (Throwable e) {
            throw Exceptions.propagate(e);
        }
    }

    @Override
    public boolean offer(T t) {
        return delegate.offer(t);
    }

    @Override
    public boolean offer(T v1, T v2) {
        return delegate.offer(v1, v2);
    }

    @Override
    public boolean isEmpty() {
        return delegate.isEmpty();
    }

    @Override
    public void clear() {
        delegate.clear();
    }

    @Override
    public int size() {
        throw new UnsupportedOperationException(NOT_SUPPORTED_MESSAGE);
    }


    @Override
    public T peek() {
        throw new UnsupportedOperationException(NOT_SUPPORTED_MESSAGE);
    }

    @Override
    public boolean add(T t) {
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
