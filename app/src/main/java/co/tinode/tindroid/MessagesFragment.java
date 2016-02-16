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

        ListView listViewMessages = (ListView) getActivity()
                .findViewById(R.id.messages_container);
        listViewMessages.setAdapter(new MessagesListAdapter(getActivity(),
                ((MessageActivity)getActivity()).getTopicName()));
    }
}
