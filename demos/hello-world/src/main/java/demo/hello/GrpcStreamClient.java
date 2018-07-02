package demo.hello;

import demo.proto.GreeterGrpc;
import demo.proto.HelloRequest;
import demo.proto.HelloResponse;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;

import java.time.Duration;

public class GrpcStreamClient {
    public static void main(String[] args) throws Exception {
        ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 8888).usePlaintext().build();
        GreeterGrpc.GreeterStub stub = GreeterGrpc.newStub(channel);

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

        StreamObserver<HelloRequest> requestObserver = stub.streamGreet(responseObserver);

        requestObserver.onNext(HelloRequest.newBuilder().setName("Alpha").build());
        requestObserver.onNext(HelloRequest.newBuilder().setName("Beta").build());
        requestObserver.onNext(HelloRequest.newBuilder().setName("Gamma").build());
        requestObserver.onCompleted();

        Thread.sleep(Duration.ofSeconds(1).toMillis());
    }
}
