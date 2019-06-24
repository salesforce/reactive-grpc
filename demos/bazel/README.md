# Bazel Reactive gRPC

This demo shows how to use the Reactive-gRPC Bazel rules.

## Setup

(Optional) Add `build --protocopt=--include_source_info` to your `.bazelrc` file.
When enabled the Reactive-gRPC generator will include comments from the proto files in the generated code.

Include this project as an external dependency in your `WORKSPACE`.

    load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")

    http_archive(
        name = "com_salesforce_servicelibs_reactive_grpc",
        sha256 = <sha256>,
        strip_prefix = "reactive-grpc-%s" % <version>,
        url = "https://github.com/salesforce/reactive-grpc/archive/%s.zip" % <version>,
    )

    load("@com_salesforce_servicelibs_reactive_grpc//bazel:repositories.bzl", reactive_grpc_repositories="repositories")
    reactive_grpc_repositories()
    
    load("@io_grpc_grpc_java//:repositories.bzl", "grpc_java_repositories")
    grpc_java_repositories()


In your build files use the following to generate reactive bindings.


    load("@com_salesforce_servicelibs_reactive_grpc//bazel:java_reactive_grpc_library.bzl", "reactor_grpc_library", "rx_grpc_library")
    
    reactor_grpc_library(
        name = "helloworld_reactor_grpc",
        proto = ":helloworld_proto",
        visibility = ["//visibility:public"],
        deps = [":helloworld_java_grpc"],
    )
    
    rx_grpc_library(
        name = "helloworld_rx_grpc",
        proto = ":helloworld_proto",
        visibility = ["//visibility:public"],
        deps = [":helloworld_java_grpc"],
    )

These targets can be used like any other `java_library` targets.
Via the `deps` attribute they depend on their respective `java_grpc_library` targets.
For more information on creating these see https://github.com/grpc/grpc-java.
