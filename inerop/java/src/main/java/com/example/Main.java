package com.example;

import io.grpc.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class Main {
    public static class GreeterImpl extends ReactorGreeterGrpc.GreeterImplBase {
        @Override
        public Mono<GreeterOuterClass.HelloResponse> sayHello(Mono<GreeterOuterClass.HelloRequest> request) {
            return request.map(req -> 
                GreeterOuterClass.HelloResponse.newBuilder()
                .addMessage("Hello " + req.getName())
                .addMessage("Aloha " + req.getName())
                .addMessage("Howdy " + req.getName())
                .build()
            );
        }
    }

    public static void main(String[] args) throws Exception {
        // Build the server
        System.out.println("Starting Java server on port 9000");
        Server server = ServerBuilder.forPort(9000).addService(new GreeterImpl()).build().start();

        // Call the Go server on port 9001
        System.out.println("Press enter to call Go server...");
        System.in.read();

        // Set up gRPC client
        ManagedChannel goChannel = ManagedChannelBuilder.forAddress("localhost", 9001).usePlaintext().build();
        ReactorGreeterGrpc.ReactorGreeterStub goStub = ReactorGreeterGrpc.newReactorStub(goChannel);

        // Call the Go service
        Mono.just(GreeterOuterClass.HelloRequest.newBuilder().setName("Java").build())
            .as(goStub::sayHello)
            .block()
            .getMessageList().forEach(msg -> System.out.println("Java " + msg));

        Flux.range(1, 100)
            .map(i -> GreeterOuterClass.HelloRequest.newBuilder().setName("Number " + i).build())
            .as(goStub::sayHelloStream)
            .flatMap(resp -> Flux.fromIterable(resp.getMessageList()))
            .doOnEach(msg -> System.out.println("Java " + msg))
            .blockLast();

        // Call the C# server on port 9002
        System.out.println("Press enter to call C# server...");
        System.in.read();

        // Set up gRPC client
        ManagedChannel csharpChannel = ManagedChannelBuilder.forAddress("localhost", 9001).usePlaintext().build();
        ReactorGreeterGrpc.ReactorGreeterStub csharpStub = ReactorGreeterGrpc.newReactorStub(csharpChannel);

        // Call the Go service
        Mono.just(GreeterOuterClass.HelloRequest.newBuilder().setName("Java").build())
            .as(csharpStub::sayHello)
            .block()
            .getMessageList().forEach(msg -> System.out.println("Java " + msg));

        Flux.range(1, 100)
            .map(i -> GreeterOuterClass.HelloRequest.newBuilder().setName("Number " + i).build())
            .as(csharpStub::sayHelloStream)
            .flatMap(resp -> Flux.fromIterable(resp.getMessageList()))
            .doOnEach(msg -> System.out.println("Java " + msg))
            .blockLast();

        // Block for server termination
        server.awaitTermination();
    }
}
