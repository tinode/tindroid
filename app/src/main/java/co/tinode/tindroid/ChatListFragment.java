package co.tinode.tindroid;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.ListFragment;
import androidx.appcompat.app.AlertDialog;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;

/**
 * View with contacts.
 */
public class ChatListFragment extends ListFragment implements AbsListView.MultiChoiceModeListener {

    private static final String TAG = "ChatListFragment";
    private ChatListAdapter mAdapter = null;

    public ChatListFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_chat_list, container, false);
    }

    @Override
    public void onActivityCreated(Bundle savedInstance) {
        super.onActivityCreated(savedInstance);
        final Activity activity = getActivity();
        if (activity == null) {
            return;
        }

        mAdapter = ((ContactsActivity) activity).getChatListAdapter();

        setListAdapter(mAdapter);
        getListView().setChoiceMode(ListView.CHOICE_MODE_MULTIPLE_MODAL);

        getListView().setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String topic = mAdapter.getTopicNameFromView(view);
                Intent intent = new Intent(activity, MessageActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                intent.putExtra("topic", topic);
                startActivity(intent);
            }
        });

        getListView().setMultiChoiceModeListener(this);
    }

    @Override
    public void onResume() {
        super.onResume();

        mAdapter.resetContent();
        mAdapter.notifyDataSetChanged();
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        // Inflate the menu; this adds items to the action bar if it is present.
        // super.onCreateOptionsMenu(menu, inflater);
        menu.clear();
        inflater.inflate(R.menu.menu_chat_list, menu);
    }

    /**
     * This menu is shown when no items are selected
     */
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        final ContactsActivity activity = (ContactsActivity) getActivity();
        if (activity == null) {
            return true;
        }

        switch (item.getItemId()) {
            case R.id.action_new_p2p_topic:
                Log.d(TAG, "Start new p2p topic");
                activity.selectTab(ContactsFragment.TAB_CONTACTS);
                return true;

            case R.id.action_new_grp_topic:
                Log.d(TAG, "Launch new group topic");
                Intent intent = new Intent(activity, CreateGroupActivity.class);
                // intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(intent);
                return true;

            case R.id.action_add_by_id:
                AlertDialog.Builder builder = new AlertDialog.Builder(activity);
                builder
                        .setTitle(R.string.action_start_by_id)
                        .setView(R.layout.dialog_add_by_id)
                        .setCancelable(true)
                        .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                TextView editor = ((AlertDialog) dialog).findViewById(R.id.editId);
                                if (editor != null) {
                                    String id = editor.getText().toString();
                                    if (!TextUtils.isEmpty(id)) {
                                        Intent it = new Intent(activity, MessageActivity.class);
                                        it.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                                        it.putExtra("topic", id);
                                        startActivity(it);
                                    } else {
                                        Toast.makeText(activity, R.string.failed_empty_id,
                                                Toast.LENGTH_SHORT).show();
                                    }
                                }

                            }
                        })
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
                return true;

            case R.id.action_archive:
                Toast.makeText(activity, R.string.not_implemented, Toast.LENGTH_SHORT).show();
                return true;

            case R.id.action_settings:
                activity.showAccountInfoFragment();
                return true;

            case R.id.action_about:
                DialogFragment about = new AboutDialogFragment();
                // The warning below is a false positive. If activity is not null, then
                // getFragmentManager is also not null
                about.show(getFragmentManager(), "about");
                return true;

            case R.id.action_offline:
                try {
                    Cache.getTinode().reconnectNow();
                } catch (IOException ex) {
                    Log.d(TAG, "Reconnect failure", ex);
                    String cause = ex.getCause().getMessage();
                    Toast.makeText(activity, activity.getString(R.string.error_connection_failed) + cause,
                            Toast.LENGTH_SHORT).show();
                }
                break;
        }
        return false;
    }

    /**
     * This menu is shown when one or more items are selected from the list
     */
    @Override
    public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_delete:
                SparseBooleanArray selected = mAdapter.getSelectedIds();
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
                Log.d(TAG, "unknown menu action");
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
        mAdapter.removeSelection();
    }

    @Override
    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                return false;
            }

    @Override
    public void onItemCheckedStateChanged(ActionMode mode, int position, long id, boolean checked) {
        mAdapter.toggleSelected(position);
        mode.setTitle("" + mAdapter.getSelectedCount());
    }
}
