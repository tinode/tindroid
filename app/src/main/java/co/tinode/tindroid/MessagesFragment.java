package co.tinode.tindroid;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;

import java.util.ArrayList;

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

        msg = new Message<String>();
        msg.content = "Excepteur sint occaecat cupidatat non proident," +
                "sunt in culpa qui officia deserunt mollit anim id est laborum.";
        dummyMessages.add(msg);

        msg = new Message<String>();
        msg.content = "2. Excepteur sint occaecat cupidatat non proident," +
                "sunt in culpa qui officia deserunt mollit anim id est laborum.";
        dummyMessages.add(msg);

        msg = new Message<String>();
        msg.content = "3. Excepteur sint occaecat cupidatat non proident," +
                "sunt in culpa qui officia deserunt mollit anim id est laborum.";
        dummyMessages.add(msg);

        msg = new Message<String>();
        msg.content = "4. Excepteur sint occaecat cupidatat non proident," +
                "sunt in culpa qui officia deserunt mollit anim id est laborum.";
        dummyMessages.add(msg);

        msg = new Message<String>();
        msg.content = "Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore " +
                "eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, " +
                "sunt in culpa qui officia deserunt mollit anim id est laborum.";
        dummyMessages.add(msg);

        ListView listViewMessages = (ListView) getActivity()
                .findViewById(R.id.messages_container);
        listViewMessages.setAdapter(new MessagesListAdapter(getActivity(), dummyMessages));
    }
}
