package co.tinode.tindroid;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;

import java.io.IOException;
import java.util.ArrayList;

import co.tinode.tinodesdk.PromisedReply;
import co.tinode.tinodesdk.Topic;
import co.tinode.tinodesdk.model.ServerMessage;

/**
 * Created by gene on 06/02/16.
 */
public class MessagesFragment extends Fragment {

    public MessagesFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        return inflater.inflate(R.layout.fragment_messages, container, false);
    }

    @Override
    public void onActivityCreated(Bundle savedInstance) {
        super.onActivityCreated(savedInstance);

        // Send message on button click
        getActivity().findViewById(R.id.chatSendButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendMessage();
            }
        });

        // Send message on Enter
        ((EditText) getActivity().findViewById(R.id.editMessage)).setOnEditorActionListener(
                new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                sendMessage();
                return true;
            }
        });
    }

    void sendMessage() {
        @SuppressWarnings("unchecked")
        Topic<?,?,String> topic = InmemoryCache.getTinode()
                .getTopic(((MessageActivity)getActivity()).getTopicName());
        String message = ((TextView) getActivity().findViewById(R.id.editMessage)).getText().toString().trim();
        if (!message.equals("")) {
            try {
                topic.publish(message).thenApply(new PromisedReply.SuccessListener<ServerMessage>() {
                    @Override
                    public PromisedReply<ServerMessage> onSuccess(ServerMessage result) throws Exception {
                        getActivity().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                // Clear text from the input field
                                ((TextView) getActivity().findViewById(R.id.editMessage)).setText("");
                            }
                        });
                        return null;
                    }
                }, null);
            } catch (Exception unused) {
                // TODO(gene): tell user that the message was not sent or save it for future delivery.
            }
        }
    }
}
