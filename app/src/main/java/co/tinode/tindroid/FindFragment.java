package co.tinode.tindroid;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import java.io.IOException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ActionMode;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.selection.ItemDetailsLookup;
import androidx.recyclerview.selection.Selection;
import androidx.recyclerview.selection.SelectionTracker;
import androidx.recyclerview.selection.StorageStrategy;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import co.tinode.tindroid.media.VxCard;
import co.tinode.tinodesdk.ComTopic;
import co.tinode.tinodesdk.FndTopic;
import co.tinode.tinodesdk.NotConnectedException;
import co.tinode.tinodesdk.NotSynchronizedException;
import co.tinode.tinodesdk.PromisedReply;
import co.tinode.tinodesdk.model.ServerMessage;
import co.tinode.tinodesdk.model.Subscription;

public class FindFragment extends Fragment implements ActionMode.Callback {

    private static final String TAG = "FindFragment";

    private FndTopic<VxCard> mFndTopic;
    private FndListener mFndListener;

    private FindAdapter mAdapter = null;
    private SelectionTracker<String> mSelectionTracker = null;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mFndTopic = Cache.getTinode().getOrCreateFndTopic();
        mFndListener = new FndListener();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        setHasOptionsMenu(true);

        return inflater.inflate(R.layout.fragment_chats, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        final AppCompatActivity activity = (AppCompatActivity) getActivity();
        if (activity == null) {
            return;
        }

        final ActionBar bar = activity.getSupportActionBar();
        if (bar != null) {
            bar.setDisplayHomeAsUpEnabled(false);
            bar.setTitle(R.string.app_name);
        }
        activity.findViewById(R.id.startNewChat).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Intent intent = new Intent(activity, StartChatActivity.class);
                    // intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                    startActivity(intent);
                }
            });

        RecyclerView rv = activity.findViewById(R.id.chat_list);
        rv.setLayoutManager(new LinearLayoutManager(activity));
        rv.setHasFixedSize(true);
        rv.addItemDecoration(new DividerItemDecoration(activity, DividerItemDecoration.VERTICAL));
        mAdapter = new FindAdapter(new FindAdapter.ClickListener() {
            @Override
            public void onCLick(final String topicName) {
                Intent intent = new Intent(activity, MessageActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                intent.putExtra("topic", topicName);
                activity.startActivity(intent);
            }
        });

        mAdapter.resetContent(activity, false);
        rv.setAdapter(mAdapter);

        mSelectionTracker = new SelectionTracker.Builder<>(
                "find-selection",
                rv,
                new FindAdapter.ContactItemKeyProvider(mAdapter),
                new ContactDetailsLookup(rv),
                StorageStrategy.createStringStorage())
                .build();

        mSelectionTracker.onRestoreInstanceState(savedInstanceState);

        mAdapter.setSelectionTracker(mSelectionTracker);
        mSelectionTracker.addObserver(new SelectionTracker.SelectionObserver() {
            @Override
            public void onSelectionChanged() {
                super.onSelectionChanged();
                // Do something here.
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();

        final Activity activity = getActivity();
        if (activity == null) {
            return;
        }

        try {
            Cache.attachFndTopic(mFndListener)
                    .thenApply(new PromisedReply.SuccessListener<ServerMessage>() {
                        @Override
                        public PromisedReply<ServerMessage> onSuccess(ServerMessage result) {
                            return null;
                        }
                    }, new PromisedReply.FailureListener<ServerMessage>() {
                        @Override
                        public PromisedReply<ServerMessage> onFailure(Exception err) {
                            Log.w(TAG, "Error subscribing to 'fnd' topic", err);
                            return null;
                        }
                    });
        } catch (NotSynchronizedException ignored) {
        } catch (NotConnectedException ignored) {
            /* offline - ignored */
            Toast.makeText(activity, R.string.no_connection, Toast.LENGTH_SHORT).show();
        } catch (Exception err) {
            Log.i(TAG, "Subscription failed", err);
            Toast.makeText(activity, R.string.failed_to_attach, Toast.LENGTH_LONG).show();
        }


        mAdapter.resetContent(activity, true);
        if (mAdapter.getItemCount() > 0) {
            activity.findViewById(R.id.chat_list).setVisibility(View.VISIBLE);
            activity.findViewById(android.R.id.empty).setVisibility(View.GONE);
        } else {
            activity.findViewById(R.id.chat_list).setVisibility(View.GONE);
            activity.findViewById(android.R.id.empty).setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        if (mFndTopic != null) {
            mFndTopic.setListener(null);
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        mSelectionTracker.onSaveInstanceState(outState);
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        Log.e(TAG, "onCreateOptionsMenu");
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
                //noinspection ConstantConditions
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

    @Override
    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
        mode.getMenuInflater().inflate(R.menu.menu_contacts_selected, menu);
        return true;
    }

    @Override
    public void onDestroyActionMode(ActionMode mode) {
        mSelectionTracker.clearSelection();
    }

    @Override
    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
        menu.setGroupVisible(R.id.single_selection, mSelectionTracker.getSelection().size() <= 1);
        return true;
    }

    /**
     * This menu is shown when one or more items are selected from the list
     */
    //@Override
    @SuppressWarnings("unchecked")
    public boolean onActionItemClicked(final ActionMode mode, final MenuItem item) {
        final ContactsActivity activity = (ContactsActivity) getActivity();
        if (activity == null) {
            return true;
        }

        final Selection<String> selection = mSelectionTracker.getSelection();
        // TODO: implement menu actions
        switch (item.getItemId()) {
            case R.id.action_delete:
                String[] topicNames = new String[selection.size()];
                int i = 0;
                for (String name : selection) {
                    topicNames[i++] = name;
                }
                showDeleteTopicsConfirmationDialog(topicNames);
                // Close CAB
                mode.finish();
                return true;

            case R.id.action_mute:
            case R.id.action_archive:
                // Archiving and muting is possible regardless of subscription status.

                final ComTopic<VxCard> topic =
                        (ComTopic<VxCard>) Cache.getTinode().getTopic(selection.iterator().next());
                (item.getItemId() == R.id.action_mute ?
                        topic.updateMuted(!topic.isMuted()) :
                        topic.updateArchived(!topic.isArchived())
                ).thenApply(new PromisedReply.SuccessListener<ServerMessage>() {
                    @Override
                    public PromisedReply<ServerMessage> onSuccess(ServerMessage result) {
                        datasetChanged();
                        return null;
                    }
                }).thenCatch(new PromisedReply.FailureListener<ServerMessage>() {
                    @Override
                    public PromisedReply<ServerMessage> onFailure(final Exception err) {
                        activity.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(activity, R.string.action_failed, Toast.LENGTH_SHORT).show();
                                Log.w(TAG, "Archiving failed", err);
                            }
                        });
                        return null;
                    }
                });
                mode.finish();
                return true;

            default:
                Log.e(TAG, "Unknown menu action");
                return false;
        }
    }

    // Confirmation dialog "Do you really want to do X?"
    private void showDeleteTopicsConfirmationDialog(final String[] topicNames) {
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
                        PromisedReply<ServerMessage> reply = null;
                        for (String name : topicNames) {
                            @SuppressWarnings("unchecked")
                            ComTopic<VxCard> t = (ComTopic<VxCard>) Cache.getTinode().getTopic(name);
                            try {
                                reply = t.delete().thenCatch(new PromisedReply.FailureListener<ServerMessage>() {
                                    @Override
                                    public PromisedReply<ServerMessage> onFailure(final Exception err) {
                                        activity.runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                Toast.makeText(activity, R.string.action_failed, Toast.LENGTH_SHORT).show();
                                                Log.w(TAG, "Delete failed", err);
                                            }
                                        });
                                        return null;
                                    }
                                });
                            } catch (NotConnectedException ignored) {
                                Toast.makeText(activity, R.string.no_connection, Toast.LENGTH_SHORT).show();
                            } catch (Exception err) {
                                Toast.makeText(activity, R.string.action_failed, Toast.LENGTH_SHORT).show();
                                Log.w(TAG, "Delete failed", err);
                            }
                        }
                        // Wait for the last reply to resolve then update dataset.
                        if (reply != null) {
                            reply.thenApply(new PromisedReply.SuccessListener<ServerMessage>() {
                                @Override
                                public PromisedReply<ServerMessage> onSuccess(ServerMessage result) {
                                    datasetChanged();
                                    return null;
                                }
                            });
                        }
                    }
                });
        confirmBuilder.show();
    }

    private void datasetChanged() {
        mAdapter.resetContent(getActivity(), true);
    }

    // TODO: Add onBackPressed handing to parent Activity.
    public boolean onBackPressed() {
        if (mSelectionTracker.hasSelection()) {
            mSelectionTracker.clearSelection();
        } else {
            return true;
        }
        return false;
    }

    private static class ContactDetailsLookup extends ItemDetailsLookup<String> {
        RecyclerView mRecyclerView;

        ContactDetailsLookup(RecyclerView recyclerView) {
            this.mRecyclerView = recyclerView;
        }

        @Nullable
        @Override
        public ItemDetails<String> getItemDetails(@NonNull MotionEvent motionEvent) {
            View view = mRecyclerView.findChildViewUnder(motionEvent.getX(), motionEvent.getY());
            if (view != null) {
                RecyclerView.ViewHolder viewHolder = mRecyclerView.getChildViewHolder(view);
                if (viewHolder instanceof FindAdapter.ViewHolder) {
                    return ((FindAdapter.ViewHolder) viewHolder).getItemDetails(motionEvent);
                }
            }
            return null;
        }
    }

    private class FndListener extends FndTopic.FndListener<VxCard> {
        @Override
        public void onMetaSub(final Subscription<VxCard,String[]> sub) {
            if (sub.pub != null) {
                sub.pub.constructBitmap();
            }
        }

        @Override
        public void onSubsUpdated() {
            datasetChanged();
        }
    }
}
