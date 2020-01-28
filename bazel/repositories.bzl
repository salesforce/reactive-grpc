load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")
load("@bazel_tools//tools/build_defs/repo:java.bzl", "java_import_external")

def repositories(
        omit_org_reactivestreams_reactive_streams = False,
        omit_io_projectreactor_reactor_core = False,
        omit_io_reactivex_rxjava2_rxjava = False,
        omit_io_grpc_grpc_java = False,
        omit_com_salesforce_servicelibs_jprotoc = False,
        omit_com_github_spullara_mustache_java_compiler = False):
    if not omit_org_reactivestreams_reactive_streams:
        java_import_external(
            name = "org_reactivestreams_reactive_streams",
            jar_urls = ["https://repo.maven.apache.org/maven2/org/reactivestreams/reactive-streams/1.0.2/reactive-streams-1.0.2.jar"],
            jar_sha256 = "cc09ab0b140e0d0496c2165d4b32ce24f4d6446c0a26c5dc77b06bdf99ee8fae",
            srcjar_urls = ["https://repo.maven.apache.org/maven2/org/reactivestreams/reactive-streams/1.0.2/reactive-streams-1.0.2-sources.jar"],
            srcjar_sha256 = "963a6480f46a64013d0f144ba41c6c6e63c4d34b655761717a436492886f3667",
            licenses = ["notice"],  # Apache 2.0
        )

    if not omit_io_projectreactor_reactor_core:
        java_import_external(
            name = "io_projectreactor_reactor_core",
            jar_urls = ["https://repo.maven.apache.org/maven2/io/projectreactor/reactor-core/3.2.6.RELEASE/reactor-core-3.2.6.RELEASE.jar"],
            jar_sha256 = "8962081aa9e0fbe1685cc2746471b232f93f58e269cc89b54efebcb99c65af1a",
            srcjar_urls = ["https://repo.maven.apache.org/maven2/io/projectreactor/reactor-core/3.2.6.RELEASE/reactor-core-3.2.6.RELEASE-sources.jar"],
            srcjar_sha256 = "b871669ed12aee2af22f20a611bf871c7f848caede85bc19b0ef08ef5f79bc46",
            licenses = ["notice"],  # Apache 2.0
        )

    if not omit_io_reactivex_rxjava2_rxjava:
        java_import_external(
            name = "io_reactivex_rxjava2_rxjava",
            jar_urls = ["https://repo.maven.apache.org/maven2/io/reactivex/rxjava2/rxjava/2.2.7/rxjava-2.2.7.jar"],
            jar_sha256 = "23798f1b5fecac2aaaa3e224fd0e73f41dc081802c7bd2a6e91030bad36b9013",
            srcjar_urls = ["https://repo.maven.apache.org/maven2/io/reactivex/rxjava2/rxjava/2.2.7/rxjava-2.2.7-sources.jar"],
            srcjar_sha256 = "b7ee7e2b2ce07eda19755e511757427701f4081a051cace1efd69cf0bfcc8ff2",
            licenses = ["notice"],  # Apache 2.0
        )

    if not omit_io_grpc_grpc_java:
        io_grpc_grpc_java_version = "v1.21.0"

        http_archive(
            name = "io_grpc_grpc_java",
            sha256 = "2137a2b568e8266d6c269c995c7ba68db3d8d7d7936087c540fdbfadae577f81",
            strip_prefix = "grpc-java-%s" % io_grpc_grpc_java_version[1:],
            urls = ["https://github.com/grpc/grpc-java/archive/%s.zip" % io_grpc_grpc_java_version],
        )

    if not omit_com_salesforce_servicelibs_jprotoc:
        java_import_external(
            name = "com_salesforce_servicelibs_jprotoc",
            jar_urls = ["https://repo.maven.apache.org/maven2/com/salesforce/servicelibs/jprotoc/0.9.1/jprotoc-0.9.1.jar"],
            jar_sha256 = "55d78aafa930693856055e7d1d63414670beb59a9b253ece5cf546541b4bbd07",
            srcjar_urls = ["https://repo.maven.apache.org/maven2/com/salesforce/servicelibs/jprotoc/0.9.1/jprotoc-0.9.1-sources.jar"],
            srcjar_sha256 = "ba023a2097874fa7131c277eab69ca748928627bea122a48ef9cb54ca8dafd91",
            licenses = ["notice"],  # BSD 3-Clause
        )

    if not omit_com_github_spullara_mustache_java_compiler:
        java_import_external(
            name = "com_github_spullara_mustache_java_compiler",
            jar_urls = ["https://repo.maven.apache.org/maven2/com/github/spullara/mustache/java/compiler/0.9.6/compiler-0.9.6.jar"],
            jar_sha256 = "c4d697fd3619cb616cc5e22e9530c8a4fd4a8e9a76953c0655ee627cb2d22318",
            srcjar_urls = ["https://repo.maven.apache.org/maven2/com/github/spullara/mustache/java/compiler/0.9.6/compiler-0.9.6-sources.jar"],
            srcjar_sha256 = "fb3cf89e4daa0aaa4e659aca12a8ddb0d7b605271285f3e108201e0a389b4c7a",
            licenses = ["notice"],  # Apache 2.0
        )
