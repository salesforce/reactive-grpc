Overview
========
RxGrpc is a new set of gRPC bindings for reactive programming with [RxJava](https://github.com/ReactiveX/RxJava).

### Android support
Reactive gRPC supports Android to the same level of the underlying reactive technologies. The generated Rx-Java binding
code targets Java 6, so it _should_ work with all versions of Android >= 2.3 (SDK 9).

See: https://github.com/ReactiveX/RxJava#version-2x-javadoc

Installation
============
### Maven
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
### Gradle
To use RxGrpc with the `protobuf-gradle-plugin`, add a RxGrpc to the protobuf `plugins` section.
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
        rxgrpc {
            artifact = "com.salesforce.servicelibs:rxgrpc:${reactiveGrpcVersion}"
        }
    }
    generateProtoTasks {
        ofSourceSet("main")*.plugins {
            grpc { }
            rxgrpc {}
        }
    }
}
```
*At this time, RxGrpc with Gradle only supports bash-based environments. Windows users will need to build using Windows 
Subsystem for Linux (win 10) or invoke the Maven protobuf plugin with Gradle.*
### Bazel

To use RxGrpc with Bazel, update your `WORKSPACE` file.

```bazel
load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")

http_archive(
    name = "com_salesforce_servicelibs_reactive_grpc",
    strip_prefix = "reactive-grpc-1.0.1",
    url = "https://github.com/salesforce/reactive-grpc/archive/v1.0.1.zip",
)

load("@com_salesforce_servicelibs_reactive_grpc//bazel:repositories.bzl", "repositories")

repositories()
```

Then, add a `rx_grpc_library()` rule to your proto's `BUILD` file, referencing both the `proto_library()` target and
the `java_proto_library()` target.

```bazel
load("@com_salesforce_servicelibs_reactive_grpc//bazel:java_reactive_grpc_library.bzl", "rx_grpc_library")

rx_grpc_library(
    name = "foo_rx_proto",
    proto = ":foo_proto",
    deps = [":foo_java_proto"],
)
```
Usage
=====
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
  Flowable<HelloResponse> resp = req.compose(stub::sayHelloBothStream);
  resp.subscribe(...);
  ```
  
## Don't break the chain
Used on their own, the generated RxGrpc stub methods do not cleanly chain with other RxJava operators.
Using the `compose()` and `as()` methods of `Single` and `Flowable` are preferred over direct invocation.

#### One→One, Many→Many
```java
Single<HelloResponse> singleResponse = singleRequest.compose(stub::sayHello);
Flowable<HelloResponse> flowableResponse = flowableRequest.compose(stub::sayHelloBothStream);
```

#### One→Many, Many→One
```java
Single<HelloResponse> singleResponse = flowableRequest.as(stub::sayHelloRequestStream);
Flowable<HelloResponse> flowableResponse = singleRequest.as(stub::sayHelloResponseStream);
```
  
## Retrying streaming requests
`GrpcRetry` is used to transparently re-establish a streaming gRPC request in the event of a server error. During a 
retry, the upstream rx pipeline is re-subscribed to acquire a request message and the RPC call re-issued. The downstream
rx pipeline never sees the error.

```java
Flowable<HelloResponse> flowableResponse = flowableRequest.compose(GrpcRetry.ManyToMany.retry(stub::sayHelloBothStream));
```

For complex retry scenarios, use the `RetryWhen` builder from <a href="https://davidmoten.github.io/rxjava2-extras/apidocs/com/github/davidmoten/rx2/RetryWhen.html">RxJava2 Extras</a>.
  
## gRPC Context propagation
Because the non-blocking nature of RX, RX-Java tends to switch between threads a lot. 
GRPC stores its context in the Thread context and is therefore often lost when RX 
switches threads. To solve this problem, you can add a hook that makes the Context
switch when RX switches threads:

```java
RxJavaPlugins.setScheduleHandler(new GrpcContextOnScheduleHook())
```    
    
To make sure you only run this piece of code once, you can for example add a utiltily class 
you can call after creating a stub, to make sure the handler is installed.

```java
public class RxContextPropagator {

	private static AtomicBoolean INSTALLED = new AtomicBoolean();

	public static void ensureInstalled() {
		if (INSTALLED.compareAndSet(false, true)) {
			RxJavaPlugins.setScheduleHandler(new GrpcContextOnScheduleHook());
		}
	}
}
```

## Configuration of flow control
RX GRPC by default prefetch 512 items on client and server side. When the messages are bigger it
can consume a lot of memory. One can override these default settings using RxCallOptions:

Prefetch on client side (client consumes too slow):

```java
    RxMyAPIStub api = RxMyAPIGrpc.newRxStub(channel)
        .withOption(RxCallOptions.CALL_OPTIONS_PREFETCH, 16)
        .withOption(RxCallOptions.CALL_OPTIONS_LOW_TIDE, 4);
```

Prefetch on server side (server consumes too slow):

```java
    // Override getCallOptions method in your *ImplBase service class.
    // One can use methodId to do method specific override
    @Override
    protected CallOptions getCallOptions(int methodId) {
        return CallOptions.DEFAULT
            .withOption(RxCallOptions.CALL_OPTIONS_PREFETCH, 16)
            .withOption(RxCallOptions.CALL_OPTIONS_LOW_TIDE, 4);
    }
```
  
Modules
=======

RxGrpc is broken down into four sub-modules:

* _rxgrpc_ - a protoc generator for generating gRPC bindings for RxJava.
* _rxgrpc-stub_ - stub classes supporting the generated RxGrpc bindings.
* _rxgrpc-test_ - integration tests for RxGrpc.
* _rxgrpc-tck_ - Reactive Streams TCK compliance tests for RxGrpc.
