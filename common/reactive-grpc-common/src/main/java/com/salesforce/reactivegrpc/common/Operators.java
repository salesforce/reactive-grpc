/*
 *  Copyright (c) 2017, salesforce.com, inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

/*
 * Copyright (c) 2011-2018 Pivotal Software Inc, All Rights Reserved.
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
package com.salesforce.reactivegrpc.common;

import java.util.concurrent.atomic.AtomicLongFieldUpdater;

import org.reactivestreams.Subscription;

/**
 * An helper to support "Operator" writing, handle noop subscriptions, validate request
 * size and to cap concurrent additive operations to Long.MAX_VALUE,
 * which is generic to {@link Subscription#request(long)} handling.
 *
 *
 */
public abstract class Operators {

	/**
	 * Cap an addition to Long.MAX_VALUE
	 *
	 * @param a left operand
	 * @param b right operand
	 *
	 * @return Addition result or Long.MAX_VALUE if overflow
	 */
	public static long addCap(long a, long b) {
		long res = a + b;
		if (res < 0L) {
			return Long.MAX_VALUE;
		}
		return res;
	}

	/**
	 * Concurrent addition bound to Long.MAX_VALUE.
	 * Any concurrent write will "happen before" this operation.
	 *
	 * @param <T> the parent instance type
	 * @param updater  current field updater
	 * @param instance current instance to update
	 * @param toAdd    delta to add
	 * @return value before addition or Long.MAX_VALUE
	 */
	public static <T> long addCap(AtomicLongFieldUpdater<T> updater, T instance, long toAdd) {
		long r, u;
		for (;;) {
			r = updater.get(instance);
			if (r == Long.MAX_VALUE) {
				return Long.MAX_VALUE;
			}
			u = addCap(r, toAdd);
			if (updater.compareAndSet(instance, r, u)) {
				return r;
			}
		}
	}

	/**
	 * Evaluate if a request is strictly positive
	 * @param n the request value
	 * @return true if valid
	 */
	public static boolean validate(long n) {
		if (n <= 0) {
			return false;
		}
		return true;
	}

	public static final class EmptySubscription implements Subscription {
	    static final EmptySubscription INSTANCE = new EmptySubscription();

	    @Override
	    public void cancel() {
	        // deliberately no op
	    }

	    @Override
	    public void request(long n) {
	        // deliberately no op
	    }
	}
}
