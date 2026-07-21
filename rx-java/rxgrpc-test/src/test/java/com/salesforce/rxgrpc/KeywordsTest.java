/*
 *  Copyright (c) 2019, Salesforce.com, Inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.rxgrpc;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.reactivex.Single;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SuppressWarnings("Duplicates")
public class KeywordsTest {
    @Rule
    public UnhandledRxJavaErrorRule errorRule = new UnhandledRxJavaErrorRule().autoVerifyNoError();

    private static Server server;
    private static ManagedChannel channel;

    @BeforeClass
    public static void setupServer() throws Exception {
        RxKeywordsGrpc.KeywordsImplBase svc = new RxKeywordsGrpc.KeywordsImplBase() {

            @Override
            public Single<BoolResponse> boolean_(Single<BoolRequest> request) {
                return request.map(r -> !r.getValue()).map(b -> BoolResponse.newBuilder().setValue(b).build());
            }
        };

        server = ServerBuilder.forPort(9000).addService(svc).build().start();
        channel = ManagedChannelBuilder.forAddress("localhost", server.getPort()).usePlaintext().build();
    }

    @AfterClass
    public static void stopServer() throws InterruptedException {
        server.shutdown();
        server.awaitTermination();
        channel.shutdown();

        server = null;
        channel = null;
    }

    @Test
    public void not() {
        AtomicBoolean called = new AtomicBoolean(false);
        RxKeywordsGrpc.RxKeywordsStub stub = RxKeywordsGrpc.newRxStub(channel);

        BoolRequest request = BoolRequest.newBuilder().setValue(false).build();

        stub.boolean_(request).map(BoolResponse::getValue).subscribe(r -> {
            assertThat(r.booleanValue()).isTrue();
            called.set(true);
        });

        await().atMost(1, TimeUnit.SECONDS).untilTrue(called);
    }
}
