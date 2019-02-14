package com.salesforce.reactorgrpc;

import io.grpc.stub.StreamObserver;
import io.grpc.testing.GrpcServerRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class InprocServerTest {
    @Rule
    public GrpcServerRule serverRule = new GrpcServerRule().directExecutor();

    @Before
    public void before() {
        serverRule.getServiceRegistry().addService(new GreeterGrpc.GreeterImplBase() {
            @Override
            public void sayHello(HelloRequest request, StreamObserver<HelloResponse> responseObserver) {
                responseObserver.onNext(HelloResponse.newBuilder().setMessage("A" + request.getName()).build());
                responseObserver.onCompleted();
            }

            @Override
            public void sayHelloRespStream(HelloRequest request, StreamObserver<HelloResponse> responseObserver) {
                responseObserver.onNext(HelloResponse.newBuilder().setMessage("A" + request.getName()).build());
                responseObserver.onNext(HelloResponse.newBuilder().setMessage("B" + request.getName()).build());
                responseObserver.onNext(HelloResponse.newBuilder().setMessage("C" + request.getName()).build());
                responseObserver.onCompleted();
            }

            @Override
            public StreamObserver<HelloRequest> sayHelloReqStream(StreamObserver<HelloResponse> responseObserver) {
                return new StreamObserver<HelloRequest>() {
                    List<String> values = new ArrayList<>();

                    @Override
                    public void onNext(HelloRequest value) {
                        values.add(value.getName());
                    }

                    @Override
                    public void onError(Throwable t) {
                        responseObserver.onCompleted();
                    }

                    @Override
                    public void onCompleted() {
                        responseObserver.onNext(HelloResponse.newBuilder().setMessage(String.join(",", values)).build());
                        responseObserver.onCompleted();
                    }
                };
            }

            @Override
            public StreamObserver<HelloRequest> sayHelloBothStream(StreamObserver<HelloResponse> responseObserver) {
                return new StreamObserver<HelloRequest>() {
                    @Override
                    public void onNext(HelloRequest value) {
                        responseObserver.onNext(HelloResponse.newBuilder().setMessage("X" + value.getName()).build());
                    }

                    @Override
                    public void onError(Throwable t) {
                        responseObserver.onCompleted();
                    }

                    @Override
                    public void onCompleted() {
                        responseObserver.onCompleted();
                    }
                };
            }
        });
    }

    @Test(timeout = 2000)
    public void inprocOneToOne() {
        ReactorGreeterGrpc.ReactorGreeterStub stub = ReactorGreeterGrpc.newReactorStub(serverRule.getChannel());

        Mono<String> chain = Mono.just("X")
                .map(s -> HelloRequest.newBuilder().setName(s).build())
                .as(stub::sayHello)
                .map(HelloResponse::getMessage);

        StepVerifier.create(chain)
                .expectNext("AX")
                .expectComplete()
                .verify(Duration.ofSeconds(1));
    }

    @Test(timeout = 2000)
    public void inprocOneToMany() {
        ReactorGreeterGrpc.ReactorGreeterStub stub = ReactorGreeterGrpc.newReactorStub(serverRule.getChannel());

        Flux<String> chain = Mono.just("X")
                .map(s -> HelloRequest.newBuilder().setName(s).build())
                .as(stub::sayHelloRespStream)
                .map(HelloResponse::getMessage);

        StepVerifier.create(chain)
                .expectNext("AX", "BX", "CX")
                .expectComplete()
                .verify(Duration.ofSeconds(1));
    }

    @Test(timeout = 2000)
    public void manyToOne() {
        ReactorGreeterGrpc.ReactorGreeterStub stub = ReactorGreeterGrpc.newReactorStub(serverRule.getChannel());

        Mono<String> chain = Flux.just("A", "B", "C")
                .map(s -> HelloRequest.newBuilder().setName(s).build())
                .as(stub::sayHelloReqStream)
                .map(HelloResponse::getMessage);

        StepVerifier.create(chain)
                .expectNext("A,B,C")
                .expectComplete()
                .verify(Duration.ofSeconds(1));
    }

    @Test(timeout = 2000)
    public void manyToMany() {
        ReactorGreeterGrpc.ReactorGreeterStub stub = ReactorGreeterGrpc.newReactorStub(serverRule.getChannel());

        Flux<String> chain = Flux.just("A", "B", "C")
                .map(s -> HelloRequest.newBuilder().setName(s).build())
                .as(stub::sayHelloBothStream)
                .map(HelloResponse::getMessage);

        StepVerifier.create(chain)
                .expectNext("XA", "XB", "XC")
                .expectComplete()
                .verify(Duration.ofSeconds(1));
    }
}
