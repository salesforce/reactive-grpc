package demo.client.android;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;

import com.google.protobuf.Empty;
import com.jakewharton.rxbinding2.view.RxView;

import java.util.ArrayList;
import java.util.List;

import demo.proto.ChatProto;
import demo.proto.RxChatGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

public class ChatActivity extends AppCompatActivity {
    private String author = "Android_Stranger";

    private ManagedChannel channel;
    private RxChatGrpc.RxChatStub stub;

    private ListView messages;
    private List<String> messageList = new ArrayList<>();
    private EditText message;
    private Button send;

    private Disposable sendSubscription;
    private Disposable receiveSubscription;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        messages = findViewById(R.id.messages);
        message = findViewById(R.id.message);
        send = findViewById(R.id.send);
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Connect to the sever
        channel = ManagedChannelBuilder.forAddress("10.0.2.2", 9999).usePlaintext().build();
        stub = RxChatGrpc.newRxStub(channel);

        // Subscribe to incoming messages
        receiveSubscription = Single
                .just(Empty.getDefaultInstance())
                .subscribeOn(Schedulers.io())
                .as(stub::getMessages)
                .map(this::fromMessage)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(message -> {
                    messageList.add(message);
                    messages.setAdapter(new ArrayAdapter<>(ChatActivity.this, R.layout.string_list_item, messageList.toArray(new String[]{})));
                });

        // Announce arrival
        stub.postMessage(toMessage("joined.")).subscribe();

        // Publish outgoing messages
        sendSubscription = RxView.clicks(send)
                .map(x -> message.getText().toString())
                .map(this::toMessage)
                .flatMapSingle(stub::postMessage)
                .subscribe(x -> message.setText(""));
    }

    @Override
    protected void onPause() {
        super.onPause();

        // Announce departure
        stub.postMessage(toMessage("left.")).subscribe();

        sendSubscription.dispose();
        receiveSubscription.dispose();

        // Disconnect from gRPC
        channel.shutdown();
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
}
