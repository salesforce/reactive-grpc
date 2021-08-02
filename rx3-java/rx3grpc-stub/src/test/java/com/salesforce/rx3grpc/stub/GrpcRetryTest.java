/*  Copyright (c) 2019, Salesforce.com, Inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.rx3grpc.stub;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import com.salesforce.rx3grpc.GrpcRetry;

import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.FlowableConverter;
import io.reactivex.rxjava3.core.FlowableEmitter;
import io.reactivex.rxjava3.core.FlowableOnSubscribe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.core.SingleConverter;
import io.reactivex.rxjava3.core.SingleEmitter;
import io.reactivex.rxjava3.core.SingleOnSubscribe;
import io.reactivex.rxjava3.functions.Function;
import io.reactivex.rxjava3.functions.Predicate;
import io.reactivex.rxjava3.observers.TestObserver;
import io.reactivex.rxjava3.subscribers.TestSubscriber;

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
    public void noRetryMakesErrorFlowabable() throws InterruptedException {
        TestSubscriber<Integer> test = newThreeErrorFlowable()
                .to(new FlowableConverter<Integer, Flowable<Integer>>() {
                    @Override
                    public Flowable<Integer> apply(Flowable<Integer> flowable) {
                        return flowable;
                    }
                })
                .test();

		test.await(1, TimeUnit.SECONDS);
		test.assertError(new Predicate<Throwable>() {
			@Override
			public boolean test(Throwable t) throws Throwable {
				return t.getMessage().equals("Not yet!");
			}
		});
	}

	@Test
	public void noRetryMakesErrorSingle() throws InterruptedException {
		TestObserver<Integer> test = newThreeErrorSingle()
				.to(new SingleConverter<Integer, Single<Integer>>() {
					@Override
					public Single<Integer> apply(Single<Integer> single) {
						return single;
					}
				})
				.test();

		test.await(1, TimeUnit.SECONDS);
		test.assertError(new Predicate<Throwable>() {
			@Override
			public boolean test(Throwable t) throws Throwable {
				return t.getMessage().equals("Not yet!");
			}
		});
	}

    @Test
    public void oneToManyRetryWhen() throws InterruptedException {
        TestSubscriber<Integer> test = newThreeErrorSingle()
                .to(GrpcRetry.OneToMany.retryWhen(new Function<Single<Integer>, Flowable<Integer>>() {
                    @Override
                    public Flowable<Integer> apply(Single<Integer> single) {
                        return single.toFlowable();
                    }
                }, RetryWhen.maxRetries(3).build()))
                .test();

        test.await(1, TimeUnit.SECONDS);
        test.assertValues(0);
        test.assertNoErrors();
        test.assertComplete();
    }

    @Test
    public void oneToManyRetryImmediately() throws InterruptedException {
        TestSubscriber<Integer> test = newThreeErrorSingle()
                .to(GrpcRetry.OneToMany.retryImmediately(new Function<Single<Integer>, Flowable<Integer>>() {
                    @Override
                    public Flowable<Integer> apply(Single<Integer> single) {
                        return single.toFlowable();
                    }
                }))
                .test();

		test.await(1, TimeUnit.SECONDS);
		test.assertValues(0);
		test.assertNoErrors();
		test.assertComplete();
	}

    @Test
    public void oneToManyRetryAfter() throws InterruptedException {
        TestSubscriber<Integer> test = newThreeErrorSingle()
                .to(GrpcRetry.OneToMany.retryAfter(new Function<Single<Integer>, Flowable<Integer>>() {
                    @Override
                    public Flowable<Integer> apply(Single<Integer> single) {
                        return single.toFlowable();
                    }
                }, 10, TimeUnit.MILLISECONDS))
                .test();

        test.await(1, TimeUnit.SECONDS);
        test.assertValues(0);
        test.assertNoErrors();
        test.assertComplete();
    }

    @Test
    public void manyToManyRetryWhen() throws InterruptedException {
        TestSubscriber<Integer> test = newThreeErrorFlowable()
                .compose(GrpcRetry.ManyToMany.retryWhen(new Function<Flowable<Integer>, Flowable<Integer>>() {
                    @Override
                    public Flowable<Integer> apply(Flowable<Integer> flowable) {
                        return flowable;
                    }
                }, RetryWhen.maxRetries(3).build()))
                .test();

        test.await(1, TimeUnit.SECONDS);
        test.assertValues(0);
        test.assertNoErrors();
        test.assertComplete();
    }

    @Test
    public void manyToManyRetryImmediately() throws InterruptedException {
        TestSubscriber<Integer> test = newThreeErrorFlowable()
                .compose(GrpcRetry.ManyToMany.retryImmediately(new Function<Flowable<Integer>, Flowable<Integer>>() {
                    @Override
                    public Flowable<Integer> apply(Flowable<Integer> flowable) {
                        return flowable;
                    }
                }))
                .test();

        test.await(1, TimeUnit.SECONDS);
        test.assertValues(0);
        test.assertNoErrors();
        test.assertComplete();
    }

    @Test
    public void manyToManyRetryAfter() throws InterruptedException {
        TestSubscriber<Integer> test = newThreeErrorFlowable()
                .compose(GrpcRetry.ManyToMany.retryAfter(new Function<Flowable<Integer>, Flowable<Integer>>() {
                    @Override
                    public Flowable<Integer> apply(Flowable<Integer> flowable) {
                        return flowable;
                    }
                }, 10, TimeUnit.MILLISECONDS))
                .test();

        test.await(1, TimeUnit.SECONDS);
        test.assertValues(0);
        test.assertNoErrors();
        test.assertComplete();
    }

    @Test
    public void manyToOneRetryWhen() throws InterruptedException {
        TestObserver<Integer> test = newThreeErrorFlowable()
                .to(GrpcRetry.ManyToOne.retryWhen(new Function<Flowable<Integer>, Single<Integer>>() {
                    @Override
                    public Single<Integer> apply(Flowable<Integer> flowable) {
                        return flowable.singleOrError();
                    }
                }, RetryWhen.maxRetries(3).build()))
                .test();

        test.await(1, TimeUnit.SECONDS);
        test.assertValues(0);
        test.assertNoErrors();
        test.assertComplete();
    }

    @Test
    public void manyToOneRetryImmediately() throws InterruptedException {
        TestObserver<Integer> test = newThreeErrorFlowable()
                .to(GrpcRetry.ManyToOne.retryImmediately(new Function<Flowable<Integer>, Single<Integer>>() {
                    @Override
                    public Single<Integer> apply(Flowable<Integer> flowable) {
                        return flowable.singleOrError();
                    }
                }))
                .test();

        test.await(1, TimeUnit.SECONDS);
        test.assertValues(0);
        test.assertNoErrors();
        test.assertComplete();
    }

    @Test
    public void manyToOneRetryAfter() throws InterruptedException {
        TestObserver<Integer> test = newThreeErrorFlowable()
                .to(GrpcRetry.ManyToOne.retryAfter(new Function<Flowable<Integer>, Single<Integer>>() {
                    @Override
                    public Single<Integer> apply(Flowable<Integer> flowable) {
                        return flowable.singleOrError();
                    }
                }, 10, TimeUnit.MILLISECONDS))
                .test();

        test.await(1, TimeUnit.SECONDS);
        test.assertValues(0);
        test.assertNoErrors();
        test.assertComplete();
    }
}
