/*
 *  Copyright (c) 2019, Salesforce.com, Inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.rxgrpc.stub;

import com.salesforce.rxgrpc.GrpcContextOnScheduleHook;
import io.grpc.Context;
import io.reactivex.Observable;
import io.reactivex.functions.Action;
import io.reactivex.functions.Consumer;
import io.reactivex.plugins.RxJavaPlugins;
import io.reactivex.schedulers.Schedulers;
import org.awaitility.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;


public class GrpcContextOnScheduleHookTest {
    @BeforeEach
    public void before() {
        RxJavaPlugins.setScheduleHandler(new GrpcContextOnScheduleHook());
    }

    @AfterEach
    public void after() {
        RxJavaPlugins.setScheduleHandler(null);
    }

    @Test
    public void GrpcContextPropagatesAcrossSchedulers() {
        final Context.Key<String> contextKey = Context.key("key");

        final AtomicBoolean done = new AtomicBoolean();

        Context.current().withValue(contextKey, "foo").wrap(new Runnable() {
            @Override
            public void run() {
                Observable.just(1, 2, 3)
                        .observeOn(Schedulers.computation())
                        .subscribeOn(Schedulers.io())
                        .subscribe(
                                new Consumer<Integer>() {
                                    @Override
                                    public void accept(Integer i) throws Exception {
                                        System.out.println(i);
                                        assertThat(contextKey.get()).isEqualTo("foo");
                                    }
                                },
                                new Consumer<Throwable>() {
                                    @Override
                                    public void accept(Throwable throwable) throws Exception {

                                    }
                                },
                                new Action() {
                                    @Override
                                    public void run() throws Exception {
                                        done.set(true);
                                    }
                                });
            }
        }).run();

        await().atMost(Duration.FIVE_HUNDRED_MILLISECONDS).untilTrue(done);
    }
}
