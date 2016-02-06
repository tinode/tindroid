package co.tinode.tindroid;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.widget.ListView;

import java.util.ArrayList;

/**
 * Created by gsokolov on 2/5/16.
 */
public class MessageActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_messages);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        final ArrayList<Message<String>> dummyMessages = new ArrayList<Message<String>>();
        Message<String> msg = new Message<String>();
        msg.content = "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor " +
                "incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis " +
                "nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat.";
        dummyMessages.add(msg);

        msg = new Message<String>();
        msg.content = "Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore " +
                "eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, " +
                "sunt in culpa qui officia deserunt mollit anim id est laborum.";
        dummyMessages.add(msg);

        msg = new Message<String>();
        msg.content = "OK!";
        dummyMessages.add(msg);

        ListView listViewMessages = (ListView) findViewById(R.id.messages_container);
        listViewMessages.setAdapter(new MessagesListAdapter(this, dummyMessages));
    }
}
