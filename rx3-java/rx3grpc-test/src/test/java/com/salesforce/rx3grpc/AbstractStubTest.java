/*  Copyright (c) 2019, Salesforce.com, Inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.rx3grpc;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.TimeUnit;

import org.junit.Rule;
import org.junit.Test;

import io.grpc.Deadline;
import io.grpc.ManagedChannel;
import io.grpc.testing.GrpcServerRule;

public class AbstractStubTest {
    @Rule
    public GrpcServerRule serverRule = new GrpcServerRule();

    @Rule
    public UnhandledRxJavaErrorRule errorRule = new UnhandledRxJavaErrorRule().autoVerifyNoError();

    @Test
    public void getChannelWorks() {
        ManagedChannel channel = serverRule.getChannel();
        Rx3GreeterGrpc.RxGreeterStub stub = Rx3GreeterGrpc.newRxStub(channel);

        assertThat(stub.getChannel()).isEqualTo(channel);
    }

    @Test
    public void settingCallOptionsWorks() {
        ManagedChannel channel = serverRule.getChannel();
        Deadline deadline = Deadline.after(42, TimeUnit.SECONDS);

        Rx3GreeterGrpc.RxGreeterStub stub = Rx3GreeterGrpc.newRxStub(channel).withDeadline(deadline);

        assertThat(stub.getCallOptions().getDeadline()).isEqualTo(deadline);
    }
}
