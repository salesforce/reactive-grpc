/*
 *  Copyright (c) 2019, Salesforce.com, Inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.reactorgrpc.tck;

import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import org.reactivestreams.Publisher;
import org.reactivestreams.tck.PublisherVerification;
import org.reactivestreams.tck.TestEnvironment;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import reactor.core.publisher.Flux;

/**
 * Publisher tests from the Reactive Streams Technology Compatibility Kit.
 * https://github.com/reactive-streams/reactive-streams-jvm/tree/master/tck
 */
@SuppressWarnings("Duplicates")
@Test(timeOut = 3000)
public class ReactorGrpcPublisherManyToManyHalfFusedVerificationTest
        extends PublisherVerification<Message> {
    public static final long DEFAULT_TIMEOUT_MILLIS = 500L;
    public static final long PUBLISHER_REFERENCE_CLEANUP_TIMEOUT_MILLIS = 1000L;

    public ReactorGrpcPublisherManyToManyHalfFusedVerificationTest() {
        super(new TestEnvironment(DEFAULT_TIMEOUT_MILLIS, DEFAULT_TIMEOUT_MILLIS), PUBLISHER_REFERENCE_CLEANUP_TIMEOUT_MILLIS);
    }

    private static Server server;
    private static ManagedChannel channel;

    @BeforeClass
    public static void setup() throws Exception {
        System.out.println("ReactorGrpcPublisherManyToManyVerificationTest");
        server = InProcessServerBuilder.forName("ReactorGrpcPublisherManyToManyVerificationTest").addService(new FusedTckService()).build().start();
        channel = InProcessChannelBuilder.forName("ReactorGrpcPublisherManyToManyVerificationTest").usePlaintext().build();
    }

    @AfterClass
    public static void tearDown() throws Exception {
        channel.shutdownNow();
        server.shutdownNow();

        server = null;
        channel = null;
    }

    @Override
    public Publisher<Message> createPublisher(long elements) {
        ReactorTckGrpc.ReactorTckStub stub = ReactorTckGrpc.newReactorStub(channel);
        Flux<Message> request = Flux.range(0, (int)elements).map(this::toMessage);
        Publisher<Message> publisher = stub.manyToMany(request.hide());

        return publisher;
    }

    @Override
    public Publisher<Message> createFailedPublisher() {
        ReactorTckGrpc.ReactorTckStub stub = ReactorTckGrpc.newReactorStub(channel);
        Flux<Message> request = Flux.just(toMessage(TckService.KABOOM));
        Publisher<Message> publisher = stub.manyToMany(request.hide());

        return publisher;
    }

    private Message toMessage(int i) {
        return Message.newBuilder().setNumber(i).build();
    }
}
