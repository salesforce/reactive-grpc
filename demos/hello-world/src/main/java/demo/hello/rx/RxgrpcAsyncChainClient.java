package demo.hello.rx;

import demo.proto.HelloRequest;
import demo.proto.HelloResponse;
import demo.proto.RxGreeterGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.reactivex.Single;

import java.time.Duration;

public class RxgrpcAsyncChainClient {
    public static void main(String[] args) throws Exception {
        ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 8888).usePlaintext().build();
        RxGreeterGrpc.RxGreeterStub stub = RxGreeterGrpc.newRxStub(channel);

        Single.just("World")
                // Call UNARY service asynchronously
                .map(RxgrpcAsyncChainClient::request)
                .as(stub::greet)
                .map(HelloResponse::getMessage)

                // Call STREAMING RESPONSE service asynchronously
                .map(RxgrpcAsyncChainClient::request)
                .as(stub::multiGreet)
                .map(HelloResponse::getMessage)

                // Call BI-DIRECTIONAL STREAMING service asynchronously
                .map(RxgrpcAsyncChainClient::request)
                .as(stub::streamGreet)
                .map(HelloResponse::getMessage)

                // Final processing
                .subscribe(
                        System.out::println,
                        Throwable::printStackTrace);

        Thread.sleep(Duration.ofSeconds(1).toMillis());
    }

    private static HelloRequest request(String message) {
        return HelloRequest.newBuilder().setName(message).build();
    }
}
