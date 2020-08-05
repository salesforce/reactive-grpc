/*
 *  Copyright (c) 2019, Salesforce.com, Inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.rxgrpc.stub;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.util.concurrent.atomic.AtomicBoolean;

import org.awaitility.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.salesforce.rxgrpc.GrpcContextOnScheduleHook;

import io.grpc.Context;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.plugins.RxJavaPlugins;
import io.reactivex.rxjava3.schedulers.Schedulers;

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

		Context.current().withValue(contextKey, "foo").wrap(() -> {
			Observable.just(1, 2, 3)
					.observeOn(Schedulers.computation())
					.subscribeOn(Schedulers.io())
					.subscribe(
							i -> {
								System.out.println(i);
								assertThat(contextKey.get()).isEqualTo("foo");
							},
							throwable -> {

							},
							() -> done.set(true));
		}).run();

		await().atMost(Duration.FIVE_HUNDRED_MILLISECONDS).untilTrue(done);
	}
}
