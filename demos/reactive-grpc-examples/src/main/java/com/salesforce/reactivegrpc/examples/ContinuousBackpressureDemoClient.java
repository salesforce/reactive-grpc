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
public class ContinuousBackpressureDemoClient {
    public static void main(String[] args) throws Exception {
        ManagedChannel channel = NettyChannelBuilder
                .forAddress("localhost", 9999)
                .usePlaintext(true)
                .flowControlWindow(NettyChannelBuilder.DEFAULT_FLOW_CONTROL_WINDOW / 1024)
                .build();
        RxNumbersGrpc.RxNumbersStub stub = RxNumbersGrpc.newRxStub(channel);

        stub.oneToMany(Single.just(Message.getDefaultInstance()))
                .subscribe(m -> {
                    int i = m.getNumber();
                    System.out.println(i);

                    try {
                        if (i % 20 == 0) {
                            Thread.sleep(1);
                        }
                    } catch (Exception e) {}
                });

        System.in.read();
    }
}
