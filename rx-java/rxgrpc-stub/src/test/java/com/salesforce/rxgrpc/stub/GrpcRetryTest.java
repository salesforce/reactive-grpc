/*  Copyright (c) 2019, Salesforce.com, Inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.rxgrpc.stub;

import com.github.davidmoten.rx2.RetryWhen;
import com.salesforce.rxgrpc.GrpcRetry;
import io.reactivex.*;
import io.reactivex.functions.Function;
import io.reactivex.observers.TestObserver;
import io.reactivex.subscribers.TestSubscriber;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

@SuppressWarnings("Duplicates")
public class GrpcRetryTest {
    private Flowable<Integer> newThreeErrorFlowable() {
        return Flowable.create(new FlowableOnSubscribe<Integer>() {
            int count = 3;
            @Override
            public void subscribe(FlowableEmitter<Integer> emitter) throws Exception {
                if (count > 0) {
                    emitter.onError(new Throwable("Not yet!"));
                    count--;
                } else {
                    emitter.onNext(0);
                    emitter.onComplete();
                }
            }
        }, BackpressureStrategy.BUFFER);
    }

    private Single<Integer> newThreeErrorSingle() {
        return Single.create(new SingleOnSubscribe<Integer>() {
            int count = 3;
            @Override
            public void subscribe(SingleEmitter<Integer> emitter) throws Exception {
                if (count > 0) {
                    emitter.onError(new Throwable("Not yet!"));
                    count--;
                } else {
                    emitter.onSuccess(0);
                }
            }
        });
    }

    @Test
    public void noRetryMakesErrorFlowabable() {
        TestSubscriber<Integer> test = newThreeErrorFlowable()
                .as(new FlowableConverter<Integer, Flowable<Integer>>() {
                    @Override
                    public Flowable<Integer> apply(Flowable<Integer> flowable) {
                        return flowable;
                    }
                })
                .test();

        test.awaitTerminalEvent(1, TimeUnit.SECONDS);
        test.assertErrorMessage("Not yet!");
    }

    @Test
    public void noRetryMakesErrorSingle() {
        TestObserver<Integer> test = newThreeErrorSingle()
                .as(new SingleConverter<Integer, Single<Integer>>() {
                    @Override
                    public Single<Integer> apply(Single<Integer> single) {
                        return single;
                    }
                })
                .test();

        test.awaitTerminalEvent(1, TimeUnit.SECONDS);
        test.assertErrorMessage("Not yet!");
    }

    @Test
    public void oneToManyRetryWhen() {
        TestSubscriber<Integer> test = newThreeErrorSingle()
                .as(GrpcRetry.OneToMany.retryWhen(new Function<Single<Integer>, Flowable<Integer>>() {
                    @Override
                    public Flowable<Integer> apply(Single<Integer> single) {
                        return single.toFlowable();
                    }
                }, RetryWhen.maxRetries(3).build()))
                .test();

        test.awaitTerminalEvent(1, TimeUnit.SECONDS);
        test.assertValues(0);
        test.assertNoErrors();
        test.assertComplete();
    }

    @Test
    public void oneToManyRetryImmediately() {
        TestSubscriber<Integer> test = newThreeErrorSingle()
                .as(GrpcRetry.OneToMany.retryImmediately(new Function<Single<Integer>, Flowable<Integer>>() {
                    @Override
                    public Flowable<Integer> apply(Single<Integer> single) {
                        return single.toFlowable();
                    }
                }))
                .test();

        test.awaitTerminalEvent(1, TimeUnit.SECONDS);
        test.assertValues(0);
        test.assertNoErrors();
        test.assertComplete();
    }

    @Test
    public void oneToManyRetryAfter() {
        TestSubscriber<Integer> test = newThreeErrorSingle()
                .as(GrpcRetry.OneToMany.retryAfter(new Function<Single<Integer>, Flowable<Integer>>() {
                    @Override
                    public Flowable<Integer> apply(Single<Integer> single) {
                        return single.toFlowable();
                    }
                }, 10, TimeUnit.MILLISECONDS))
                .test();

        test.awaitTerminalEvent(1, TimeUnit.SECONDS);
        test.assertValues(0);
        test.assertNoErrors();
        test.assertComplete();
    }

    @Test
    public void manyToManyRetryWhen() {
        TestSubscriber<Integer> test = newThreeErrorFlowable()
                .compose(GrpcRetry.ManyToMany.retryWhen(new Function<Flowable<Integer>, Flowable<Integer>>() {
                    @Override
                    public Flowable<Integer> apply(Flowable<Integer> flowable) {
                        return flowable;
                    }
                }, RetryWhen.maxRetries(3).build()))
                .test();

        test.awaitTerminalEvent(1, TimeUnit.SECONDS);
        test.assertValues(0);
        test.assertNoErrors();
        test.assertComplete();
    }

    @Test
    public void manyToManyRetryImmediately() {
        TestSubscriber<Integer> test = newThreeErrorFlowable()
                .compose(GrpcRetry.ManyToMany.retryImmediately(new Function<Flowable<Integer>, Flowable<Integer>>() {
                    @Override
                    public Flowable<Integer> apply(Flowable<Integer> flowable) {
                        return flowable;
                    }
                }))
                .test();

        test.awaitTerminalEvent(1, TimeUnit.SECONDS);
        test.assertValues(0);
        test.assertNoErrors();
        test.assertComplete();
    }

    @Test
    public void manyToManyRetryAfter() {
        TestSubscriber<Integer> test = newThreeErrorFlowable()
                .compose(GrpcRetry.ManyToMany.retryAfter(new Function<Flowable<Integer>, Flowable<Integer>>() {
                    @Override
                    public Flowable<Integer> apply(Flowable<Integer> flowable) {
                        return flowable;
                    }
                }, 10, TimeUnit.MILLISECONDS))
                .test();

        test.awaitTerminalEvent(1, TimeUnit.SECONDS);
        test.assertValues(0);
        test.assertNoErrors();
        test.assertComplete();
    }

    @Test
    public void manyToOneRetryWhen() {
        TestObserver<Integer> test = newThreeErrorFlowable()
                .as(GrpcRetry.ManyToOne.retryWhen(new Function<Flowable<Integer>, Single<Integer>>() {
                    @Override
                    public Single<Integer> apply(Flowable<Integer> flowable) {
                        return flowable.singleOrError();
                    }
                }, RetryWhen.maxRetries(3).build()))
                .test();

        test.awaitTerminalEvent(1, TimeUnit.SECONDS);
        test.assertValues(0);
        test.assertNoErrors();
        test.assertComplete();
    }

    @Test
    public void manyToOneRetryImmediately() {
        TestObserver<Integer> test = newThreeErrorFlowable()
                .as(GrpcRetry.ManyToOne.retryImmediately(new Function<Flowable<Integer>, Single<Integer>>() {
                    @Override
                    public Single<Integer> apply(Flowable<Integer> flowable) {
                        return flowable.singleOrError();
                    }
                }))
                .test();

        test.awaitTerminalEvent(1, TimeUnit.SECONDS);
        test.assertValues(0);
        test.assertNoErrors();
        test.assertComplete();
    }

    @Test
    public void manyToOneRetryAfter() {
        TestObserver<Integer> test = newThreeErrorFlowable()
                .as(GrpcRetry.ManyToOne.retryAfter(new Function<Flowable<Integer>, Single<Integer>>() {
                    @Override
                    public Single<Integer> apply(Flowable<Integer> flowable) {
                        return flowable.singleOrError();
                    }
                }, 10, TimeUnit.MILLISECONDS))
                .test();

        test.awaitTerminalEvent(1, TimeUnit.SECONDS);
        test.assertValues(0);
        test.assertNoErrors();
        test.assertComplete();
    }
}
