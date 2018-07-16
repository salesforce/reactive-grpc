package demo.client.console;

import com.google.protobuf.Empty;
import demo.proto.ChatProto;
import demo.proto.RxChatGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import jline.console.ConsoleReader;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static demo.client.console.ConsoleUtil.*;

/**
 * Demonstrates building a gRPC streaming client using RxJava and Reactive-Grpc.
 */
public final class ChatClient {
    private static final int PORT = 9999;
    private static final CompositeDisposable disposables = new CompositeDisposable();

    private ChatClient() { }

    public static void main(String[] args) throws Exception {
        String author = args.length == 0 ? "Random_Stranger" : args[0];

        // Connect to the sever
        ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", PORT).usePlaintext().build();
        RxChatGrpc.RxChatStub stub = RxChatGrpc.newRxStub(channel);

        CountDownLatch done = new CountDownLatch(1);
        ConsoleReader console = new ConsoleReader();
        console.println("Type /quit to exit");



        /* ******************************
         * Subscribe to incoming messages
         * ******************************/
        disposables.add(Single.just(Empty.getDefaultInstance())
                .as(stub::getMessages)
                .filter(message -> !message.getAuthor().equals(author))
                .subscribe(message -> printLine(console, message.getAuthor(), message.getMessage())));



        /* *************************
         * Publish outgoing messages
         * *************************/
        disposables.add(Observable
                // Send connection message
                .just(author + " joined.")
                // Send user input
                .concatWith(Observable.fromIterable(new ConsoleIterator(console, author + " > ")))
                // Send disconnect message
                .concatWith(Single.just(author + " left."))
                .map(msg -> toMessage(author, msg))
                .flatMapSingle(stub::postMessage)
                .subscribe(
                    ChatClient::doNothing,
                    throwable -> printLine(console, "ERROR", throwable.getMessage()),
                    done::countDown
                ));



        // Wait for a signal to exit, then clean up
        done.await();
        disposables.dispose();
        channel.shutdown();
        channel.awaitTermination(1, TimeUnit.SECONDS);
        console.getTerminal().restore();
    }

    private static ChatProto.ChatMessage toMessage(String author, String message) {
            return ChatProto.ChatMessage.newBuilder()
                .setAuthor(author)
                .setMessage(message)
                .build();
    }

    private static  <T> void doNothing(T ignore) {}
}
