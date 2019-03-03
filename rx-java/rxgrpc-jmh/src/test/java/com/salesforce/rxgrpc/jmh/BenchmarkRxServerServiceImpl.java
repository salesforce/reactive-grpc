/*
 *  Copyright (c) 2019, Salesforce.com, Inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */
package com.salesforce.rxgrpc.jmh;

import java.util.Arrays;

import com.salesforce.rxgrpc.jmh.proto.Messages;
import com.salesforce.rxgrpc.jmh.proto.RxBenchmarkServiceGrpc;
import io.reactivex.Flowable;
import io.reactivex.Single;

public class BenchmarkRxServerServiceImpl extends
                                        RxBenchmarkServiceGrpc.BenchmarkServiceImplBase {

    final Single<Messages.SimpleResponse>   responseMono;
    final Flowable<Messages.SimpleResponse> responseFlux;

    public BenchmarkRxServerServiceImpl(int times) {
        Messages.SimpleResponse[] array = new Messages.SimpleResponse[times];
        Arrays.fill(array, Messages.SimpleResponse.getDefaultInstance());

        this.responseFlux = Flowable.fromArray(array);
        this.responseMono = Single.just(Messages.SimpleResponse.getDefaultInstance());
    }

    @Override
    public Single<Messages.SimpleResponse> unaryCall(Single<Messages.SimpleRequest> request) {
        return request.ignoreElement().andThen(responseMono);
    }

    @Override
    public Flowable<Messages.SimpleResponse> streamingFromServer(Single<Messages.SimpleRequest> request) {
        return request.ignoreElement().andThen(responseFlux);
    }

    @Override
    public Single<Messages.SimpleResponse> streamingFromClient(Flowable<Messages.SimpleRequest> request) {
        return request.ignoreElements().andThen(responseMono);
    }

    @Override
    public Flowable<Messages.SimpleResponse> streamingBothWays(Flowable<Messages.SimpleRequest> request) {
        request.subscribe();
        return responseFlux;
    }

    //    @Override
//    public Flux<Messages.SimpleResponse> streamingCall(Flux<Messages.SimpleRequest> request) {
//        return request.map(__ -> Messages.SimpleResponse.getDefaultInstance());
//    }
//
//    @Override
//    public Mono<Messages.SimpleResponse> streamingFromClient(Flux<Messages.SimpleRequest> request) {
//        return request.then(responseMono);
//    }
//
//    @Override
//    public Flux<Messages.SimpleResponse> streamingBothWays(Flux<Messages.SimpleRequest> request) {
//        return request.map(__ -> Messages.SimpleResponse.getDefaultInstance());
//    }
}
