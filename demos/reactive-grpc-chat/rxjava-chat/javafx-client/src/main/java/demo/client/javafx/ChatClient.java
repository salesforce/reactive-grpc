package demo.client.javafx;

import com.google.protobuf.Empty;
import demo.proto.ChatProto;
import demo.proto.RxChatGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.reactivex.Single;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.rxjavafx.observables.JavaFxObservable;
import io.reactivex.rxjavafx.schedulers.JavaFxScheduler;
import io.reactivex.rxjavafx.sources.WindowEventSource;
import io.reactivex.schedulers.Schedulers;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

import java.util.concurrent.TimeUnit;

public class ChatClient extends Application {
    private static final int PORT = 9999;

    private final TextArea messages = new TextArea();
    private final TextField message = new TextField();
    private final Button send = new Button();

    private String author;
    private ManagedChannel channel;
    private RxChatGrpc.RxChatStub stub;
    private CompositeDisposable disposables = new CompositeDisposable();

    public static void doMain(String[] args) {
        launch(args);
    }

    /**
     * Start the application and connect to gRPC
     */
    @Override
    public void init() {
        author = getParameters().getRaw().isEmpty() ? "Random_Stranger" : getParameters().getRaw().get(0);

        // Connect to the sever
        channel = ManagedChannelBuilder.forAddress("localhost", PORT).usePlaintext().build();
        stub = RxChatGrpc.newRxStub(channel);
    }

    /**
     * Build the UI and wire up event handlers
     */
    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Reactive Chat - " + author);
        Scene scene = buildScene();



        /* ******************************
         * Subscribe to incoming messages
         * ******************************/
        disposables.add(Single
                // Trigger
                .just(Empty.getDefaultInstance())
                .subscribeOn(Schedulers.io())
                // Invoke
                .as(stub::getMessages)
                .map(this::fromMessage)
                // Execute
                .observeOn(JavaFxScheduler.platform())
                .subscribe(messages::appendText));



        /* *************************
         * Publish outgoing messages
         * *************************/
        disposables.add(JavaFxObservable
                // Trigger
                .actionEventsOf(send)
                .subscribeOn(Schedulers.io())
                // Invoke
                .map(x -> message.getText())
                .map(this::toMessage)
                .flatMapSingle(stub::postMessage)
                // Execute
                .observeOn(JavaFxScheduler.platform())
                .subscribe(ignore -> message.clear()));



        /* *********************************
         * Publish arrival/departure message
         * *********************************/
        disposables.add(WindowEventSource
                // Trigger
                .fromWindowEvents(primaryStage, WindowEvent.ANY)
                .subscribeOn(Schedulers.io())
                .filter(event -> event.getEventType().equals(WindowEvent.WINDOW_SHOWING) |
                        event.getEventType().equals(WindowEvent.WINDOW_HIDING))
                // Invoke
                .map(event -> event.getEventType().equals(WindowEvent.WINDOW_SHOWING) ? "joined" : "left")
                .map(this::toMessage)
                .flatMapSingle(stub::postMessage)
                // Execute
                .subscribe());



        primaryStage.setScene(scene);
        primaryStage.show();
    }



    /**
     * Close down event listeners and disconnect from gRPC
     */
    @Override
    public void stop() throws Exception {
        disposables.dispose();
        channel.shutdown();
        channel.awaitTermination(1, TimeUnit.SECONDS);
    }

    private String fromMessage(ChatProto.ChatMessage message) {
        return message.getAuthor() + " > " + message.getMessage() + "\n";
    }

    private ChatProto.ChatMessage toMessage(String message) {
        return ChatProto.ChatMessage.newBuilder()
                .setAuthor(author)
                .setMessage(message)
                .build();
    }

    private Scene buildScene() {
        messages.setWrapText(true);
        messages.setEditable(false);
        VBox.setVgrow(messages, Priority.ALWAYS);

        HBox.setHgrow(message, Priority.ALWAYS);

        send.setText("Send");
        send.setDefaultButton(true);

        HBox hBox = new HBox();
        hBox.setSpacing(5);
        hBox.getChildren().addAll(message, send);

        VBox vBox = new VBox();
        vBox.setSpacing(5);
        vBox.setPadding(new Insets(10, 10, 10, 10));
        vBox.getChildren().addAll(messages, hBox);

        return new Scene(vBox, 300, 500);
    }
}
