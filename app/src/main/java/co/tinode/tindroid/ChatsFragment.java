package co.tinode.tindroid;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ActionMode;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProviders;

import android.app.Activity;
import android.content.Context;
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

import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.OvershootInterpolator;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;

public class ChatsFragment extends Fragment implements ActionMode.Callback {

    private static final String TAG = "ChatListFragment";

    private ChatsViewModel mViewModel;
    private boolean isFabMenuOpen = false;
    private Boolean mIsArchive;

    private ChatListAdapter mAdapter = null;
    private SelectionTracker<String> mSelectionTracker = null;
    private ActionMode mActionMode = null;

    public static ChatsFragment newInstance() {
        return new ChatsFragment();
    }

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

        View fragment = inflater.inflate(mIsArchive ? R.layout.fragment_archive : R.layout.fragment_chats,
                container, false);

        return fragment;
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        final AppCompatActivity activity = (AppCompatActivity) getActivity();
        if (activity == null) {
            return;
        }

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

        view.findViewById(R.id.startNewChat).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                handleFabMenu(!isFabMenuOpen);
            }
        });

        RecyclerView rv = activity.findViewById(R.id.chat_list);
        rv.setLayoutManager(new LinearLayoutManager(activity));
        rv.setHasFixedSize(true);
        rv.addItemDecoration(new DividerItemDecoration(activity, DividerItemDecoration.HORIZONTAL));
        mAdapter = new ChatListAdapter(activity, new ChatListAdapter.ContactClickListener() {
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

        mAdapter.resetContent(activity, mIsArchive, false);
        rv.setAdapter(mAdapter);

        mSelectionTracker = new SelectionTracker.Builder<>(
                "contacts-selection",
                rv,
                new ChatListAdapter.ContactItemKeyProvider(mAdapter),
                new ContactDetailsLookup(rv),
                StorageStrategy.createStringStorage())
                .build();
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
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mViewModel = ViewModelProviders.of(this).get(ChatsViewModel.class);
        // TODO: Use the ViewModel
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
        if (mAdapter.getItemCount() > 0) {
            activity.findViewById(R.id.chat_list).setVisibility(View.VISIBLE);
            activity.findViewById(android.R.id.empty).setVisibility(View.GONE);
        } else {
            activity.findViewById(R.id.chat_list).setVisibility(View.GONE);
            activity.findViewById(android.R.id.empty).setVisibility(View.VISIBLE);
        }
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
            case R.id.action_new_p2p_topic:
                // activity.selectTab(ContactsFragment.TAB_CONTACTS);
                return true;

            case R.id.action_new_grp_topic:
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

    /*
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
    */

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

    /**
     * Wraps mAdapter.notifyDataSetChanged() into runOnUiThread()
     */
    void datasetChanged() {
        mAdapter.resetContent(getActivity(), mIsArchive, true);
    }

    private void handleFabMenu(boolean open) {
        final Activity activity = getActivity();
        if (activity == null) {
            return;
        }

        if (open) {
            Animation animOpen = AnimationUtils.loadAnimation(activity, R.anim.fab_open);
            ViewCompat.animate(activity.findViewById(R.id.startNewChat))
                    .rotation(45.0F)
                    .withLayer()
                    .setDuration(200)
                    .setInterpolator(new OvershootInterpolator(10.0F))
                    .start();
            activity.findViewById(R.id.groupChatLayout).startAnimation(animOpen);
            activity.findViewById(R.id.p2pChatLayout).startAnimation(animOpen);
            isFabMenuOpen = true;
        } else {
            Animation animClose = AnimationUtils.loadAnimation(activity, R.anim.fab_close);
            ViewCompat.animate(activity.findViewById(R.id.startNewChat))
                    .rotation(0.0F)
                    .withLayer()
                    .setDuration(200)
                    .setInterpolator(new OvershootInterpolator(10.0F))
                    .start();
            activity.findViewById(R.id.groupChatLayout).startAnimation(animClose);
            activity.findViewById(R.id.p2pChatLayout).startAnimation(animClose);
            isFabMenuOpen = false;
        }
    }

    // TODO: Add onBackPressed handing to parent Activity.
    public boolean onBackPressed() {
        if (mSelectionTracker.hasSelection()) {
            mSelectionTracker.clearSelection();
        } else if (isFabMenuOpen) {
            handleFabMenu(false);
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
                if (viewHolder instanceof ChatListAdapter.ContactViewHolder) {
                    return ((ChatListAdapter.ContactViewHolder) viewHolder).getItemDetails(motionEvent);
                }
            }
            return null;
        }
    }
}
