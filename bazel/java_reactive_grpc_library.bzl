load("@bazel_tools//tools/jdk:toolchain_utils.bzl", "find_java_runtime_toolchain", "find_java_toolchain")

# Taken from bazelbuild/rules_go license: Apache 2
# https://github.com/bazelbuild/rules_go/blob/528f6faf83f85c23da367d61f784893d1b3bd72b/proto/compiler.bzl#L94
# replaced `prefix = paths.join(..` with `prefix = "/".join(..`
def _proto_path(src, proto):
    """proto_path returns the string used to import the proto. This is the proto
    source path within its repository, adjusted by import_prefix and
    strip_import_prefix.

    Args:
        src: the proto source File.
        proto: the ProtoInfo provider.

    Returns:
        An import path string.
    """
    if not hasattr(proto, "proto_source_root"):
        # Legacy path. Remove when Bazel minimum version >= 0.21.0.
        path = src.path
        root = src.root.path
        ws = src.owner.workspace_root
        if path.startswith(root):
            path = path[len(root):]
        if path.startswith("/"):
            path = path[1:]
        if path.startswith(ws):
            path = path[len(ws):]
        if path.startswith("/"):
            path = path[1:]
        return path

    if proto.proto_source_root == ".":
        # true if proto sources were generated
        prefix = src.root.path + "/"
    elif proto.proto_source_root.startswith(src.root.path):
        # sometimes true when import paths are adjusted with import_prefix
        prefix = proto.proto_source_root + "/"
    else:
        # usually true when paths are not adjusted
        prefix = "/".join([src.root.path, proto.proto_source_root]) + "/"
    if not src.path.startswith(prefix):
        # sometimes true when importing multiple adjusted protos
        return src.path
    return src.path[len(prefix):]

def _reactive_grpc_library_impl(ctx):
    proto = ctx.attr.proto[ProtoInfo]
    descriptor_set_in = proto.transitive_descriptor_sets

    gensrcjar = ctx.actions.declare_file("%s-proto-gensrc.jar" % ctx.label.name)

    args = ctx.actions.args()
    args.add(ctx.executable.reactive_plugin.path, format = "--plugin=protoc-gen-reactive-grpc-plugin=%s")
    args.add("--reactive-grpc-plugin_out=:{0}".format(gensrcjar.path))
    args.add_joined("--descriptor_set_in", descriptor_set_in, join_with = ctx.configuration.host_path_separator)
    for src in proto.check_deps_sources.to_list():
        args.add(_proto_path(src, proto))

    ctx.actions.run(
        inputs = descriptor_set_in,
        tools = [ctx.executable.reactive_plugin],
        outputs = [gensrcjar],
        executable = ctx.executable._protoc,
        arguments = [args],
    )

    deps = [java_common.make_non_strict(dep[JavaInfo]) for dep in ctx.attr.deps]
    deps += [dep[JavaInfo] for dep in ctx.attr.reactive_deps]

    java_info = java_common.compile(
        ctx,
        deps = deps,
        java_toolchain = find_java_toolchain(ctx, ctx.attr._java_toolchain),
        output = ctx.outputs.jar,
        output_source_jar = ctx.outputs.srcjar,
        source_jars = [gensrcjar],
    )

    return [java_info]

_reactive_grpc_library = rule(
    attrs = {
        "proto": attr.label(
            mandatory = True,
            providers = [ProtoInfo],
        ),
        "deps": attr.label_list(
            mandatory = True,
            allow_empty = False,
            providers = [JavaInfo],
        ),
        "_protoc": attr.label(
            default = Label("@com_google_protobuf//:protoc"),
            executable = True,
            cfg = "host",
        ),
        "reactive_deps": attr.label_list(
            mandatory = True,
            allow_empty = False,
            providers = [JavaInfo],
        ),
        "reactive_plugin": attr.label(
            mandatory = True,
            executable = True,
            cfg = "host",
        ),
        "_java_toolchain": attr.label(
            default = Label("@bazel_tools//tools/jdk:current_java_toolchain"),
        ),
    },
    fragments = ["java"],
    outputs = {
        "jar": "lib%{name}.jar",
        "srcjar": "lib%{name}-src.jar",
    },
    provides = [JavaInfo],
    toolchains = ["@@bazel_tools//tools/jdk:toolchain_type"],
    implementation = _reactive_grpc_library_impl,
)

def reactor_grpc_library(**kwargs):
    _reactive_grpc_library(
        reactive_plugin = "@com_salesforce_servicelibs_reactive_grpc//reactor/reactor-grpc:reactor_grpc_bin",
        reactive_deps = [
            "@com_salesforce_servicelibs_reactive_grpc//reactor/reactor-grpc-stub",
            "@io_projectreactor_reactor_core",
        ],
        **kwargs
    )

def rx_grpc_library(**kwargs):
    _reactive_grpc_library(
        reactive_plugin = "@com_salesforce_servicelibs_reactive_grpc//rx-java/rxgrpc:rxgrpc_bin",
        reactive_deps = [
            "@com_salesforce_servicelibs_reactive_grpc//rx-java/rxgrpc-stub",
            "@com_salesforce_servicelibs_reactive_grpc//common/reactive-grpc-common",
            "@io_reactivex_rxjava2_rxjava",
        ],
        **kwargs
    )
