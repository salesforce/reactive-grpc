package demo.hello.grpc;

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

        HelloRequest request = HelloRequest.newBuilder().setName("World").build();

        /*
         * Create a request callback observer.
         */
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



        /*
         * Call an ssync UNARY operation
         * Callbacks defined outside of service invocation :(
         */
        stub.greet(request, responseObserver);



        /*
         * Call an async STREAMING RESPONSE operation
         * Callbacks defined outside of service invocation :(
         */
        stub.multiGreet(request, responseObserver);



        /*
         * Call an async BI-DIRECTIONAL STREAMING operation
         * Callbacks defined outside of service invocation :(
         */
        StreamObserver<HelloRequest> requestObserver = stub.streamGreet(responseObserver);
        // Notice how the programming model completely changes
        requestObserver.onNext(HelloRequest.newBuilder().setName("Alpha").build());
        requestObserver.onNext(HelloRequest.newBuilder().setName("Beta").build());
        requestObserver.onNext(HelloRequest.newBuilder().setName("Gamma").build());
        requestObserver.onCompleted();

        Thread.sleep(Duration.ofSeconds(1).toMillis());
    }
}
