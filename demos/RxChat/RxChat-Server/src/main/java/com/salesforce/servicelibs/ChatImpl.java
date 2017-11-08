/*
 *  Copyright (c) 2017, salesforce.com, inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.servicelibs;

import com.google.protobuf.Empty;
import com.salesforce.grpc.contrib.spring.GrpcService;
import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import io.reactivex.Single;
import io.reactivex.subjects.PublishSubject;
import io.reactivex.subjects.Subject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Demonstrates building a gRPC streaming server using RxJava and RxGrpc.
 */
@GrpcService
public class ChatImpl extends RxChatGrpc.ChatImplBase {
    private final Logger logger = LoggerFactory.getLogger(ChatImpl.class);
    private final Subject<ChatProto.ChatMessage> broadcast = PublishSubject.create();

    @Override
    public Single<Empty> postMessage(Single<ChatProto.ChatMessage> request) {
        return request.map(this::broadcast);
    }

    private Empty broadcast(ChatProto.ChatMessage message) {
        logger.info(message.getAuthor() + ": " + message.getMessage());
        broadcast.onNext(message);
        return Empty.getDefaultInstance();
    }

    @Override
    public Flowable<ChatProto.ChatMessage> getMessages(Single<Empty> request) {
        return broadcast.toFlowable(BackpressureStrategy.BUFFER);
    }
}
