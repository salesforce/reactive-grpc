package com.salesforce.servicelibs.reactivegrpc;

import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.reactivex.Single;

public class GradleProof extends RxGreeterGrpc.GreeterImplBase {
    public static void main(String[] args) throws Exception {
        GradleProof proof = new GradleProof();
        try {
            proof.startServer();
            System.out.println(proof.doClient("World"));
        } finally {
            proof.stopServer();
        }
    }

    private Server server;

    public void startServer() throws Exception {
        server = InProcessServerBuilder
                .forName("GradleProof")
                .addService(this)
                .build()
                .start();
    }

    public void stopServer() {
        if (server != null) {
            server.shutdownNow();
        }
    }

    public String doClient(String name) {
        ManagedChannel channel = InProcessChannelBuilder
                .forName("GradleProof")
                .build();
        try {
            GreeterGrpc.GreeterBlockingStub stub = GreeterGrpc.newBlockingStub(channel);
            HelloRequest request = HelloRequest.newBuilder().setName(name).build();
            HelloResponse response = stub.sayHello(request);
            return response.getMessage();
        } finally {
            channel.shutdownNow();
        }
    }

    @Override
    public Single<HelloResponse> sayHello(Single<HelloRequest> request) {
        return request
                .map(HelloRequest::getName)
                .map(name -> "Hello " + name)
                .map(message -> HelloResponse.newBuilder().setMessage(message).build());
    }
}
