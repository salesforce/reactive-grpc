/*
 *  Copyright (c) 2019, Salesforce.com, Inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.rx3grpc.tck;

import org.reactivestreams.Publisher;
import org.reactivestreams.tck.PublisherVerification;
import org.reactivestreams.tck.TestEnvironment;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.reactivex.rxjava3.core.Single;

/**
 * Publisher tests from the Reactive Streams Technology Compatibility Kit.
 * https://github.com/reactive-streams/reactive-streams-jvm/tree/master/tck
 */
@SuppressWarnings("Duplicates")
@Test(timeOut = 3000)
public class RxGrpcPublisherOneToOneVerificationTest extends PublisherVerification<Message> {
    public static final long DEFAULT_TIMEOUT_MILLIS = 500L;
    public static final long PUBLISHER_REFERENCE_CLEANUP_TIMEOUT_MILLIS = 1000L;

    public RxGrpcPublisherOneToOneVerificationTest() {
        super(new TestEnvironment(DEFAULT_TIMEOUT_MILLIS, DEFAULT_TIMEOUT_MILLIS), PUBLISHER_REFERENCE_CLEANUP_TIMEOUT_MILLIS);
    }

    private static Server server;
    private static ManagedChannel channel;

    @BeforeClass
    public static void setup() throws Exception {
        System.out.println("RxGrpcPublisherOneToOneVerificationTest");
        server = InProcessServerBuilder.forName("RxGrpcPublisherOneToOneVerificationTest").addService(new TckService()).build().start();
        channel = InProcessChannelBuilder.forName("RxGrpcPublisherOneToOneVerificationTest").usePlaintext().build();
    }

    @AfterClass
    public static void tearDown() throws Exception {
        channel.shutdownNow();
        server.shutdownNow();

        server = null;
        channel = null;
    }

    @Override
    public long maxElementsFromPublisher() {
        return 1;
    }

    @Override
    public Publisher<Message> createPublisher(long elements) {
        Rx3TckGrpc.RxTckStub stub = Rx3TckGrpc.newRxStub(channel);
        Single<Message> request = Single.just(toMessage((int) elements));
        Single<Message> publisher = request.compose(stub::oneToOne);

        return publisher.toFlowable();
    }

    @Override
    public Publisher<Message> createFailedPublisher() {
        Rx3TckGrpc.RxTckStub stub = Rx3TckGrpc.newRxStub(channel);
        Single<Message> request = Single.just(toMessage(TckService.KABOOM));
        Single<Message> publisher = request.compose(stub::oneToOne);

        return publisher.toFlowable();
    }

    private Message toMessage(int i) {
        return Message.newBuilder().setNumber(i).build();
    }
}
