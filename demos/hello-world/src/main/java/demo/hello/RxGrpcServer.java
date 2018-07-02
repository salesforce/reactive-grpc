package demo.hello;

import demo.proto.HelloRequest;
import demo.proto.HelloResponse;
import demo.proto.RxGreeterGrpc;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.reactivex.Flowable;
import io.reactivex.Single;

public class RxGrpcServer extends RxGreeterGrpc.GreeterImplBase {
    public static void main(String[] args) throws Exception {
        Server server = ServerBuilder.forPort(8888).addService(new RxGrpcServer()).build().start();
        server.awaitTermination();
    }

    @Override
    public Single<HelloResponse> greet(Single<HelloRequest> request) {
        return request
                .map(HelloRequest::getName)
                .map(name -> "Hello " + name)
                .map(this::toResponse);
    }

    @Override
    public Flowable<HelloResponse> multiGreet(Single<HelloRequest> request) {
        return request
                .toFlowable()
                .map(HelloRequest::getName)
                .flatMap(
                        x -> Flowable.just("Welcome", "Hola", "Bonjour"),
                        (name, salutation) -> salutation + " " + name)
                .map(this::toResponse);
    }

    @Override
    public Flowable<HelloResponse> streamGreet(Flowable<HelloRequest> request) {
        return request
                .map(HelloRequest::getName)
                .map(name -> "Greetings " + name)
                .map(this::toResponse);
    }

    private HelloResponse toResponse(String greeting) {
        return HelloResponse.newBuilder().setMessage(greeting).build();
    }
}
