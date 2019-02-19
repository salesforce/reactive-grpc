/*  Copyright (c) 2019, Salesforce.com, Inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.reactorgrpc;

import java.util.concurrent.TimeUnit;

import io.grpc.Deadline;
import io.grpc.ManagedChannel;
import io.grpc.testing.GrpcServerRule;
import org.junit.Rule;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AbstractStubTest {
    @Rule
    public GrpcServerRule serverRule = new GrpcServerRule();

    @Test
    public void getChannelWorks() {
        ManagedChannel channel = serverRule.getChannel();
        ReactorGreeterGrpc.ReactorGreeterStub stub = ReactorGreeterGrpc.newReactorStub(channel);

        assertThat(stub.getChannel()).isEqualTo(channel);
    }

    @Test
    public void settingCallOptionsWorks() {
        ManagedChannel channel = serverRule.getChannel();
        Deadline deadline = Deadline.after(42, TimeUnit.SECONDS);

        ReactorGreeterGrpc.ReactorGreeterStub stub = ReactorGreeterGrpc.newReactorStub(channel).withDeadline(deadline);

        assertThat(stub.getCallOptions().getDeadline()).isEqualTo(deadline);
    }
}
