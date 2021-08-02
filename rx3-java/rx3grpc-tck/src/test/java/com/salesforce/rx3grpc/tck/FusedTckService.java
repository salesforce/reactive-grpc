/*
 *  Copyright (c) 2019, Salesforce.com, Inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.rx3grpc.tck;

import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;

public class FusedTckService extends Rx3TckGrpc.TckImplBase {
    public static final int KABOOM = -1;

    @Override
    public Single<Message> oneToOne(Single<Message> request) {
        return request.map(this::maybeExplode);
    }

    @Override
    public Flowable<Message> oneToMany(Single<Message> request) {
        return request
                .map(this::maybeExplode)
                .toFlowable()
                // send back no more than 10 responses
                .flatMap(message -> Flowable.range(1, Math.min(message.getNumber(), 10))
                        ,false, 1, 1)
                .map(this::toMessage);
    }

    @Override
    public Single<Message> manyToOne(Flowable<Message> request) {
        return request.map(this::maybeExplode).last(Message.newBuilder().setNumber(0).build());
    }

    @Override
    public Flowable<Message> manyToMany(Flowable<Message> request) {
        return request.map(this::maybeExplode);
    }

    private Message maybeExplode(Message req) throws Exception {
        if (req.getNumber() < 0) {
            throw new Exception("Kaboom!");
        } else {
            return req;
        }
    }

    private Message toMessage(int i) {
        return Message.newBuilder().setNumber(i).build();
    }
}
