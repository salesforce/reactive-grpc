Overview
========
Reactor-gRPC is a set of gRPC bindings for reactive programming with [Reactor](http://projectreactor.io/).

### Android support
Reactive gRPC supports Android to the same level of the underlying reactive technologies. Spring Reactor
does [not officially support Android](http://projectreactor.io/docs/core/release/reference/docs/index.html#prerequisites),
however, "it should work fine with Android SDK 26 (Android O) and above."

Installation
=====
### Maven
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

### Gradle
To use Reactor-gRPC with the `protobuf-gradle-plugin`, add the reactor-grpc plugin to the protobuf `plugins` section.
```scala
protobuf {
    protoc {
        // The artifact spec for the Protobuf Compiler
        artifact = "com.google.protobuf:protoc:${protobufVersion}"
    }
    plugins {
        grpc {
            artifact = "io.grpc:protoc-gen-grpc-java:${grpcVersion}"
        }
        reactor {
            artifact = "com.salesforce.servicelibs:reactor-grpc:${reactiveGrpcVersion}:jdk8@jar"
        }
    }
    generateProtoTasks {
        ofSourceSet("main")*.plugins {
            grpc { }
            reactor {}
        }
    }
}
```
*At this time, Reactor-gRPC with Gradle only supports bash-based environments. Windows users will need to build using Windows Subsystem for Linux (win 10), Gitbash, or Cygwin.*

Usage
=====
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

## Don't break the chain
Used on their own, the generated RxGrpc stub methods do not cleanly chain with other RxJava operators.
Using the `compose()` and `as()` methods of `Mono` and `Flux` are preferred over direct invocation.

#### One→One, Many→Many
```java
Mono<HelloResponse> monoResponse = monoRequest.compose(stub::sayHello);
Flux<HelloResponse> fluxResponse = fluxRequest.compose(stub::sayHelloBothStream);
```

#### One→Many, Many→One
```java
Mono<HelloResponse> monoResponse = fluxRequest.as(stub::sayHelloRequestStream);
Flux<HelloResponse> fluxResponse = monoRequest.as(stub::sayHelloResponseStream);
```

## Retrying streaming requests
`GrpcRetry` is used to transparently re-establish a streaming gRPC request in the event of a server error. During a
retry, the upstream rx pipeline is re-subscribed to acquire a request message and the RPC call re-issued. The downstream
rx pipeline never sees the error.

```java
Flux<HelloResponse> fluxResponse = fluxRequest.compose(GrpcRetry.ManyToMany.retry(stub::sayHelloBothStream));
```

For complex retry scenarios, use the `Retry` builder from <a href="https://github.com/reactor/reactor-addons/blob/master/reactor-extra/src/main/java/reactor/retry/Retry.java">Reactor Extras</a>.

Modules
=======

Reactor-gRPC is broken down into four sub-modules:

* _reactor-grpc_ - a protoc generator for generating gRPC bindings for Reactor.
* _reactor-grpc-stub_ - stub classes supporting the generated Reactor bindings.
* _reactor-grpc-test_ - integration tests for Reactor.
* _reactor-grpc-tck_ - Reactive Streams TCK compliance tests for Reactor.
