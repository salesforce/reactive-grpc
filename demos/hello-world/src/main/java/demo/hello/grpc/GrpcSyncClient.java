package demo.hello.grpc;

import demo.proto.GreeterGrpc;
import demo.proto.HelloRequest;
import demo.proto.HelloResponse;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

/**
 * This client demonstrates calling unary and streaming response operations with the gRPC blocking API.
 */
public class GrpcSyncClient {
    public static void main(String[] args) {
        ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 8888).usePlaintext().build();
        GreeterGrpc.GreeterBlockingStub blockingStub = GreeterGrpc.newBlockingStub(channel);


        /*
         * Create a service request
         */
        HelloRequest request = HelloRequest.newBuilder().setName("World").build();



        /*
         * Call a blocking UNARY operation
         */
        HelloResponse response = blockingStub.greet(request);
        System.out.println(response.getMessage());



        /*
         * Call a blocking STREAMING RESPONSE operation
         */
        blockingStub.multiGreet(request).forEachRemaining(r -> {
            System.out.println(r.getMessage());
        });
    }
}
