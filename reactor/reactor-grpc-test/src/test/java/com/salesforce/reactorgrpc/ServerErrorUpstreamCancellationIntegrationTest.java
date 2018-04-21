package com.salesforce.reactorgrpc;

import com.google.protobuf.Empty;
import com.salesforce.grpc.testing.contrib.NettyGrpcServerRule;
import com.salesforce.servicelibs.NumberProto;
import com.salesforce.servicelibs.ReactorNumbersGrpc;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.Rule;
import org.junit.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

public class ServerErrorUpstreamCancellationIntegrationTest {
    @Rule
    public NettyGrpcServerRule serverRule = new NettyGrpcServerRule();

    private static class ExplodeAfterFiveService extends ReactorNumbersGrpc.NumbersImplBase {
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

    @Test
    public void serverErrorSignalsUpstreamCancellationManyToOne() {
        serverRule.getServiceRegistry().addService(new ExplodeAfterFiveService());
        ReactorNumbersGrpc.ReactorNumbersStub stub = ReactorNumbersGrpc.newReactorStub(serverRule.getChannel());

        AtomicBoolean upstreamCancel = new AtomicBoolean(false);

        Mono<NumberProto.Number> observer = Flux.range(0, Integer.MAX_VALUE)
                .map(this::protoNum)
                .doOnCancel(() -> upstreamCancel.set(true))
                .as(stub::requestPressure)
                .doOnError(System.out::println)
                .doOnSuccess(i -> System.out.println(i.getNumber(0)));

        StepVerifier.create(observer)
                .verifyError(StatusRuntimeException.class);

        assertThat(upstreamCancel.get()).isTrue();
    }

    @Test
    public void serverErrorSignalsUpstreamCancellationBidi() {
        serverRule.getServiceRegistry().addService(new ExplodeAfterFiveService());
        ReactorNumbersGrpc.ReactorNumbersStub stub = ReactorNumbersGrpc.newReactorStub(serverRule.getChannel());

        AtomicBoolean upstreamCancel = new AtomicBoolean(false);

        Flux<NumberProto.Number> subscriber = Flux.range(0, Integer.MAX_VALUE)
                .map(this::protoNum)
                .doOnCancel(() -> upstreamCancel.set(true))
                .compose(stub::twoWayPressure)
                .doOnNext(i -> System.out.println(i.getNumber(0)));

        StepVerifier.create(subscriber)
                .verifyError(StatusRuntimeException.class);
        assertThat(upstreamCancel.get()).isTrue();
    }

    private NumberProto.Number protoNum(int i) {
        Integer[] ints = {i};
        return NumberProto.Number.newBuilder().addAllNumber(Arrays.asList(ints)).build();
    }
}
