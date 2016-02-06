package co.tinode.tindroid;

import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.Toast;

import java.util.ArrayList;

/**
 * Created by gsokolov on 2/3/16.
 */
public class ContactsFragment extends Fragment {

    private ListView mContactList;

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


        final ArrayList<Contact> dummyContacts = new ArrayList<Contact>();

        Contact c = new Contact();
        c.topic = "AAA";
        c.online = true;
        c.seq = 8;
        c.recv = 7;
        c.read = 7;
        c.pub = new VCard();
        c.pub.fn = "Alice Johnson";
        c.priv = "none";
        dummyContacts.add(c);

        c = new Contact();
        c.topic = "BBB";
        c.seq = 8;
        c.recv = 7;
        c.read = 5;
        c.pub = new VCard();
        c.pub.fn = "Bob Smith";
        c.priv = "waka waka";
        dummyContacts.add(c);

        c = new Contact();
        c.topic = "CCC";
        c.seq = 8;
        c.recv = 3;
        c.read = 3;
        c.pub = new VCard();
        c.pub.fn = "Carol Xmas";
        c.priv = "ooga chaka";
        dummyContacts.add(c);

        c = new Contact();
        c.topic = "DDD";
        c.seq = 16;
        c.recv = 5;
        c.read = 5;
        c.pub = new VCard();
        c.pub.fn = "Dave Goliaphsson";
        c.priv = null;
        dummyContacts.add(c);

        c = new Contact();
        c.topic = "EEE";
        c.seq = 8;
        c.recv = 8;
        c.read = 8;
        c.pub = new VCard();
        c.pub.fn = "Eve Adams";
        c.priv = "apple!";
        dummyContacts.add(c);

        c = new Contact();
        c.topic = "FFF";
        c.seq = 8;
        c.pub = new VCard();
        c.pub.fn = "Frank Singer";
        c.priv = "rain";
        dummyContacts.add(c);

        mContactList = (ListView) getActivity().findViewById(R.id.contactsView);
        mContactList.setAdapter(new ContactsListAdapter(getActivity(), dummyContacts));
        mContactList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Contact c = dummyContacts.get(position);
                Toast.makeText(getActivity().getApplicationContext(),
                        c.topic, Toast.LENGTH_LONG).show();

                Intent intent = new Intent(getActivity(), MessageActivity.class);
                startActivity(intent);

            }
        });

        FloatingActionButton fab = (FloatingActionButton) getActivity().findViewById(R.id.addTopic);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });
    }
}
