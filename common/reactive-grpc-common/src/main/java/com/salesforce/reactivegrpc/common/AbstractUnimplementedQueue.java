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

/**
 * {@code AbstractUnimplementedQueue} provides a base class for implementing reactive operation fusion queues in a
 * framework agnostic way. RxJava and Reactor both have the concept of a "simple queue", which lacks most of the
 * complexity of {@code java.util.Queue}, however, these interfaces are not interoperable when writing shared code.
 *
 * <p>This class implements the unused (not "simple") parts {@code java.util.Queue} such that they throw
 * {@code UnsupportedOperationException} so that subclasses only have to implement the "simple" parts.
 * @param <T> T
 */
public abstract class AbstractUnimplementedQueue<T> implements Queue<T> {
    private final String NOT_SUPPORTED_MESSAGE = "Although " + getClass().getSimpleName() +
            " implements Queue it is purely internal and only guarantees support for poll/clear/size/isEmpty." +
            " Instances shouldn't be used/exposed as Queue outside of RxGrpc operators.";

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
    public boolean add(T t) {
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

    @Override
    public boolean offer(T t) {
        throw new UnsupportedOperationException(NOT_SUPPORTED_MESSAGE);
    }

    public boolean offer(T t, T t1) {
        throw new UnsupportedOperationException(NOT_SUPPORTED_MESSAGE);
    }

    @Override
    public T remove() {
        throw new UnsupportedOperationException(NOT_SUPPORTED_MESSAGE);
    }

    @Override
    public T poll() {
        throw new UnsupportedOperationException(NOT_SUPPORTED_MESSAGE);
    }

    @Override
    public T element() {
        throw new UnsupportedOperationException(NOT_SUPPORTED_MESSAGE);
    }

    @Override
    public T peek() {
        throw new UnsupportedOperationException(NOT_SUPPORTED_MESSAGE);
    }

    @Override
    public int size() {
        throw new UnsupportedOperationException(NOT_SUPPORTED_MESSAGE);
    }
}
