Overview
========
Reactor-gRPC is a set of gRPC bindings for reactive programming with [Reactor](http://projectreactor.io/).

Usage
=====
To use Reactor-gRPC with the `protobuf-maven-plugin`, add a [custom protoc plugin configuration section](https://www.xolstice.org/protobuf-maven-plugin/examples/protoc-plugin.html).
```xml
<protocPlugins>
    <protocPlugin>
        <id>reactor-grpc</id>
        <groupId>com.salesforce.servicelibs</groupId>
        <artifactId>reactor-grpc</artifactId>
        <version>[VERSION]</version>
        <mainClass>com.salesforce.reactorgrpc.ReactorGrpcGenerator</mainClass>
    </protocPlugin>
</protocPlugins>
```

After installing the plugin, Reactor-gRPC service stubs will be generated along with your gRPC service stubs.
  
* To implement a service using an Reactor-gRPC service, subclass `Reactor[Name]Grpc.[Name]ImplBase` and override the Reactor-based
  methods.
  
  ```java
  ReactorGreeterGrpc.GreeterImplBase svc = new ReactorGreeterGrpc.GreeterImplBase() {
      @Override
      public Mono<HelloResponse> sayHello(Mono<HelloRequest> request) {
          return request.map(protoRequest -> greet("Hello", protoRequest));
      }

      ...

      @Override
      public Flux<HelloResponse> sayHelloBothStream(Flux<HelloRequest> request) {
          return request
                  .map(HelloRequest::getName)
                  .buffer(2)
                  .map(names -> greet("Hello", String.join(" and ", names)));
      }
  };
  ```
* To call a service using an Reactor-gRPC client, call `Reactor[Name]Grpc.newReactorStub(Channel channel)`.

  ```java
  ReactorGreeterGrpc.ReactorGreeterStub stub = ReactorGreeterGrpc.newReactorStub(channel);
  Flux<HelloRequest> req = Flux.just(
          HelloRequest.newBuilder().setName("a").build(),
          HelloRequest.newBuilder().setName("b").build(),
          HelloRequest.newBuilder().setName("c").build());
  Flux<HelloResponse> resp = stub.sayHelloBothStream(req);
  resp.subscribe(...);
  ```
  
Modules
=======

Reactor-gRPC is broken down into four sub-modules:

* _reactor-grpc_ - a protoc generator for generating gRPC bindings for Reactor.
* _reactor-grpc-stub_ - stub classes supporting the generated Reactor bindings.
* _reactor-grpc-test_ - integration tests for Reactor.
* _reactor-grpc-tck_ - Reactive Streams TCK compliance tests for Reactor.
