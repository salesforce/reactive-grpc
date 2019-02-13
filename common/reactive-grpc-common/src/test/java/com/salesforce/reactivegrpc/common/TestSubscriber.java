/*  Copyright (c) 2017, salesforce.com, inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.reactivegrpc.common;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.assertj.core.api.Assertions;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

public class TestSubscriber<T> implements Subscriber<T> {

    volatile Subscription s;
    Throwable t;
    boolean done;
    final CountDownLatch latch = new CountDownLatch(1);

    @Override
    public void onSubscribe(Subscription subscription) {
        s = subscription;

        s.request(Long.MAX_VALUE);
    }

    @Override
    public void onNext(T t) {

    }

    @Override
    public void onError(Throwable throwable) {
        t = throwable;
        done = true;
        latch.countDown();
    }

    @Override
    public void onComplete() {
        done = true;
        latch.countDown();
    }

    public void awaitTerminalEvent() {
        try {
            latch.await();
        }
        catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean awaitTerminalEvent(long timeout, TimeUnit timeUnit) {
        try {
            return latch.await(timeout, timeUnit);
        }
        catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public void assertError() {
        Assertions.assertThat(t).isNotNull();
        Assertions.assertThat(done).isTrue();
    }

    public void assertComplete() {
        Assertions.assertThat(t).isNull();
        Assertions.assertThat(done).isTrue();
    }

    public void assertNoErrors() {
        Assertions.assertThat(t).isNull();
    }
}
