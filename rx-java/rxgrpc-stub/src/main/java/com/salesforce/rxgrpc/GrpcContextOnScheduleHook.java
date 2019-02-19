/*
 *  Copyright (c) 2019, Salesforce.com, Inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.rxgrpc;

import io.grpc.Context;
import io.reactivex.functions.Function;

/**
 * {@code GrpcContextOnScheduleHook} is a RxJava scheduler handler hook implementation for transferring the gRPC
 * {@code Context} between RxJava Schedulers.
 * <p>
 * To install the hook, call {@code RxJavaPlugins.setScheduleHandler(new GrpcContextOnScheduleHook());} somewhere in
 * your application startup.
 */
public class GrpcContextOnScheduleHook implements Function<Runnable, Runnable> {
    @Override
    public Runnable apply(Runnable runnable) {
        return Context.current().wrap(runnable);
    }
}
