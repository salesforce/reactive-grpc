[![Javadocs](https://javadoc.io/badge/com.salesforce.servicelibs/rxgrpc-stub.svg)](https://javadoc.io/doc/com.salesforce.servicelibs/rxgrpc-stub)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.salesforce.servicelibs/rxgrpc/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.salesforce.servicelibs/rxgrpc)


Usage
=====
```xml
<project>
    
    ...
    
    <build>
        <extensions>
            <extension>
                <groupId>kr.motd.maven</groupId>
                <artifactId>os-maven-plugin</artifactId>
                <version>1.4.1.Final</version>
            </extension>
        </extensions>
        <plugins>
            <plugin>
                <groupId>org.xolstice.maven.plugins</groupId>
                <artifactId>protobuf-maven-plugin</artifactId>
                <version>0.5.0</version>
                <configuration>
                    <protocArtifact>com.google.protobuf:protoc:3.0.2:exe:${os.detected.classifier}</protocArtifact>
                    <pluginId>grpc-java</pluginId>
                    <pluginArtifact>io.grpc:protoc-gen-grpc-java:1.2.0:exe:${os.detected.classifier}</pluginArtifact>
                </configuration>
                <executions>
                    <execution>
                        <goals>
                            <goal>test-compile</goal>
                            <goal>test-compile-custom</goal>
                        </goals>
                        <configuration>
                            <protocPlugins>
                                <protocPlugin>
                                    <id>rxgrpc</id>
                                    <groupId>com.salesforce.servicelibs</groupId>
                                    <artifactId>rxgrpc</artifactId>
                                    <version>[VERSION]</version>
                                    <mainClass>com.salesforce.rxgrpc.RxGrpcGenerator</mainClass>
                                </protocPlugin>
                            </protocPlugins>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>
```