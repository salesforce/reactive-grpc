/*
 *  Copyright (c) 2019, Salesforce.com, Inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */
package com.salesforce.reactivegrpc.jmh;

import java.util.concurrent.CountDownLatch;

import org.openjdk.jmh.infra.Blackhole;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

/**
 * PerfSubscriber is a sink for reactive requests that blackholes all messages.
 */
public final class PerfSubscriber implements Subscriber<Object> {

    private final Blackhole bh;
    protected final CountDownLatch latch;

    private Subscription subscription;

    public PerfSubscriber(Blackhole bh) {
        this.bh = bh;
        this.latch = new CountDownLatch(1);
    }

    @Override
    public void onSubscribe(Subscription subscription) {
        this.subscription = subscription;
        subscription.request(1);
    }

    @Override
    public void onNext(Object item) {
        bh.consume(item);
        subscription.request(1);
    }

    @Override
    public void onError(Throwable throwable) {
        throwable.printStackTrace();
        bh.consume(throwable);
        latch.countDown();
    }

    @Override
    public void onComplete() {
        latch.countDown();
    }

}
