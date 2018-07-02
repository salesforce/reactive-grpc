package demo.server;

import com.salesforce.grpc.contrib.spring.GrpcServerHost;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * Demonstrates building a gRPC streaming server using RxJava, Reactive-Grpc, grpc-spring, and Spring Boot.
 */
@SpringBootApplication
public class ChatServer {
    private final Logger logger = LoggerFactory.getLogger(ChatServer.class);

    public static void main(String[] args) throws Exception {
        SpringApplication.run(ChatServer.class, args);
        Thread.currentThread().join();
    }

    @Bean(initMethod = "start", destroyMethod = "close")
    public GrpcServerHost grpcServerHost(@Value("${port}") int port) {
        logger.info("Listening for gRPC on port " + port);
        GrpcServerHost host = new GrpcServerHost(port);
        return host;
    }
}
