package co.tinode.tindroid;

import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.Toast;

import java.util.ArrayList;

import co.tinode.tinodesdk.model.Subscription;

/**
 * Created by gsokolov on 2/3/16.
 */
public class ContactsFragment extends Fragment {

    private static final String TAG = "ContactsFragment";

    public ContactsFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_contacts, container, false);
    }

    @Override
    public void onActivityCreated(Bundle savedInstance) {
        super.onActivityCreated(savedInstance);

        ListView contactList = (ListView) getActivity().findViewById(R.id.contactsView);
        contactList.setAdapter(((ContactsActivity) getActivity()).getContactsAdapter());
        contactList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Subscription s = ((ContactsActivity) getActivity()).getContactByPos(position);
                Intent intent = new Intent(getActivity(), MessageActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                intent.putExtra("topic", s.topic);
                Log.d(TAG, "adding extra '" + s.topic + "'");
                startActivity(intent);
            }
        });

        FloatingActionButton fab = (FloatingActionButton) getActivity().findViewById(R.id.addTopic);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "This will open a new topic dialog", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });
    }
}
