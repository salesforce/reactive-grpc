package demo.hello;

import demo.proto.GreeterGrpc;
import demo.proto.HelloRequest;
import demo.proto.HelloResponse;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

public class GrpcClient {
    public static void main(String[] args) {
        ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 8888).usePlaintext().build();
        GreeterGrpc.GreeterBlockingStub blockingStub = GreeterGrpc.newBlockingStub(channel);

        HelloRequest request = HelloRequest.newBuilder().setName("OSCON").build();

        // Blocking unary request
        HelloResponse response = blockingStub.greet(request);
        System.out.println(response.getMessage());

        // Blocking streaming request
        blockingStub.multiGreet(request).forEachRemaining(r -> {
            System.out.println(r.getMessage());
        });
    }
}
