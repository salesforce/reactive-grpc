package com.salesforce.reactorgrpc;

import com.salesforce.grpc.testing.contrib.NettyGrpcServerRule;
import demo.proto.*;
import org.junit.Rule;
import org.junit.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Verifies the Flux SwitchOnFirst operator works correctly. Derived from demonstration code at
 * https://github.com/simonbasle/reactiveGrpcSwitchOnFirst
 *
 * https://github.com/salesforce/reactive-grpc/issues/136
 */
public class SwitchOnFirstIntegrationTest {
    @Rule
    public NettyGrpcServerRule serverRule = new NettyGrpcServerRule();

    private ReactorSkipGreeterGrpc.SkipGreeterImplBase svc = new ReactorSkipGreeterGrpc.SkipGreeterImplBase() {
        @Override
        public Flux<Greeting> skipGreet(Flux<Frame> request) {
            return request
                .doOnNext(frame -> System.out.println("input from client: " + frame.toString().replaceAll("\n", "\t")))
                .switchOnFirst((firstSignal, all) -> {
                    System.out.println("switching on first signal " + firstSignal); //FIXME this never gets invoked
                    if (firstSignal.isOnNext()) {
                        Frame frame = firstSignal.get();
                        assert frame != null;
                        if (frame.hasConfig()) {
                            int skip = frame.getConfig().getSkip();
                            return all.filter(Frame::hasPayload).skip(skip).map(Frame::getPayload);
                        }
                        else {
                            //no configuration frame
                            return Mono.error(new IllegalArgumentException("Missing Config frame at start"));
                        }
                    } else {
                        //avoid returning Flux.empty, which would suppress an immediate error.
                        return all.cast(Payload.class); //the "all" input Flux completes or errors immediately, so no real payload
                    }
                })
                .map(payload -> Greeting.newBuilder().setMessage("Hello " + payload.getName()).build());
        }
    };

    @Test
    public void SwitchOnFirstWorksAsExpected() {
        serverRule.getServiceRegistry().addService(svc);
        ReactorSkipGreeterGrpc.ReactorSkipGreeterStub stub = ReactorSkipGreeterGrpc.newReactorStub(serverRule.getChannel());

        StepVerifier.Step<String> stepVerifier = StepVerifier.create(
            stub.skipGreet(
                Flux.just(
                    Frame.newBuilder().setConfig(Config.newBuilder().setSkip(3).build()).build(),
                    Frame.newBuilder().setPayload(Payload.newBuilder().setName("Andy").build()).build(),
                    Frame.newBuilder().setPayload(Payload.newBuilder().setName("Brad").build()).build(),
                    Frame.newBuilder().setPayload(Payload.newBuilder().setName("Charles").build()).build(),
                    Frame.newBuilder().setPayload(Payload.newBuilder().setName("Diane").build()).build(),
                    Frame.newBuilder().setPayload(Payload.newBuilder().setName("Edith").build()).build()
                ))
                .map(Greeting::getMessage)
                .doOnNext(System.out::println));

        stepVerifier
                .expectNext("Hello Diane")
                .expectNext("Hello Edith")
                .verifyComplete();
    }
}
