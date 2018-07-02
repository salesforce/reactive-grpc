/*
 *  Copyright (c) 2017, salesforce.com, inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.servicelibs

import com.google.protobuf.Empty
import com.salesforce.grpc.contrib.spring.GrpcService
import org.slf4j.LoggerFactory
import reactor.core.publisher.EmitterProcessor
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/**
 * Demonstrates building a gRPC streaming server using Reactor and Reactive-Grpc.
 */
@GrpcService
class ChatImpl : ReactorChatGrpc.ChatImplBase() {
    private val logger = LoggerFactory.getLogger(ChatImpl::class.java)
    private val broadcast: EmitterProcessor<ChatProto.ChatMessage> = EmitterProcessor.create()

    override fun postMessage(request: Mono<ChatProto.ChatMessage>): Mono<Empty> {
        return request.map(this::broadcast)
    }

    private fun broadcast(message: ChatProto.ChatMessage): Empty {
        logger.info("${message.author}: ${message.message}")
        broadcast.onNext(message)
        return Empty.getDefaultInstance()
    }

    override fun getMessages(request: Mono<Empty>): Flux<ChatProto.ChatMessage> {
        return broadcast
    }
}
