package demo.hello.rx;

import demo.proto.HelloRequest;
import demo.proto.HelloResponse;
import demo.proto.RxGreeterGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.reactivex.Flowable;
import io.reactivex.Single;

import java.time.Duration;

public class RxGrpcSyncClient {
    public static void main(String[] args) throws Exception {
        ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 8888).usePlaintext().build();
        RxGreeterGrpc.RxGreeterStub stub = RxGreeterGrpc.newRxStub(channel);

        /*
         * Create a service request
         */
        Single<HelloRequest> request = Single.just(HelloRequest.newBuilder().setName("OSCON").build());



        /*
         * Call an async UNARY operation
         */
        System.out.println(request
                .as(stub::greet)
                .map(HelloResponse::getMessage)
                .blockingGet());



        /*
         * Call an async STREAMING RESPONSE operation
         */
        request
                .as(stub::multiGreet)
                .map(HelloResponse::getMessage)
                .blockingSubscribe(System.out::println);



        /*
         * Call an async BI-DIRECTIONAL STREAMING operation
         */
        Flowable
                .just("Alpha", "Beta", "Gamma")
                .map(name -> HelloRequest.newBuilder().setName(name).build())
                .as(stub::streamGreet)
                .map(HelloResponse::getMessage)
                .blockingSubscribe(System.out::println);
    }
}
