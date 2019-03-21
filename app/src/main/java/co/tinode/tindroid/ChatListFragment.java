package co.tinode.tindroid;

import androidx.fragment.app.ListFragment;

/**
 * View with contacts.
 */
public class ChatListFragment extends ListFragment {

    private static final String TAG = "ChatListFragment";
    private ChatsAdapter mAdapter = null;
    private Boolean mIsArchive;

    public ChatListFragment() {
    }
/*
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        Bundle args = getArguments();
        if (args != null) {
            mIsArchive = args.getBoolean("archive", false);
        } else {
            mIsArchive = false;
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(mIsArchive ? R.layout.fragment_archive : R.layout.fragment_chat_list,
                container, false);
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

    // This menu is shown when no items are selected
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        final ContactsActivity activity = (ContactsActivity) getActivity();
        if (activity == null) {
            return true;
        }

        switch (item.getItemId()) {
            case R.id.action_new_p2p_topic:
                activity.selectTab(ContactsFragment.TAB_CONTACTS);
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

    // This menu is shown when one or more items are selected from the list
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
                // Archiving and muting is possible regardless of subscription status.
                topic = (ComTopic<VxCard>) mAdapter.getItem(selected.keyAt(0));
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

    */
}
