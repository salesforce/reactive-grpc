[![Build Status](https://travis-ci.org/salesforce/reactive-grpc.svg?branch=master)](https://travis-ci.org/salesforce/reactive-grpc)
[![codecov](https://codecov.io/gh/salesforce/reactive-grpc/branch/master/graph/badge.svg)](https://codecov.io/gh/salesforce/reactive-grpc)

What is reactive-grpc?
======================
Reactive gRPC is a suite of libraries for using gRPC with reactive programming libraries. Using a protocol buffers
compiler plugin, Reactive gRPC generates alternative gRPC bindings for each reactive technology.

Reactive gRPC supports the following reactive programming models:

* [RxJava 2](https://github.com/ReactiveX/RxJava)
* [Spring Reactor](https://projectreactor.io/)
* (Eventually) [Java9 Flow](https://community.oracle.com/docs/DOC-1006738)

Usage
=====
See the readme in each technology-specific sub-directory for usage details.