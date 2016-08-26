package co.tinode.tindroid;

import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.Toast;

import java.util.ArrayList;

import co.tinode.tinodesdk.model.Subscription;

/**
 * View with contacts.
 */
public class ContactsFragment extends Fragment implements AbsListView.MultiChoiceModeListener {

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

        final ListView contactList = (ListView) getActivity().findViewById(R.id.contactsView);
        final ContactsListAdapter adapter = ((ContactsActivity) getActivity()).getContactsAdapter();
        contactList.setAdapter(adapter);
        contactList.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE_MODAL);

        contactList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Subscription s = ((ContactsActivity) getActivity()).getContactByPos(position);
                Intent intent = new Intent(getActivity(), MessageActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT|Intent.FLAG_ACTIVITY_SINGLE_TOP);
                intent.putExtra("topic", s.topic);
                startActivity(intent);
            }
        });

        contactList.setMultiChoiceModeListener(this);
    }

    @Override
    public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
        ContactsListAdapter adapter = ((ContactsActivity) getActivity()).getContactsAdapter();
        switch (item.getItemId()) {
            case R.id.action_delete:
                SparseBooleanArray selected = adapter.getSelectedIds();
                for (int i = 0; i < selected.size(); i++) {
                    if (selected.valueAt(i)) {
                        Log.d(TAG, "deleting item at " + selected.keyAt(i));
                    }
                }
                // Close CAB
                mode.finish();
                return true;
            case R.id.action_mute:
                Log.d(TAG, "muting item");
                mode.finish();
                return true;

            case R.id.action_edit:
                Log.d(TAG, "editing item");
                mode.finish();
                return true;

            default:
                return false;
        }
    }

    @Override
    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
        mode.getMenuInflater().inflate(R.menu.menu_contacts_selected, menu);
        return true;
    }

    @Override
    public void onDestroyActionMode(ActionMode mode) {
        ContactsListAdapter adapter = ((ContactsActivity) getActivity()).getContactsAdapter();
        adapter.removeSelection();
    }

    @Override
    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                return false;
            }

    @Override
    public void onItemCheckedStateChanged(ActionMode mode, int position, long id, boolean checked) {
        ContactsListAdapter adapter = ((ContactsActivity) getActivity()).getContactsAdapter();
        adapter.toggleSelected(position);
        mode.setTitle("" + adapter.getSelectedCount());
    }
}
