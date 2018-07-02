package demo.hello;

import demo.proto.HelloRequest;
import demo.proto.RxGreeterGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.reactivex.Single;

import java.time.Duration;

public class RxGrpcClient {
    public static void main(String[] args) throws Exception {
        ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 8888).usePlaintext().build();
        RxGreeterGrpc.RxGreeterStub stub = RxGreeterGrpc.newRxStub(channel);

        Single<HelloRequest> request = Single.just(HelloRequest.newBuilder().setName("OSCON").build());

        // Unary and streaming services work exactly the same :)
        request.as(stub::greet).subscribe(response -> System.out.println(response.getMessage()));
        request.as(stub::multiGreet).subscribe(response -> System.out.println(response.getMessage()));

        Thread.sleep(Duration.ofSeconds(1).toMillis());
    }
}
