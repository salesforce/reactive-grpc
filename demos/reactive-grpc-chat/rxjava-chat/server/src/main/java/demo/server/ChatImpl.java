package demo.server;

import com.google.protobuf.Empty;
import com.salesforce.grpc.contrib.spring.GrpcService;
import demo.proto.ChatProto;
import demo.proto.RxChatGrpc;
import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import io.reactivex.Single;
import io.reactivex.subjects.PublishSubject;
import io.reactivex.subjects.Subject;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Demonstrates building a gRPC streaming server using RxJava and Reactive-Grpc.
 */
@GrpcService
public class ChatImpl extends RxChatGrpc.ChatImplBase {
    private final Logger logger = LoggerFactory.getLogger(ChatImpl.class);

    /**
     * PublishSubject used for pub-sub bus
     */
    private final Subject<ChatProto.ChatMessage> broadcast = PublishSubject.create();




    /**
     * Accept and broadcast ChatMessages.
     */
    @Override
    public Single<Empty> postMessage(Single<ChatProto.ChatMessage> request) {
        return request
                // Side effect: publish message
                .doOnSuccess(broadcast::onNext)
                // Side effect: log message
                .doOnSuccess(message -> logger.info(message.getAuthor() + ": " + message.getMessage()))
                // Return empty
                .map(x -> Empty.getDefaultInstance());
    }




    /**
     * This service operation returns an infinite stream of ChatMessages.
     */
    @Override
    public Flowable<ChatProto.ChatMessage> getMessages(Single<Empty> request) {
        return broadcast.toFlowable(BackpressureStrategy.BUFFER);
    }
}
