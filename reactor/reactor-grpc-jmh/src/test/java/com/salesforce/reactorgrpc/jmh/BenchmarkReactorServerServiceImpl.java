/*
 *  Copyright (c) 2019, Salesforce.com, Inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */
package com.salesforce.reactorgrpc.jmh;

import java.util.Arrays;

import com.salesforce.reactorgrpc.jmh.proto.Messages;
import com.salesforce.reactorgrpc.jmh.proto.ReactorBenchmarkServiceGrpc;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class BenchmarkReactorServerServiceImpl extends
                                        ReactorBenchmarkServiceGrpc.BenchmarkServiceImplBase {

    final Mono<Messages.SimpleResponse> responseMono;
    final Flux<Messages.SimpleResponse> responseFlux;

    public BenchmarkReactorServerServiceImpl(int times) {
        Messages.SimpleResponse[] array = new Messages.SimpleResponse[times];
        Arrays.fill(array, Messages.SimpleResponse.getDefaultInstance());

        this.responseFlux = Flux.fromArray(array);
        this.responseMono = Mono.just(Messages.SimpleResponse.getDefaultInstance());
    }

    @Override
    public Mono<Messages.SimpleResponse> unaryCall(Mono<Messages.SimpleRequest> request) {
        return request.then(responseMono);
    }

    @Override
    public Flux<Messages.SimpleResponse> streamingFromServer(Mono<Messages.SimpleRequest> request) {
        return request.thenMany(responseFlux);
    }

    @Override
    public Mono<Messages.SimpleResponse> streamingFromClient(Flux<Messages.SimpleRequest> request) {
        return request.then(responseMono);
    }

    @Override
    public Flux<Messages.SimpleResponse> streamingBothWays(Flux<Messages.SimpleRequest> request) {
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
