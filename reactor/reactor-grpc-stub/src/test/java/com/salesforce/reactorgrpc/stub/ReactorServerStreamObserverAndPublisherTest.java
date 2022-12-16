package com.salesforce.reactorgrpc.stub;

import static org.mockito.Answers.RETURNS_DEFAULTS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.stub.ServerCallStreamObserver;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class ReactorServerStreamObserverAndPublisherTest {

    @SuppressWarnings("unchecked")
    @Test
    void noErrorsOnCancelBeforeHalfClose() {
        // ServerCalls.manyToMany(mock(StreamObserver.class), f -> f, CallOptions.DEFAULT).;
        io.grpc.stub.ServerCalls.BidiStreamingMethod<Object, Object> method = mock(io.grpc.stub.ServerCalls.BidiStreamingMethod.class);
        ServerCallStreamObserver<Object> upstream = mock(ServerCallStreamObserver.class, RETURNS_DEFAULTS);
        ReactorServerStreamObserverAndPublisher<Object> observer = new ReactorServerStreamObserverAndPublisher<>(upstream, null, 42, 42);
        when(method.invoke(any())).thenReturn(observer);

        ServerCallHandler<?, ?> serverCallHandler = io.grpc.stub.ServerCalls.asyncBidiStreamingCall(method);
        ServerCall.Listener<Object> callListener = serverCallHandler.startCall(mock(ServerCall.class), new Metadata());

        StepVerifier.create(observer)
            .expectSubscription()
            .then(callListener::onCancel)
            .expectNoEvent(Duration.ofMillis(1))
            .thenCancel()
            .verifyThenAssertThat()
            .hasNotDroppedErrors();
    }
}