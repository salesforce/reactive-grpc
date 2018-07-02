package demo.hello;

import demo.proto.HelloRequest;
import demo.proto.HelloResponse;
import demo.proto.RxGreeterGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.reactivex.Flowable;
import io.reactivex.Single;

import java.time.Duration;

public class RxGrpcStreamClient {
    public static void main(String[] args) throws Exception {
        ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 8888).usePlaintext().build();
        RxGreeterGrpc.RxGreeterStub stub = RxGreeterGrpc.newRxStub(channel);

        Flowable<String> request = Flowable.just("Alpha", "Beta", "Gamma");


        request.map(RxGrpcStreamClient::toRequest)
                .as(stub::streamGreet)
                .map(HelloResponse::getMessage)
                .subscribe(System.out::println);

        Thread.sleep(Duration.ofSeconds(1).toMillis());
    }

    private static HelloRequest toRequest(String name) {
        return HelloRequest.newBuilder().setName(name).build();
    }
}
