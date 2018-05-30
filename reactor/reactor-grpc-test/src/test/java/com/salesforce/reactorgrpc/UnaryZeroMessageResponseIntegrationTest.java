package com.salesforce.reactorgrpc;

import com.salesforce.grpc.testing.contrib.NettyGrpcServerRule;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.junit.Rule;
import org.junit.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

public class UnaryZeroMessageResponseIntegrationTest {
    @Rule
    public NettyGrpcServerRule serverRule = new NettyGrpcServerRule();

    private static class MissingUnaryResponseService extends GreeterGrpc.GreeterImplBase {
        @Override
        public void sayHello(HelloRequest request, StreamObserver<HelloResponse> responseObserver) {
            responseObserver.onCompleted();
        }

        @Override
        public StreamObserver<HelloRequest> sayHelloReqStream(StreamObserver<HelloResponse> responseObserver) {
            return new StreamObserver<HelloRequest>() {
                @Override
                public void onNext(HelloRequest helloRequest) {
                    responseObserver.onCompleted();
                }

                @Override
                public void onError(Throwable throwable) {

                }

                @Override
                public void onCompleted() {

                }
            };
        }
    }

    @Test
    public void zeroMessageResponseOneToOne() {
        serverRule.getServiceRegistry().addService(new MissingUnaryResponseService());

        ReactorGreeterGrpc.ReactorGreeterStub stub = ReactorGreeterGrpc.newReactorStub(serverRule.getChannel());
        Mono<HelloRequest> req = Mono.just(HelloRequest.newBuilder().setName("reactor").build());
        Mono<HelloResponse> resp = req.compose(stub::sayHello);

        StepVerifier.create(resp).verifyErrorMatches(t ->
                t instanceof StatusRuntimeException &&
                ((StatusRuntimeException) t).getStatus().getCode() == Status.Code.CANCELLED);
    }

    @Test
    public void zeroMessageResponseManyToOne() {
        serverRule.getServiceRegistry().addService(new MissingUnaryResponseService());

        ReactorGreeterGrpc.ReactorGreeterStub stub = ReactorGreeterGrpc.newReactorStub(serverRule.getChannel());
        Flux<HelloRequest> req = Flux.just(
                HelloRequest.newBuilder().setName("a").build(),
                HelloRequest.newBuilder().setName("b").build(),
                HelloRequest.newBuilder().setName("c").build());

        Mono<HelloResponse> resp = req.as(stub::sayHelloReqStream);

        StepVerifier.create(resp).verifyErrorMatches(t ->
                t instanceof StatusRuntimeException &&
                ((StatusRuntimeException) t).getStatus().getCode() == Status.Code.CANCELLED);
    }
}
