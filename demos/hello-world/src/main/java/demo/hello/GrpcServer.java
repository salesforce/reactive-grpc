package demo.hello;

import demo.proto.GreeterGrpc;
import demo.proto.HelloRequest;
import demo.proto.HelloResponse;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;

public class GrpcServer extends GreeterGrpc.GreeterImplBase {
    public static void main(String[] args) throws Exception {
        Server server = ServerBuilder.forPort(8888).addService(new GrpcServer()).build().start();
        server.awaitTermination();
    }

    @Override
    public void greet(HelloRequest request, StreamObserver<HelloResponse> responseObserver) {
        String name = request.getName();
        responseObserver.onNext(HelloResponse.newBuilder().setMessage("Hello " + name).build());
        responseObserver.onCompleted();
    }

    @Override
    public void multiGreet(HelloRequest request, StreamObserver<HelloResponse> responseObserver) {
        String name = request.getName();
        responseObserver.onNext(HelloResponse.newBuilder().setMessage("Welcome " + name).build());
        responseObserver.onNext(HelloResponse.newBuilder().setMessage("Hola " + name).build());
        responseObserver.onNext(HelloResponse.newBuilder().setMessage("Bonjour " + name).build());
        responseObserver.onCompleted();
    }

    @Override
    public StreamObserver<HelloRequest> streamGreet(StreamObserver<HelloResponse> responseObserver) {
        return new StreamObserver<HelloRequest>() {
            @Override
            public void onNext(HelloRequest request) {
                String name = request.getName();
                responseObserver.onNext(HelloResponse.newBuilder().setMessage("Welcome " + name).build());
            }

            @Override
            public void onError(Throwable t) {
                responseObserver.onCompleted();
            }

            @Override
            public void onCompleted() {
                responseObserver.onCompleted();
            }
        };
    }
}
