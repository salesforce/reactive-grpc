/*
 *  Copyright (c) 2017, salesforce.com, inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.servicelibs

import com.salesforce.grpc.contrib.spring.GrpcServerHost
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.context.annotation.Bean

/**
 * Demonstrates building a gRPC streaming server using Reactor, Reactive-Grpc, grpc-spring, and Spring Boot.
 */
@SpringBootApplication(scanBasePackages = arrayOf("com.salesforce.servicelibs"))
class ReactorChatServer {
    private val logger = LoggerFactory.getLogger(ReactorChatServer::class.java)

    @Bean(initMethod = "start")
    fun grpcServerHost(@Value("\${port}") port: Int): GrpcServerHost {
        logger.info("Listening for gRPC on port $port")
        return GrpcServerHost(port)
    }

    @Bean
    fun chatImpl(): ReactorChatGrpc.ChatImplBase {
        return ChatImpl()
    }

}

fun main(args: Array<String>) {
    SpringApplication.run(ReactorChatServer::class.java, *args)
    Thread.currentThread().join()
}
