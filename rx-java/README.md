Overview
========
RxGrpc is a new set of gRPC bindings for reactive programming with [RxJava](https://github.com/ReactiveX/RxJava).

Usage
=====
To use RxGrpc with the `protobuf-maven-plugin`, add a [custom protoc plugin configuration section](https://www.xolstice.org/protobuf-maven-plugin/examples/protoc-plugin.html).
```xml
<protocPlugins>
    <protocPlugin>
        <id>rxgrpc</id>
        <groupId>com.salesforce.servicelibs</groupId>
        <artifactId>rxgrpc</artifactId>
        <version>[VERSION]</version>
        <mainClass>com.salesforce.rxgrpc.RxGrpcGenerator</mainClass>
    </protocPlugin>
</protocPlugins>
```

After installing the plugin, RxGrpc service stubs will be generated along with your gRPC service stubs.
  
* To implement a service using an RxGrpc service, subclass `Rx[Name]Grpc.[Name]ImplBase` and override the RxJava-based
  methods.
  
  ```java
  RxGreeterGrpc.GreeterImplBase svc = new RxGreeterGrpc.GreeterImplBase() {
      @Override
      public Single<HelloResponse> sayHello(Single<HelloRequest> rxRequest) {
          return rxRequest.map(protoRequest -> greet("Hello", protoRequest));
      }

      ...

      @Override
      public Flowable<HelloResponse> sayHelloBothStream(Flowable<HelloRequest> rxRequest) {
          return rxRequest
                  .map(HelloRequest::getName)
                  .buffer(2)
                  .map(names -> greet("Hello", String.join(" and ", names)));
      }
  };
  ```
* To call a service using an RxGrpc client, call `Rx[Name]Grpc.newRxStub(Channel channel)`.

  ```java
  RxGreeterGrpc.RxGreeterStub stub = RxGreeterGrpc.newRxStub(channel);
  Flowable<HelloRequest> req = Flowable.just(
          HelloRequest.newBuilder().setName("a").build(),
          HelloRequest.newBuilder().setName("b").build(),
          HelloRequest.newBuilder().setName("c").build());
  Flowable<HelloResponse> resp = stub.sayHelloBothStream(req);
  resp.subscribe(...);
  ```
  
Modules
=======

RxGrpc is broken down into four sub-modules:

* _rxgrpc_ - a protoc generator for generating gRPC bindings for RxJava.
* _rxgrpc-stub_ - stub classes supporting the generated RxGrpc bindings.
* _rxgrpc-test_ - integration tests for RxGrpc.
* _rxgrpc-tck_ - Reactive Streams TCK compliance tests for RxGrpc.
