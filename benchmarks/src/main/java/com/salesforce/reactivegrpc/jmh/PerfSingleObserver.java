/*
 *  Copyright (c) 2019, Salesforce.com, Inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */
package com.salesforce.reactivegrpc.jmh;

import java.util.concurrent.CountDownLatch;

import io.reactivex.SingleObserver;
import io.reactivex.disposables.Disposable;
import org.openjdk.jmh.infra.Blackhole;

/**
 * PerfSingleObserver is a sink for gRPC requests that blackholes all messages.
 */
public final class PerfSingleObserver implements SingleObserver<Object> {

    private final Blackhole bh;
    protected final CountDownLatch latch;

    public PerfSingleObserver(Blackhole bh) {
        this.bh = bh;
        this.latch = new CountDownLatch(1);
    }

    @Override
    public void onSubscribe(Disposable subscription) {
    }

    @Override
    public void onSuccess(Object item) {
        bh.consume(item);
        latch.countDown();
    }

    @Override
    public void onError(Throwable throwable) {
        throwable.printStackTrace();
        bh.consume(throwable);
        latch.countDown();
    }
}
