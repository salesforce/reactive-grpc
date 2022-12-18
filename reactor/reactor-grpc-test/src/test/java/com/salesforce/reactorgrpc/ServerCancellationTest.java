package com.salesforce.reactorgrpc;

import java.io.IOException;
import java.time.Duration;

import io.grpc.ForwardingServerCallListener;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.testing.GrpcCleanupRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.test.publisher.TestPublisher;

public class ServerCancellationTest {
    @Rule
    public final GrpcCleanupRule grpcCleanup = new GrpcCleanupRule();
    private final TestService testService = new TestService();
    private static ReactorGreeterGrpc.ReactorGreeterStub greetings;

    @Before
    public void setupServer() throws IOException {
        String serverName = InProcessServerBuilder.generateName();
        grpcCleanup.register(
            InProcessServerBuilder.forName(serverName)
                .addService(testService)
                .intercept(new CallClosingInterceptor())
                .build()
                .start()
        );
        ManagedChannel channel = grpcCleanup.register(InProcessChannelBuilder.forName(serverName).build());
        greetings = ReactorGreeterGrpc.newReactorStub(channel);
    }

    @Test
    public void cancelsSubscriptionOnInterceptorClose() {
        assertCancelsReactorSubscription(greetings.sayHello(HelloRequest.getDefaultInstance()));
    }

    @Test
    public void cancelsSubscriptionOnInterceptorCloseClientStream() {
        assertCancelsReactorSubscription(greetings.sayHelloReqStream(Flux.just(HelloRequest.getDefaultInstance())));
    }

    @Test
    public void cancelsSubscriptionOnInterceptorCloseServerStream() {
        assertCancelsReactorSubscription(greetings.sayHelloRespStream(HelloRequest.getDefaultInstance()));
    }

    @Test
    public void cancelsSubscriptionOnInterceptorCloseBidiStream() {
        assertCancelsReactorSubscription(greetings.sayHelloBothStream(Flux.just(HelloRequest.getDefaultInstance())));
    }

    private void assertCancelsReactorSubscription(Publisher<HelloResponse> request) {
        StepVerifier.create(request)
            .expectErrorMatches(error ->
                error instanceof StatusRuntimeException &&
                    ((StatusRuntimeException) error).getStatus().getCode() == Status.Code.ABORTED
            )
            .verify(Duration.ofSeconds(2));
        testService.testPublisher.assertNoSubscribers();
    }

    private static class CallClosingInterceptor implements ServerInterceptor {
        @Override
        public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next
        ) {
            return new ForwardingServerCallListener.SimpleForwardingServerCallListener<ReqT>(next.startCall(call, headers)) {
                @Override
                public void onMessage(ReqT message) {
                    super.onMessage(message);
                    call.close(Status.ABORTED, new Metadata());
                }
            };
        }
    }

    private static class TestService extends ReactorGreeterGrpc.GreeterImplBase {
        protected final TestPublisher<HelloResponse> testPublisher = TestPublisher.create();

        @Override public Mono<HelloResponse> sayHello(HelloRequest request) {
            return testPublisher.mono();
        }

        @Override public Flux<HelloResponse> sayHelloRespStream(HelloRequest request) {
            return testPublisher.flux();
        }

        @Override public Mono<HelloResponse> sayHelloReqStream(Flux<HelloRequest> request) {
            request.subscribe();
            return testPublisher.mono();
        }

        @Override public Flux<HelloResponse> sayHelloBothStream(Flux<HelloRequest> request) {
            request.subscribe();
            return testPublisher.flux();
        }
    }
}
