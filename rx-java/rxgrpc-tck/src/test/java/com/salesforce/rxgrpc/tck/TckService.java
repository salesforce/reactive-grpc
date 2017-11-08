/*
 *  Copyright (c) 2017, salesforce.com, inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.rxgrpc.tck;

import io.reactivex.Flowable;
import io.reactivex.Single;

public class TckService extends RxTckGrpc.TckImplBase {
    public static final int KABOOM = -1;

    @Override
    public Single<Message> oneToOne(Single<Message> request) {
        return request.map(this::maybeExplode);
    }

    @Override
    public Flowable<Message> oneToMany(Single<Message> request) {
        return request
                .map(this::maybeExplode)
                // send back no more than 10 responses
                .flatMapPublisher(message -> Flowable.range(0, Math.min(message.getNumber(), 10)))
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