/*
 *  Copyright (c) 2019, salesforce.com, inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

/*
 * Copyright (c) 2011-2017 Pivotal Software Inc, All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.salesforce.rxgrpc.stub;

import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicReference;

import io.reactivex.exceptions.Exceptions;
import io.reactivex.internal.fuseable.SimplePlainQueue;
import io.reactivex.internal.fuseable.SimpleQueue;
import io.reactivex.internal.queue.MpscLinkedQueue;
import io.reactivex.internal.queue.SpscArrayQueue;
import io.reactivex.internal.queue.SpscLinkedArrayQueue;

/**
 * Queue utilities and suppliers for 1-producer/1-consumer ready queues adapted for
 * various given capacities.
 */
public final class Queues {

	/**
	 * An allocation friendly default of available slots in a given container, e.g. slow publishers and or fast/few
	 * subscribers
	 */
	public static final int XS_BUFFER_SIZE    = Math.max(8, Integer.parseInt(System.getProperty("rx.bufferSize.x", "32")));
	/**
	 * A small default of available slots in a given container, compromise between intensive pipelines, small
	 * subscribers numbers and memory use.
	 */
	public static final int SMALL_BUFFER_SIZE = Math.max(16, Integer.parseInt(System.getProperty("rx.bufferSize.small", "256")));

	/**
	 * Adapts {@link SimplePlainQueue} to generic {@link Queue}
	 *
	 * @param simplePlainQueue
	 * @param <T>
	 * @return
	 */
	public static <T> Queue<T> toQueue(SimpleQueue<T> simplePlainQueue) {
		return new SimpleQueueAdapter<T>(simplePlainQueue);
	}

	/**
	 *
	 * @param batchSize the bounded or unbounded (int.max) queue size
	 * @param <T> the reified {@link Queue} generic type
	 * @return an unbounded or bounded {@link Queue}
	 */
	@SuppressWarnings("unchecked")
	public static <T> Queue<T> get(int batchSize) {
		if (batchSize == Integer.MAX_VALUE) {
			return unbounded();
		}
		if (batchSize == XS_BUFFER_SIZE) {
			return xs();
		}
		if (batchSize == SMALL_BUFFER_SIZE) {
			return small();
		}
		if (batchSize == 1) {
			return one();
		}
		if (batchSize == 0) {
			return empty();
		}

		final int adjustedBatchSize = Math.max(8, batchSize);
		if (adjustedBatchSize > 10000000) {
			return unbounded();
		}
		else {
			return new SimpleQueueAdapter<T>(new SpscArrayQueue<T>(adjustedBatchSize));
		}
	}

	/**
	 * An empty immutable {@link Queue}, to be used as a placeholder
	 * in methods that require a Queue when one doesn't expect to store any data in said
	 * Queue.
	 *
	 * @param <T> the reified {@link Queue} generic type
	 * @return an immutable empty {@link Queue}
	 */
	public static <T> Queue<T> empty() {
		return new ZeroQueue<T>();
	}

	/**
	 *
	 * @param <T> the reified {@link Queue} generic type
	 * @return a bounded {@link Queue}
	 */
	public static <T> Queue<T> one() {
		return new OneQueue<T>();
	}

	/**
	 *
	 * @param <T> the reified {@link Queue} generic type
	 * @return a bounded {@link Queue}
	 */
	public static <T> Queue<T> xs() {
		return new SimpleQueueAdapter<T>(new SpscArrayQueue<T>(XS_BUFFER_SIZE));
	}

	/**
	 * @param <T> the reified {@link Queue} generic type
	 *
	 * @return a bounded {@link Queue}
	 */
	public static <T> Queue<T> small() {
		return new SimpleQueueAdapter<T>(new SpscArrayQueue<T>(SMALL_BUFFER_SIZE));
	}

	/**
	 *
	 * @param <T> the reified {@link Queue} generic type
	 * @return an unbounded {@link Queue}
	 */
	public static <T> Queue<T> unbounded() {
		return new SimpleQueueAdapter<T>(new SpscLinkedArrayQueue<T>(SMALL_BUFFER_SIZE));
	}

	/**
	 * Returns an unbounded, linked-array-based Queue. Integer.max sized link will
	 * return the default {@link #SMALL_BUFFER_SIZE} size.
	 * @param linkSize the link size
	 * @param <T> the reified {@link Queue} generic type
	 * @return an unbounded {@link Queue}
	 */
	public static <T> Queue<T> unbounded(final int linkSize) {
		if (linkSize == Integer.MAX_VALUE || linkSize == SMALL_BUFFER_SIZE) {
			return unbounded();
		}
		return new SimpleQueueAdapter<T>(new SpscLinkedArrayQueue<T>(linkSize));
	}

	/**
	 * Returns an unbounded queue suitable for multi-producer/single-consumer (MPSC)
	 * scenarios.
	 *
	 * @param <T> the reified {@link Queue} generic type
	 * @return an unbounded MPSC {@link Queue}
	 */
	public static <T> Queue<T> unboundedMultiproducer() {
		return new SimpleQueueAdapter<T>(new MpscLinkedQueue<T>());
	}

	private Queues() {
		//prevent construction
	}

	static final class OneQueue<T> extends AtomicReference<T> implements Queue<T> {
        @Override
		public boolean add(T t) {

		    while (!offer(t));

		    return true;
		}

		@Override
		public boolean addAll(Collection<? extends T> c) {
			return false;
		}

		@Override
		public void clear() {
			set(null);
		}

		@Override
		public boolean contains(Object b) {
			T a = get();
			return (a == b) || (a != null && a.equals(b));
		}

		@Override
		public boolean containsAll(Collection<?> c) {
			return false;
		}

		@Override
		public T element() {
			return get();
		}

		@Override
		public boolean isEmpty() {
			return get() == null;
		}

		@Override
		public Iterator<T> iterator() {
			return Collections.singleton(get()).iterator();
		}

		@Override
		public boolean offer(T t) {
			if (get() != null) {
			    return false;
			}
			lazySet(t);
			return true;
		}

		@Override
		public T peek() {
			return get();
		}

		@Override
		public T poll() {
			T v = get();
			if (v != null) {
			    lazySet(null);
			}
			return v;
		}

		@Override
		public T remove() {
			return getAndSet(null);
		}

		@Override
		public boolean remove(Object o) {
			return false;
		}

		@Override
		public boolean removeAll(Collection<?> c) {
			return false;
		}

		@Override
		public boolean retainAll(Collection<?> c) {
			return false;
		}

		@Override
		public int size() {
			return get() == null ? 0 : 1;
		}

		@Override
		public Object[] toArray() {
			T t = get();
			if (t == null) {
				return new Object[0];
			}
			return new Object[]{t};
		}

		@Override
		@SuppressWarnings("unchecked")
		public <T1> T1[] toArray(T1[] a) {
			int size = size();
			if (a.length < size) {
				a = (T1[]) java.lang.reflect.Array.newInstance(
						a.getClass().getComponentType(), size);
			}
			if (size == 1) {
				a[0] = (T1) get();
			}
			if (a.length > size) {
				a[size] = null;
			}
			return a;
		}

		private static final long serialVersionUID = -6079491923525372331L;
	}

	static final class ZeroQueue<T> implements Queue<T>, Serializable {

		@Override
		public boolean add(T t) {
			return false;
		}

		@Override
		public boolean addAll(Collection<? extends T> c) {
			return false;
		}

		@Override
		public void clear() {
			//NO-OP
		}

		@Override
		public boolean contains(Object o) {
			return false;
		}

		@Override
		public boolean containsAll(Collection<?> c) {
			return false;
		}

		@Override
		public T element() {
			throw new NoSuchElementException("immutable empty queue");
		}

		@Override
		public boolean isEmpty() {
			return true;
		}

		@Override
		public Iterator<T> iterator() {
			return Collections.<T>emptyList().iterator();
		}

		@Override
		public boolean offer(T t) {
			return false;
		}

		@Override
		public T peek() {
			return null;
		}

		@Override
		public T poll() {
			return null;
		}

		@Override
		public T remove() {
			throw new NoSuchElementException("immutable empty queue");
		}

		@Override
		public boolean remove(Object o) {
			return false;
		}

		@Override
		public boolean removeAll(Collection<?> c) {
			return false;
		}

		@Override
		public boolean retainAll(Collection<?> c) {
			return false;
		}

		@Override
		public int size() {
			return 0;
		}

		@Override
		public Object[] toArray() {
			return new Object[0];
		}

		@Override
		@SuppressWarnings("unchecked")
		public <T1> T1[] toArray(T1[] a) {
			if (a.length > 0) {
				a[0] = null;
			}
			return a;
		}

		private static final long serialVersionUID = -8876883675795156827L;
	}

	static final class SimpleQueueAdapter<T> implements Queue<T>, SimpleQueue<T> {

		String NOT_SUPPORTED_MESSAGE = "Although QueueSubscription extends Queue it is purely internal" +
				" and only guarantees support for poll/clear/size/isEmpty." +
				" Instances shouldn't be used/exposed as Queue outside of Reactor operators.";

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

		@Override
		public int size() {
			throw new UnsupportedOperationException(NOT_SUPPORTED_MESSAGE);
		}
	}
}
