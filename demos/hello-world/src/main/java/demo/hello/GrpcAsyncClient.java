package demo.hello;

import demo.proto.GreeterGrpc;
import demo.proto.HelloRequest;
import demo.proto.HelloResponse;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;

import java.time.Duration;

public class GrpcAsyncClient {
    public static void main(String[] args) throws Exception {
        ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 8888).usePlaintext().build();
        GreeterGrpc.GreeterStub stub = GreeterGrpc.newStub(channel);

        HelloRequest request = HelloRequest.newBuilder().setName("OSCON").build();

        // Callbacks defined outside of service invocation :(
        StreamObserver<HelloResponse> responseObserver = new StreamObserver<HelloResponse>() {
            @Override
            public void onNext(HelloResponse response) {
                System.out.println(response.getMessage());
            }

            @Override
            public void onError(Throwable t) { }

            @Override
            public void onCompleted() { }
        };

        // Streaming unary and streaming services
        stub.greet(request, responseObserver);
        stub.multiGreet(request, responseObserver);

        Thread.sleep(Duration.ofSeconds(1).toMillis());
    }
}
