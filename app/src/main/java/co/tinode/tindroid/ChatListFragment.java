package co.tinode.tindroid;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
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

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.ListFragment;
import co.tinode.tindroid.media.VxCard;
import co.tinode.tinodesdk.ComTopic;
import co.tinode.tinodesdk.NotConnectedException;
import co.tinode.tinodesdk.PromisedReply;
import co.tinode.tinodesdk.Topic;
import co.tinode.tinodesdk.model.ServerMessage;

/**
 * View with contacts.
 */
public class ChatListFragment extends ListFragment implements AbsListView.MultiChoiceModeListener {

    private static final String TAG = "ChatListFragment";
    private ChatListAdapter mAdapter = null;
    private Boolean mIsArchive;

    public ChatListFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        Bundle args = getArguments();
        if (args != null) {
            mIsArchive = args.getBoolean("archive", false);
            Log.d(TAG, "onCreate, args NOT null, mIsArchive=" + mIsArchive);
        } else {
            mIsArchive = false;
            Log.d(TAG, "onCreate, args null, mIsArchive=" + mIsArchive);
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(mIsArchive ? R.layout.fragment_archive : R.layout.fragment_chat_list,
                container, false);
    }

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        final AppCompatActivity activity = (AppCompatActivity) getActivity();
        if (activity == null) {
            return;
        }

        mAdapter = new ChatListAdapter(activity, mIsArchive);
        setListAdapter(mAdapter);

        if (mIsArchive) {
            final Toolbar toolbar = view.findViewById(R.id.toolbar);
            activity.setSupportActionBar(toolbar);

            final ActionBar bar = activity.getSupportActionBar();
            if (bar != null) {
                bar.setDisplayHomeAsUpEnabled(true);
            }

            toolbar.setTitle(R.string.archived_chats);
            toolbar.setNavigationOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    FragmentManager fm = getFragmentManager();
                    if (fm != null) {
                        fm.popBackStack();
                    }
                }
            });
        }

        ListView lv = getListView();
        lv.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE_MODAL);

        lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String topic = mAdapter.getTopicNameFromView(view);
                Intent intent = new Intent(activity, MessageActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                intent.putExtra("topic", topic);
                startActivity(intent);
            }
        });

        lv.setMultiChoiceModeListener(this);
    }


    @Override
    public void onResume() {
        super.onResume();

        Bundle bundle = getArguments();
        if (bundle != null) {
            mIsArchive = bundle.getBoolean("archive", false);
        } else {
            mIsArchive = false;
        }

        mAdapter.resetContent(mIsArchive);
        mAdapter.notifyDataSetChanged();
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        // Inflate the menu; this adds items to the action bar if it is present.
        // super.onCreateOptionsMenu(menu, inflater);
        menu.clear();
        inflater.inflate(R.menu.menu_chat_list, menu);
    }

    @Override
    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
        mode.getMenuInflater().inflate(R.menu.menu_contacts_selected, menu);
        return true;
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

            case R.id.action_show_archive:
                activity.showFragment(ContactsActivity.FRAGMENT_ARCHIVE);
                return true;

            case R.id.action_settings:
                activity.showFragment(ContactsActivity.FRAGMENT_EDIT_ACCOUNT);
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
    @SuppressWarnings("unchecked")
    public boolean onActionItemClicked(final ActionMode mode, final MenuItem item) {
        final ContactsActivity activity = (ContactsActivity) getActivity();
        if (activity == null) {
            return true;
        }

        final SparseBooleanArray selected = mAdapter.getSelectedIds();
        final ComTopic<VxCard> topic;
        // TODO: implement menu actions
        switch (item.getItemId()) {
            case R.id.action_delete:
                int[] positions = new int[selected.size()];
                for (int i = 0; i < selected.size(); i++) {
                    positions[i] = selected.keyAt(i);
                }
                showDeleteTopicsConfirmationDialog(positions);
                // Close CAB
                mode.finish();
                return true;

            case R.id.action_mute:
            case R.id.action_archive:
                topic = (ComTopic<VxCard>) mAdapter.getItem(selected.keyAt(0));
                topic.subscribe(null, null)
                        .thenApply(new PromisedReply.SuccessListener<ServerMessage>() {
                            @Override
                            public PromisedReply<ServerMessage> onSuccess(ServerMessage result) {
                                return item.getItemId() == R.id.action_mute ?
                                        topic.updateMuted(!topic.isMuted()) :
                                        topic.updateArchived(!topic.isArchived());
                            }
                        }).thenApply(new PromisedReply.SuccessListener<ServerMessage>() {
                    @Override
                    public PromisedReply<ServerMessage> onSuccess(ServerMessage result) {
                        datasetChanged();
                        return topic.leave();
                    }
                }).thenCatch(new PromisedReply.FailureListener<ServerMessage>() {
                    @Override
                    public PromisedReply<ServerMessage> onFailure(Exception err) {
                        Log.d(TAG, "Archive item failed", err);
                        return null;
                    }
                });
                mode.finish();
                return true;

            default:
                Log.d(TAG, "unknown menu action");
                return false;
        }
    }

    @Override
    public void onDestroyActionMode(ActionMode mode) {
        mAdapter.removeSelection();
    }

    @Override
    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
        menu.setGroupVisible(R.id.single_selection, mAdapter.getSelectedCount() <= 1);
        return true;
    }

    @Override
    public void onItemCheckedStateChanged(ActionMode mode, int position, long id, boolean checked) {
        boolean isMultipleBefore = mAdapter.getSelectedCount() > 1;
        mAdapter.toggleSelected(position);
        int count = mAdapter.getSelectedCount();
        boolean isMultipleAfter = count > 1;
        mode.setTitle("" + count);
        if (isMultipleAfter != isMultipleBefore) {
            mode.invalidate();
        }
    }

    // Confirmation dialog "Do you really want to do X?"
    private void showDeleteTopicsConfirmationDialog(final int[] positions) {
        final ContactsActivity activity = (ContactsActivity) getActivity();
        if (activity == null) {
            return;
        }

        final AlertDialog.Builder confirmBuilder = new AlertDialog.Builder(activity);
        confirmBuilder.setNegativeButton(android.R.string.cancel, null);
        confirmBuilder.setMessage(R.string.confirm_delete_multiple_topics);
        confirmBuilder.setPositiveButton(android.R.string.ok,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        try {
                            for (int pos : positions) {
                                ComTopic<VxCard> t = (ComTopic<VxCard>) mAdapter.getItem(pos);
                                t.delete().thenCatch(new PromisedReply.FailureListener<ServerMessage>() {
                                    @Override
                                    public PromisedReply<ServerMessage> onFailure(Exception err) {
                                        // TODO: show error message to user.
                                        return null;
                                    }
                                });
                            }
                        } catch (NotConnectedException ignored) {
                            Toast.makeText(activity, R.string.no_connection, Toast.LENGTH_SHORT).show();
                        } catch (Exception ignored) {
                            Toast.makeText(activity, R.string.action_failed, Toast.LENGTH_SHORT).show();
                        }
                    }
                });
        confirmBuilder.show();
    }

    void datasetChanged() {
        mAdapter.resetContent(mIsArchive);

        final ContactsActivity activity = (ContactsActivity) getActivity();
        if (activity == null) {
            return;
        }

        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mAdapter.notifyDataSetChanged();
            }
        });
    }
}
