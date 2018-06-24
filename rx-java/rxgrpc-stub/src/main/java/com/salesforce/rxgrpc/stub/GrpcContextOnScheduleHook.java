package com.salesforce.rxgrpc.stub;

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
