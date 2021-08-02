/*
 *  Copyright (c) 2019, Salesforce.com, Inc.
 *  All rights reserved.
 *  Licensed under the BSD 3-Clause license.
 *  For full license text, see LICENSE.txt file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.rx3grpc;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.TimeUnit;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.ForwardingClientCall;
import io.grpc.ForwardingClientCallListener;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.ServerInterceptors;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;

@SuppressWarnings("Duplicates")
public class ContextPropagationIntegrationTest {
    @Rule
    public UnhandledRxJavaErrorRule errorRule = new UnhandledRxJavaErrorRule().autoVerifyNoError();

    private static Server server;
    private static ManagedChannel channel;

    private static Context.Key<String> ctxKey = Context.key("ctxKey");
    private static Single<HelloRequest> worldReq = Single.just(HelloRequest.newBuilder().setName("World").build());

    private static TestService svc = new TestService();
    private static TestClientInterceptor clientInterceptor = new TestClientInterceptor();
    private static TestServerInterceptor serverInterceptor = new TestServerInterceptor();

    private static class TestClientInterceptor implements ClientInterceptor {
        private String sendMessageCtxValue;

        public void reset() {
            sendMessageCtxValue = null;
        }

        public String getSendMessageCtxValue() {
            return sendMessageCtxValue;
        }

        @Override
        public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {
            return new ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT>(next.newCall(method, callOptions)) {
                @Override
                public void start(Listener<RespT> responseListener, Metadata headers) {
                    super.start(new ForwardingClientCallListener.SimpleForwardingClientCallListener<RespT>(responseListener){
                        @Override
                        public void onMessage(RespT message) {
                            Context.current().withValue(ctxKey, "ClientGetsContext").run(() -> super.onMessage(message));
                        }
                    }, headers);
                }

                @Override
                public void sendMessage(ReqT message) {
                    sendMessageCtxValue = ctxKey.get();
                    super.sendMessage(message);
                }
            };
        }
    }

    private static class TestServerInterceptor implements ServerInterceptor {
        @Override
        public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
            Context ctx = Context.current().withValue(ctxKey, "ServerAcceptsContext");
            return Contexts.interceptCall(ctx, call, headers, next);
        }
    }

    private static class TestService extends Rx3GreeterGrpc.GreeterImplBase {
        private String receivedCtxValue;

        public String getReceivedCtxValue() {
            return receivedCtxValue;
        }

        private void reset() {
            receivedCtxValue = null;
        }

        @Override
        public Single<HelloResponse> sayHello(Single<HelloRequest> request) {
            return request
                    .doOnSuccess(x -> receivedCtxValue = ctxKey.get())
                    .map(HelloRequest::getName)
                    .map(name -> HelloResponse.newBuilder().setMessage("Hello " + name).build());
        }
    }

    @BeforeClass
    public static void setupServer() throws Exception {
        server = ServerBuilder.forPort(9000).addService(ServerInterceptors.intercept(svc, serverInterceptor)).build().start();
        channel = ManagedChannelBuilder.forAddress("localhost", server.getPort()).usePlaintext().intercept(clientInterceptor).build();
    }

    @Before
    public void resetServerStats() {
        svc.reset();
        clientInterceptor.reset();
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
    public void ClientSendsContext() {
        Rx3GreeterGrpc.RxGreeterStub stub = Rx3GreeterGrpc.newRxStub(channel);
        Context.current()
                .withValue(ctxKey, "ClientSendsContext")
                .run(() -> {
                    try {
                        worldReq.compose(stub::sayHello).test().await(1, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                });

        assertThat(clientInterceptor.getSendMessageCtxValue()).isEqualTo("ClientSendsContext");
    }

    @Test
    public void ClientGetsContext() throws InterruptedException {
        Rx3GreeterGrpc.RxGreeterStub stub = Rx3GreeterGrpc.newRxStub(channel);

        TestObserver<HelloResponse> testObserver = worldReq
                .compose(stub::sayHello)
                .doOnSuccess(resp -> {
                    Context ctx = Context.current();
                    assertThat(ctxKey.get(ctx)).isEqualTo("ClientGetsContext");
                })
                .test();

        testObserver.await(1, TimeUnit.SECONDS);
        testObserver.assertComplete();
    }

    @Test
    public void ServerAcceptsContext() throws InterruptedException {
        Rx3GreeterGrpc.RxGreeterStub stub = Rx3GreeterGrpc.newRxStub(channel);

        worldReq.compose(stub::sayHello).test().await(1, TimeUnit.SECONDS);

        assertThat(svc.getReceivedCtxValue()).isEqualTo("ServerAcceptsContext");
    }
}
