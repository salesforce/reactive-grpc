/*
 *  Copyright (c) 2019, Salesforce.com, Inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.reactorgrpc.tck;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class FusedTckService extends ReactorTckGrpc.TckImplBase {
    public static final int KABOOM = -1;

    @Override
    public Mono<Message> oneToOne(Mono<Message> request) {
        return request.map(this::maybeExplode);
    }

    @Override
    public Flux<Message> oneToMany(Mono<Message> request) {
        return request
                .map(this::maybeExplode)
                // send back no more than 10 responses
                .flatMapMany(message -> Flux.range(0, Math.min(message.getNumber(), 10)))
                .map(this::toMessage);
    }

    @Override
    public Mono<Message> manyToOne(Flux<Message> request) {
        return request.map(this::maybeExplode).last(Message.newBuilder().setNumber(0).build());
    }

    @Override
    public Flux<Message> manyToMany(Flux<Message> request) {
        return request.map(this::maybeExplode);
    }

    private Message maybeExplode(Message req) {
        if (req.getNumber() < 0) {
            throw new RuntimeException("Kaboom!");
        } else {
            return req;
        }
    }

    private Message toMessage(int i) {
        return Message.newBuilder().setNumber(i).build();
    }
}