# Reactive-gRPC Interoperability Tests

This directory contains three gRPC Hello-World implementations in Java, C#, and Go. They represent the three base
implementations of gRPC - Java, C-Core, and Go. The Java service uses a Reactive-gRPC Reactor stub instead of a basic
gRPC-Java stub.

These three services excercise the following cases:

* Reactive-gRPC calling gRPC-C
* gRPC-C calling Reactive-gRPC
* Reactive-gRPC calling gRPC-Go
* gRPC-Go calling Reactive-gRPC

## Running

Each directory has a `run.sh` script that builds and starts each service. Start all three services and then follow
the on-screen prompts.
