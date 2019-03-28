package co.tinode.tindroid;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ActionMode;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.DialogFragment;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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
import co.tinode.tinodesdk.NotConnectedException;
import co.tinode.tinodesdk.PromisedReply;
import co.tinode.tinodesdk.model.ServerMessage;

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

public class ChatsFragment extends Fragment implements ActionMode.Callback {

    private static final String TAG = "ChatsFragment";

    private Boolean mIsArchive;

    private ChatsAdapter mAdapter = null;
    private SelectionTracker<String> mSelectionTracker = null;
    private ActionMode mActionMode = null;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        Bundle args = getArguments();
        if (args != null) {
            mIsArchive = args.getBoolean("archive", false);
        } else {
            mIsArchive = false;
        }

        setHasOptionsMenu(true);

        return inflater.inflate(mIsArchive ? R.layout.fragment_archive : R.layout.fragment_chats,
                container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        final AppCompatActivity activity = (AppCompatActivity) getActivity();
        if (activity == null) {
            return;
        }

        final ActionBar bar = activity.getSupportActionBar();
        if (mIsArchive) {
            if (bar != null) {
                bar.setDisplayHomeAsUpEnabled(true);
                bar.setTitle(R.string.archived_chats);
                ((Toolbar) activity.findViewById(R.id.toolbar)).setNavigationOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        // activity.getSupportFragmentManager().popBackStack();
                        ((ChatsActivity)activity).showFragment(ChatsActivity.FRAGMENT_CHATLIST);
                    }
                });
            }
        } else {
            if (bar != null) {
                bar.setDisplayHomeAsUpEnabled(false);
                bar.setTitle(R.string.app_name);
            }
            view.findViewById(R.id.startNewChat).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        Intent intent = new Intent(activity, StartChatActivity.class);
                        // intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                        startActivity(intent);
                    }
                });
        }

        RecyclerView rv = view.findViewById(R.id.chat_list);
        rv.setLayoutManager(new LinearLayoutManager(activity));
        rv.setHasFixedSize(true);
        rv.addItemDecoration(new DividerItemDecoration(activity, DividerItemDecoration.VERTICAL));
        mAdapter = new ChatsAdapter(activity, new ChatsAdapter.ClickListener() {
            @Override
            public void onCLick(final String topicName) {
                if (mActionMode != null) {
                    return;
                }
                Intent intent = new Intent(activity, MessageActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                intent.putExtra("topic", topicName);
                activity.startActivity(intent);
            }
        });
        rv.setAdapter(mAdapter);

        mSelectionTracker = new SelectionTracker.Builder<>(
                "contacts-selection",
                rv,
                new ChatsAdapter.ContactItemKeyProvider(mAdapter),
                new ContactDetailsLookup(rv),
                StorageStrategy.createStringStorage())
                .build();

        mSelectionTracker.onRestoreInstanceState(savedInstanceState);

        mAdapter.setSelectionTracker(mSelectionTracker);
        mSelectionTracker.addObserver(new SelectionTracker.SelectionObserver() {
            @Override
            public void onSelectionChanged() {
                super.onSelectionChanged();
                if (mSelectionTracker.hasSelection() && mActionMode == null) {
                    mActionMode = activity.startSupportActionMode(ChatsFragment.this);
                } else if (!mSelectionTracker.hasSelection() && mActionMode != null) {
                    mActionMode.finish();
                    mActionMode = null;
                }
            }
        });

        mAdapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onChanged() {
                super.onChanged();

                if (mAdapter.getItemCount() > 0) {
                    activity.findViewById(R.id.chat_list).setVisibility(View.VISIBLE);
                    activity.findViewById(android.R.id.empty).setVisibility(View.GONE);
                } else {
                    activity.findViewById(R.id.chat_list).setVisibility(View.GONE);
                    activity.findViewById(android.R.id.empty).setVisibility(View.VISIBLE);
                }
            }
        });
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
        final Activity activity = getActivity();
        if (activity == null) {
            return;
        }

        mAdapter.resetContent(activity, mIsArchive, true);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        mSelectionTracker.onSaveInstanceState(outState);
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
        final ChatsActivity activity = (ChatsActivity) getActivity();
        if (activity == null) {
            return true;
        }

        switch (item.getItemId()) {
            case R.id.action_show_archive:
                activity.showFragment(ChatsActivity.FRAGMENT_ARCHIVE);
                return true;

            case R.id.action_settings:
                activity.showFragment(ChatsActivity.FRAGMENT_EDIT_ACCOUNT);
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
        final ChatsActivity activity = (ChatsActivity) getActivity();
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
        final ChatsActivity activity = (ChatsActivity) getActivity();
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

    /**
     * Wraps mAdapter.notifyDataSetChanged() into runOnUiThread()
     */
    void datasetChanged() {
        mAdapter.resetContent(getActivity(), mIsArchive, true);
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
                if (viewHolder instanceof ChatsAdapter.ViewHolder) {
                    return ((ChatsAdapter.ViewHolder) viewHolder).getItemDetails(motionEvent);
                }
            }
            return null;
        }
    }
}
