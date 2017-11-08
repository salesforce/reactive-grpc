/*
 *  Copyright (c) 2017, salesforce.com, inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.rxgrpc.tck;

import com.salesforce.rxgrpc.stub.RxFlowableBackpressureOnReadyHandler;
import io.grpc.stub.ClientCallStreamObserver;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.reactivestreams.tck.SubscriberWhiteboxVerification;
import org.reactivestreams.tck.TestEnvironment;
import org.testng.annotations.BeforeClass;

import javax.annotation.Nullable;

/**
 * Subscriber tests from the Reactive Streams Technology Compatibility Kit.
 * https://github.com/reactive-streams/reactive-streams-jvm/tree/master/tck
 */
public class RxGrpcSubscriberWhiteboxVerificationTest extends SubscriberWhiteboxVerification<Message> {
    public RxGrpcSubscriberWhiteboxVerificationTest() {
        super(new TestEnvironment());
    }

    @BeforeClass
    public static void setup() throws Exception {
        System.out.println("RxGrpcSubscriberWhiteboxVerificationTest");
    }

    @Override
    public Subscriber<Message> createSubscriber(WhiteboxSubscriberProbe<Message> probe) {
        return new RxFlowableBackpressureOnReadyHandler<Message>(new StubServerCallStreamObserver()) {
            @Override
            public void onSubscribe(final Subscription s) {
                super.onSubscribe(s);

                // register a successful Subscription, and create a Puppet,
                // for the WhiteboxVerification to be able to drive its tests:
                probe.registerOnSubscribe(new SubscriberPuppet() {

                    @Override
                    public void triggerRequest(long elements) {
                        s.request(elements);
                    }

                    @Override
                    public void signalCancel() {
                        s.cancel();
                    }
                });

                super.run();
            }

            @Override
            public void onNext(Message element) {
                // in addition to normal Subscriber work that you're testing, register onNext with the probe
                super.onNext(element);
                probe.registerOnNext(element);
            }

            @Override
            public void onError(Throwable cause) {
                // in addition to normal Subscriber work that you're testing, register onError with the probe
                super.onError(cause);
                probe.registerOnError(cause);
            }

            @Override
            public void onComplete() {
                // in addition to normal Subscriber work that you're testing, register onComplete with the probe
                super.onComplete();
                probe.registerOnComplete();
            }
        };
    }

    @Override
    public Message createElement(int i) {
        return Message.newBuilder().setNumber(i).build();
    }

    private final class StubServerCallStreamObserver extends ClientCallStreamObserver<Message> {
        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setOnReadyHandler(Runnable onReadyHandler) {

        }

        @Override
        public void disableAutoInboundFlowControl() {

        }

        @Override
        public void request(int count) {
            System.out.println("Request " + count);
        }

        @Override
        public void setMessageCompression(boolean enable) {

        }

        @Override
        public void onNext(Message value) {
            System.out.println(value.getNumber());
        }

        @Override
        public void onError(Throwable t) {
            System.out.println(t.getMessage());
        }

        @Override
        public void onCompleted() {
            System.out.println("Completed");
        }

        @Override
        public void cancel(@Nullable String s, @Nullable Throwable throwable) {
            
        }
    }
}
