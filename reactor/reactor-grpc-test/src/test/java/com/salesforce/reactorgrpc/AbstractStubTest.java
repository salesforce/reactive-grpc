package com.salesforce.reactorgrpc;

import io.grpc.Deadline;
import io.grpc.ManagedChannel;
import io.grpc.inprocess.InProcessChannelBuilder;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class AbstractStubTest {
    @Test
    public void getChannelWorks() {
        ManagedChannel channel = InProcessChannelBuilder.forName("settingCallOptionsWorks").build();
        ReactorGreeterGrpc.ReactorGreeterStub stub = ReactorGreeterGrpc.newReactorStub(channel);

        assertThat(stub.getChannel()).isEqualTo(channel);
    }

    @Test
    public void settingCallOptionsWorks() {
        ManagedChannel channel = InProcessChannelBuilder.forName("settingCallOptionsWorks").build();
        Deadline deadline = Deadline.after(42, TimeUnit.SECONDS);

        ReactorGreeterGrpc.ReactorGreeterStub stub = ReactorGreeterGrpc.newReactorStub(channel).withDeadline(deadline);

        assertThat(stub.getCallOptions().getDeadline()).isEqualTo(deadline);
    }
}
