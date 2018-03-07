/*
 *  Copyright (c) 2017, salesforce.com, inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.reactivegrpc.examples;

import io.grpc.Server;
import io.grpc.netty.NettyServerBuilder;
import io.reactivex.Flowable;
import io.reactivex.Single;

import java.util.Iterator;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * This demo shows how clients and servers behave in the presence of backpressure. In this example, the server
 * produces a continuous stream of numbers as fast as it can. These numbers are serialized and transmitted until
 * client and server's http/2 flow control buffers are full. Once that happens, backpressure kicks in and the server
 * stops producing until more buffer space becomes available.
 *
 * Varying the size of the flow control window increases or decreases how much buffer space is available for
 * backpressure.
 */
public final class ContinuousBackpressureDemoServer extends RxNumbersGrpc.NumbersImplBase {
    public static final int PORT = 9999;
    private static final int PRINT_EVERY = 100;

    private ContinuousBackpressureDemoServer() { }

    public static void main(String[] args) throws Exception {
        Server server = NettyServerBuilder
                .forPort(PORT)
                .addService(new ContinuousBackpressureDemoServer())
                .flowControlWindow(NettyServerBuilder.DEFAULT_FLOW_CONTROL_WINDOW)
                .build()
                .start();

        System.out.println("Listening on port 9999");
        server.awaitTermination();
    }

    @Override
    public Flowable<Message> oneToMany(Single<Message> request) {
        return Flowable.fromIterable(() -> {
            // Return increasing integers as fast as possible forever.
            AtomicInteger i = new AtomicInteger(0);
            return new Iterator<Message>() {
                @Override
                public boolean hasNext() {
                    return i.get() < Integer.MAX_VALUE;
                }

                @Override
                public Message next() {
                    int j = i.getAndIncrement();
                    if (j % PRINT_EVERY == 0) {
                        System.out.println(j);
                    }
                    return Message.newBuilder().setNumber(j).build();
                }
            };
        });
    }
}
