/*
 *  Copyright (c) 2017, salesforce.com, inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.servicelibs;

import com.google.protobuf.Empty;
import com.salesforce.grpc.contrib.spring.GrpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.EmitterProcessor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Demonstrates building a gRPC streaming server using Reactor and Reactive-Grpc.
 */
@GrpcService
public class ChatImpl extends ReactorChatGrpc.ChatImplBase {
    private final Logger logger = LoggerFactory.getLogger(ChatImpl.class);
    private final EmitterProcessor<ChatProto.ChatMessage> broadcast = EmitterProcessor.create();

    @Override
    public Mono<Empty> postMessage(Mono<ChatProto.ChatMessage> request) {
        return request.map(this::broadcast);
    }

    private Empty broadcast(ChatProto.ChatMessage message) {
        logger.info(message.getAuthor() + ": " + message.getMessage());
        broadcast.onNext(message);
        return Empty.getDefaultInstance();
    }

    @Override
    public Flux<ChatProto.ChatMessage> getMessages(Mono<Empty> request) {
        return broadcast;
    }
}
