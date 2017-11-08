/*
 *  Copyright (c) 2017, salesforce.com, inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.rxgrpc;

import com.google.common.base.Strings;
import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.DescriptorProtos.*;
import com.google.protobuf.compiler.PluginProtos;
import com.salesforce.jprotoc.Generator;
import com.salesforce.jprotoc.GeneratorException;
import com.salesforce.jprotoc.ProtoTypeMap;
import com.salesforce.jprotoc.ProtocPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A protoc generator for generating ReactiveX 2.0 bindings for gRPC.
 */
public class RxGrpcGenerator extends Generator {
    private static final String CLASS_PREFIX = "Rx";

    public static void main(String[] args) {
        ProtocPlugin.generate(new RxGrpcGenerator());
    }

    @Override
    public Stream<PluginProtos.CodeGeneratorResponse.File> generate(PluginProtos.CodeGeneratorRequest request) throws GeneratorException {
        final ProtoTypeMap typeMap = ProtoTypeMap.of(request.getProtoFileList());

        List<FileDescriptorProto> protosToGenerate = request.getProtoFileList().stream()
                .filter(protoFile -> request.getFileToGenerateList().contains(protoFile.getName()))
                .collect(Collectors.toList());

        List<ServiceContext> services = findServices(protosToGenerate, typeMap);
        List<PluginProtos.CodeGeneratorResponse.File> files = generateFiles(services);
        return files.stream();
    }

    private List<ServiceContext> findServices(List<FileDescriptorProto> protos, ProtoTypeMap typeMap) {
        List<ServiceContext> contexts = new ArrayList<>();

        for (FileDescriptorProto fileProto : protos) {
            for (ServiceDescriptorProto serviceProto : fileProto.getServiceList()) {
                ServiceContext serviceContext = buildServiceContext(serviceProto, typeMap);
                serviceContext.protoName = fileProto.getName();
                serviceContext.packageName = extractPackageName(fileProto);
                contexts.add(serviceContext);
            }
        }

        return contexts;
    }

    private String extractPackageName(DescriptorProtos.FileDescriptorProto proto) {
        DescriptorProtos.FileOptions options = proto.getOptions();
        if (options != null) {
            String javaPackage = options.getJavaPackage();
            if (!Strings.isNullOrEmpty(javaPackage)) {
                return javaPackage;
            }
        }

        return Strings.nullToEmpty(proto.getPackage());
    }

    private ServiceContext buildServiceContext(ServiceDescriptorProto serviceProto, ProtoTypeMap typeMap) {
        ServiceContext serviceContext = new ServiceContext();
        serviceContext.fileName = CLASS_PREFIX + serviceProto.getName() + "Grpc.java";
        serviceContext.className = CLASS_PREFIX + serviceProto.getName() + "Grpc";
        serviceContext.serviceName = serviceProto.getName();
        serviceContext.deprecated = serviceProto.getOptions() != null && serviceProto.getOptions().getDeprecated();

        serviceContext.oneToOne = serviceProto.getMethodList().stream()
                .filter(method -> !method.getClientStreaming() && !method.getServerStreaming())
                .map(methodProto -> buildMethodContext(methodProto, typeMap))
                .collect(Collectors.toList());

        serviceContext.oneToMany = serviceProto.getMethodList().stream()
                .filter(method -> !method.getClientStreaming() && method.getServerStreaming())
                .map(methodProto -> buildMethodContext(methodProto, typeMap))
                .collect(Collectors.toList());

        serviceContext.manyToOne = serviceProto.getMethodList().stream()
                .filter(method -> method.getClientStreaming() && !method.getServerStreaming())
                .map(methodProto -> buildMethodContext(methodProto, typeMap))
                .collect(Collectors.toList());

        serviceContext.manyToMany = serviceProto.getMethodList().stream()
                .filter(method -> method.getClientStreaming() && method.getServerStreaming())
                .map(methodProto -> buildMethodContext(methodProto, typeMap))
                .collect(Collectors.toList());

        return serviceContext;
    }

    private MethodContext buildMethodContext(MethodDescriptorProto methodProto, ProtoTypeMap typeMap) {
        MethodContext methodContext = new MethodContext();
        methodContext.methodName = lowerCaseFirst(methodProto.getName());
        methodContext.inputType = typeMap.toJavaTypeName(methodProto.getInputType());
        methodContext.outputType = typeMap.toJavaTypeName(methodProto.getOutputType());
        methodContext.deprecated = methodProto.getOptions() != null && methodProto.getOptions().getDeprecated();
        return methodContext;
    }

    private String lowerCaseFirst(String s) {
        return Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }

    private List<PluginProtos.CodeGeneratorResponse.File> generateFiles(List<ServiceContext> services) {
        return services.stream()
                .map(this::buildFile)
                .collect(Collectors.toList());
    }

    private PluginProtos.CodeGeneratorResponse.File buildFile(ServiceContext context) {
        String content = applyTemplate("RxStub.mustache", context);
        return PluginProtos.CodeGeneratorResponse.File
                .newBuilder()
                .setName(absoluteFileName(context))
                .setContent(content)
                .build();
    }

    private String absoluteFileName(ServiceContext ctx) {
        String dir = ctx.packageName.replace('.', '/');
        if (Strings.isNullOrEmpty(dir)) {
            return ctx.fileName;
        } else {
            return dir + "/" + ctx.fileName;
        }
    }

    /**
     * Template class for proto Service objects.
     */
    private class ServiceContext {
        // CHECKSTYLE DISABLE VisibilityModifier FOR 11 LINES
        public String fileName;
        public String protoName;
        public String packageName;
        public String className;
        public String serviceName;
        public boolean deprecated;

        public List<MethodContext> oneToOne;
        public List<MethodContext> oneToMany;
        public List<MethodContext> manyToOne;
        public List<MethodContext> manyToMany;

        public boolean getHasOneToOne() {
            return oneToOne != null && oneToOne.size() > 0;
        }

        public boolean getHasOneToMany() {
            return oneToMany != null && oneToMany.size() > 0;
        }

        public boolean getHasManyToOne() {
            return manyToOne != null && manyToOne.size() > 0;
        }

        public boolean getHasManyToMany() {
            return manyToMany != null && manyToMany.size() > 0;
        }
    }

    /**
     * Template class for proto RPC objects.
     */
    private class MethodContext {
        // CHECKSTYLE DISABLE VisibilityModifier FOR 4 LINES
        public String methodName;
        public String inputType;
        public String outputType;
        public boolean deprecated;
    }
}
