/*
 *  Copyright (c) 2017, salesforce.com, inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.reactivegrpc.examples;

import io.grpc.ManagedChannel;
import io.grpc.netty.NettyChannelBuilder;
import io.reactivex.Single;

/**
 * This demo shows how clients and servers behave in the presence of backpressure. In this example, the client
 * consumes a continuous stream of numbers, briefly pausing every twenty to simulate work. These numbers are serialized
 * and transmitted until client and server's http/2 flow control buffers are full. Once that happens, backpressure kicks
 * in and the server stops producing until more buffer space becomes available, while the client process continuously.
 *
 *
 * Varying the size of the flow control window increases or decreases how much buffer space is available for
 * backpressure.
 */
public final class ContinuousBackpressureDemoClient {
    private static final int PAUSE_AFTER_N_MESSAGES = 20;
    private static final int PRINT_EVERY = 100;

    private ContinuousBackpressureDemoClient() { }

    public static void main(String[] args) throws Exception {
        ManagedChannel channel = NettyChannelBuilder
                .forAddress("localhost", ContinuousBackpressureDemoServer.PORT)
                .usePlaintext()
                .flowControlWindow(NettyChannelBuilder.DEFAULT_FLOW_CONTROL_WINDOW)
                .build();
        RxNumbersGrpc.RxNumbersStub stub = RxNumbersGrpc.newRxStub(channel);

        stub.oneToMany(Single.just(Message.getDefaultInstance()))
                .subscribe(m -> {
                    int i = m.getNumber();
                    if (i % PRINT_EVERY == 0) {
                        System.out.println(i);
                    }

                    try {
                        if (PAUSE_AFTER_N_MESSAGES != 0 && i % PAUSE_AFTER_N_MESSAGES == 0) {
                            Thread.sleep(2);
                        }
                    } catch (Exception e) { }
                });

        System.in.read();
    }
}
