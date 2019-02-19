/*  Copyright (c) 2019, Salesforce.com, Inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.reactorgrpc;

import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicBoolean;

import com.google.protobuf.Empty;
import com.salesforce.grpc.testing.contrib.NettyGrpcServerRule;
import com.salesforce.servicelibs.NumberProto;
import com.salesforce.servicelibs.ReactorNumbersGrpc;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(Parameterized.class)
public class ServerErrorUpstreamCancellationIntegrationTest {

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
                { new ExplodeAfterFiveService(), false },
                { new FusedExplodeAfterFiveService(), true }
        });
    }

    @Rule
    public NettyGrpcServerRule serverRule = new NettyGrpcServerRule();

    private final ReactorNumbersGrpc.NumbersImplBase service;
    private final boolean                            expectFusion;

    public ServerErrorUpstreamCancellationIntegrationTest(ReactorNumbersGrpc.NumbersImplBase service, boolean expectFusion) {
        this.service = service;
        this.expectFusion = expectFusion;
    }

    @Test
    public void serverErrorSignalsUpstreamCancellationManyToOne() {
        serverRule.getServiceRegistry().addService(service);
        ReactorNumbersGrpc.ReactorNumbersStub stub = ReactorNumbersGrpc.newReactorStub(serverRule.getChannel());

        AtomicBoolean upstreamCancel = new AtomicBoolean(false);

        Flux<NumberProto.Number> requestFlux = Flux.range(0, Integer.MAX_VALUE)
                                                   .map(this::protoNum)
                                                   .doOnCancel(() -> upstreamCancel.set(true));

        if (!expectFusion) {
            requestFlux = requestFlux.hide();
        }

        Mono<NumberProto.Number> observer = requestFlux
                                                .as(stub::requestPressure)
                                                .doOnError(System.out::println)
                                                .doOnSuccess(i -> System.out.println(i.getNumber(0)));

        StepVerifier.Step<NumberProto.Number> stepVerifier = StepVerifier.create(observer);

        if (expectFusion) {
            stepVerifier = ((StepVerifier.FirstStep<NumberProto.Number>) stepVerifier).expectFusion();
        }

        stepVerifier
                .verifyError(StatusRuntimeException.class);

        assertThat(upstreamCancel.get()).isTrue();
    }

    @Test
    public void serverErrorSignalsUpstreamCancellationBidi() {
        serverRule.getServiceRegistry().addService(service);
        ReactorNumbersGrpc.ReactorNumbersStub stub = ReactorNumbersGrpc.newReactorStub(serverRule.getChannel());

        AtomicBoolean upstreamCancel = new AtomicBoolean(false);

        Flux<NumberProto.Number> requestFlux = Flux.range(0, Integer.MAX_VALUE)
                                                   .map(this::protoNum)
                                                   .doOnCancel(() -> upstreamCancel.set(true));

        if (!expectFusion) {
            requestFlux = requestFlux.hide();
        }

        Flux<NumberProto.Number> subscriber = requestFlux
                                                  .transform(stub::twoWayPressure)
                                                  .doOnNext(i -> System.out.println(i.getNumber(0)));

        StepVerifier.Step<NumberProto.Number> stepVerifier = StepVerifier.create(subscriber);

        if (expectFusion) {
            stepVerifier = ((StepVerifier.FirstStep<NumberProto.Number>) stepVerifier).expectFusion();
        }

        stepVerifier
                .verifyError(StatusRuntimeException.class);

        assertThat(upstreamCancel.get()).isTrue();
    }

    private NumberProto.Number protoNum(int i) {
        Integer[] ints = {i};
        return NumberProto.Number.newBuilder().addAllNumber(Arrays.asList(ints)).build();
    }

    private static class ExplodeAfterFiveService extends ReactorNumbersGrpc.NumbersImplBase {
        @Override
        public Flux<NumberProto.Number> twoWayPressure(Flux<NumberProto.Number> request) {
            return request.hide().map(x -> kaboom());
        }

        @Override
        public Mono<NumberProto.Number> requestPressure(Flux<NumberProto.Number> request) {
            return request.hide().map(x -> kaboom()).single().hide();
        }

        @Override
        public Flux<NumberProto.Number> responsePressure(Mono<Empty> request) {
            return request.hide().map(x -> kaboom()).flux().hide();
        }

        private NumberProto.Number kaboom() {
            throw Status.FAILED_PRECONDITION.asRuntimeException();
        }
    }

    private static class FusedExplodeAfterFiveService extends ReactorNumbersGrpc.NumbersImplBase {
        @Override
        public Flux<NumberProto.Number> twoWayPressure(Flux<NumberProto.Number> request) {
            return request.map(x -> kaboom());
        }

        @Override
        public Mono<NumberProto.Number> requestPressure(Flux<NumberProto.Number> request) {
            return request.map(x -> kaboom()).single();
        }

        @Override
        public Flux<NumberProto.Number> responsePressure(Mono<Empty> request) {
            return request.map(x -> kaboom()).flux();
        }

        private NumberProto.Number kaboom() {
            throw Status.FAILED_PRECONDITION.asRuntimeException();
        }
    }
}
