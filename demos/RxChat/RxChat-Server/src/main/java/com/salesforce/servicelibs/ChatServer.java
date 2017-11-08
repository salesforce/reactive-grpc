/*
 *  Copyright (c) 2017, salesforce.com, inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.servicelibs;

import com.salesforce.grpc.contrib.spring.GrpcServerHost;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * Demonstrates building a gRPC streaming server using RxJava, RxGrpc, grpc-spring, and Spring Boot.
 */
@SpringBootApplication
public class ChatServer {
    private final Logger logger = LoggerFactory.getLogger(ChatServer.class);

    public static void main(String[] args) throws Exception {
        SpringApplication.run(ChatServer.class, args);
        Thread.currentThread().join();
    }

    @Bean(initMethod = "start")
    public GrpcServerHost grpcServerHost(@Value("${port}") int port) {
        logger.info("Listening for gRPC on port " + port);
        GrpcServerHost host = new GrpcServerHost(port);
        return host;
    }

    @Bean
    public ChatGrpc.ChatImplBase chatImpl() {
        return new ChatImpl();
    }
}
