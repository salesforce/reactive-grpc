package demo.hello.grpc;

import demo.proto.GreeterGrpc;
import demo.proto.HelloRequest;
import demo.proto.HelloResponse;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;

import java.time.Duration;

public class GrpcAsyncChainClient {
    public static void main(String[] args) throws Exception {
        ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 8888).usePlaintext().build();
        GreeterGrpc.GreeterStub stub = GreeterGrpc.newStub(channel);

        // Call UNARY service asynchronously
        stub.greet(HelloRequest.newBuilder().setName("World").build(), new StreamObserver<HelloResponse>() {
            @Override
            public void onNext(HelloResponse value) {

                // Handle BI-DIRECTIONAL STREAMING response
                StreamObserver<HelloRequest> streamGreetObserver = stub.streamGreet(new StreamObserver<HelloResponse>() {
                    @Override
                    public void onNext(HelloResponse value) {
                        // Final processing
                        System.out.println(value.getMessage());
                    }

                    @Override
                    public void onError(Throwable t) {
                        t.printStackTrace();
                    }

                    @Override
                    public void onCompleted() { }
                });

                // Call STREAMING RESPONSE service asynchronously
                stub.multiGreet(requestFromResponse(value), new StreamObserver<HelloResponse>() {
                    @Override
                    public void onNext(HelloResponse value) {
                        // Call BI-DIRECTIONAL STREAMING service asynchronously
                        streamGreetObserver.onNext(requestFromResponse(value));
                    }

                    @Override
                    public void onError(Throwable t) {
                        t.printStackTrace();
                    }

                    @Override
                    public void onCompleted() {
                        streamGreetObserver.onCompleted();
                    }
                });
            }

            @Override
            public void onError(Throwable t) {
                t.printStackTrace();
            }

            @Override
            public void onCompleted() { }
        });

        Thread.sleep(Duration.ofSeconds(1).toMillis());
    }

    private static HelloRequest requestFromResponse(HelloResponse response) {
        String message = response.getMessage();
        return HelloRequest.newBuilder().setName(message).build();
    }
}
