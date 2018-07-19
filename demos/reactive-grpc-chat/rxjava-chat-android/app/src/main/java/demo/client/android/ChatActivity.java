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
import io.reactivex.Scheduler;
import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

public class ChatActivity extends AppCompatActivity {
    private final String AUTHOR = "Android_Stranger";

    private ManagedChannel channel;
    private RxChatGrpc.RxChatStub stub;

    private ListView messages;
    private List<String> messageList = new ArrayList<>();
    private EditText message;
    private Button send;

    private CompositeDisposable disposables = new CompositeDisposable();

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



        /* ******************************
         * Subscribe to incoming messages
         * ******************************/
        disposables.add(Single
                // Trigger
                .just(Empty.getDefaultInstance())
                .observeOn(Schedulers.io())
                // Invoke
                .as(stub::getMessages)
                .map(this::fromMessage)
                // Execute
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(message -> {
                    messageList.add(message);
                    messages.setAdapter(new ArrayAdapter<>(ChatActivity.this, R.layout.string_list_item, messageList.toArray(new String[]{})));
                }));



        /* *************************
         * Publish outgoing messages
         * *************************/
        disposables.add(RxView
                // Trigger
                .clicks(send)
                // Invoke
                .observeOn(Schedulers.io())
                .map(x -> message.getText().toString())
                .map(this::toMessage)
                .flatMapSingle(stub::postMessage)
                // Execute
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(x -> message.setText("")));



        /* ***********************
         * Publish arrival message
         * ***********************/
        stub.postMessage(toMessage("joined.")).subscribe();
    }

    @Override
    protected void onPause() {
        super.onPause();



        /* ***********************
         * Publish arrival message
         * ***********************/
        stub.postMessage(toMessage("left.")).subscribe();



        // Close down event listeners and disconnect from gRPC
        disposables.dispose();
        channel.shutdown();
    }

    private String fromMessage(ChatProto.ChatMessage message) {
        return message.getAuthor() + " > " + message.getMessage() + "\n";
    }

    private ChatProto.ChatMessage toMessage(String message) {
        return ChatProto.ChatMessage.newBuilder()
                .setAuthor(AUTHOR)
                .setMessage(message)
                .build();
    }
}
