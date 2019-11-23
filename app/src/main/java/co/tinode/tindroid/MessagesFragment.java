package co.tinode.tindroid;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import androidx.exifinterface.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.AsyncTaskLoader;
import androidx.loader.content.Loader;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;

import co.tinode.tindroid.db.BaseDb;
import co.tinode.tindroid.db.StoredTopic;
import co.tinode.tindroid.media.VxCard;
import co.tinode.tinodesdk.ComTopic;
import co.tinode.tinodesdk.LargeFileHelper;
import co.tinode.tinodesdk.NotConnectedException;
import co.tinode.tinodesdk.PromisedReply;
import co.tinode.tinodesdk.Storage;
import co.tinode.tinodesdk.Tinode;
import co.tinode.tinodesdk.Topic;
import co.tinode.tinodesdk.model.AccessChange;
import co.tinode.tinodesdk.model.Acs;
import co.tinode.tinodesdk.model.AcsHelper;
import co.tinode.tinodesdk.model.Drafty;
import co.tinode.tinodesdk.model.MetaSetSub;
import co.tinode.tinodesdk.model.MsgServerCtrl;
import co.tinode.tinodesdk.model.MsgSetMeta;
import co.tinode.tinodesdk.model.PrivateType;
import co.tinode.tinodesdk.model.ServerMessage;
import co.tinode.tinodesdk.model.Subscription;

import static android.app.Activity.RESULT_OK;

/**
 * Fragment handling message display and message sending.
 */
public class MessagesFragment extends Fragment
        implements LoaderManager.LoaderCallbacks<MessagesFragment.UploadResult> {
    private static final String TAG = "MessageFragment";

    static final String MESSAGE_TO_SEND = "messageText";

    private static final int MESSAGES_TO_LOAD = 24;

    private static final int ACTION_ATTACH_FILE = 100;
    private static final int ACTION_ATTACH_IMAGE = 101;

    private static final int READ_EXTERNAL_STORAGE_PERMISSION = 1;

    // Maximum size of file to send in-band. 256KB.
    private static final long MAX_INBAND_ATTACHMENT_SIZE = 1 << 17;
    // Maximum size of file to upload. 8MB.
    private static final long MAX_ATTACHMENT_SIZE = 1 << 23;

    private static final int READ_DELAY = 1000;
    private ComTopic<VxCard> mTopic;

    private LinearLayoutManager mMessageViewLayoutManager;
    private MessagesAdapter mMessagesAdapter;
    private SwipeRefreshLayout mRefresher;

    // It cannot be local.
    @SuppressWarnings("FieldCanBeLocal")
    private UploadProgress mUploadProgress;

    private String mTopicName = null;
    private Timer mNoteTimer = null;
    private String mMessageToSend = null;
    private boolean mChatInvitationShown = false;

    private PromisedReply.FailureListener<ServerMessage> mFailureListener;

    public MessagesFragment() {
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_messages, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstance) {

        final MessageActivity activity = (MessageActivity) getActivity();
        if (activity == null) {
            return;
        }

        mMessageViewLayoutManager = new LinearLayoutManager(activity) {
            @Override
            public void onLayoutChildren(RecyclerView.Recycler recycler, RecyclerView.State state) {
                // This is a hack for IndexOutOfBoundsException: Inconsistency detected. Invalid view holder adapter positionViewHolder
                // It happens when two uploads are started at the same time.
                // See discussion here:
                // https://stackoverflow.com/questions/31759171/recyclerview-and-java-lang-indexoutofboundsexception-inconsistency-detected-in
                try {
                    super.onLayoutChildren(recycler, state);
                } catch (IndexOutOfBoundsException e) {
                    Log.i(TAG, "meet a IOOBE in RecyclerView", e);
                }
            }
        };
        // mMessageViewLayoutManager.setStackFromEnd(true);
        mMessageViewLayoutManager.setReverseLayout(true);

        RecyclerView ml = view.findViewById(R.id.messages_container);
        ml.setLayoutManager(mMessageViewLayoutManager);

        // Creating a strong reference from this Fragment, otherwise it will be immediately garbage collected.
        mUploadProgress = new UploadProgress();
        // This needs to be rebound on activity creation.
        FileUploader.setProgressHandler(mUploadProgress);

        mRefresher = view.findViewById(R.id.swipe_refresher);
        mMessagesAdapter = new MessagesAdapter(activity, mRefresher);
        ml.setAdapter(mMessagesAdapter);
        mRefresher.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                if (!mMessagesAdapter.loadNextPage() && !StoredTopic.isAllDataLoaded(mTopic)) {
                    try {
                        mTopic.getMeta(mTopic.getMetaGetBuilder().withEarlierData(MESSAGES_TO_LOAD).build())
                                .thenApply(
                                        new PromisedReply.SuccessListener<ServerMessage>() {
                                            @Override
                                            public PromisedReply<ServerMessage> onSuccess(ServerMessage result) {
                                                mRefresher.setRefreshing(false);
                                                return null;
                                            }
                                        },
                                        new PromisedReply.FailureListener<ServerMessage>() {
                                            @Override
                                            public PromisedReply<ServerMessage> onFailure(Exception err) {
                                                mRefresher.setRefreshing(false);
                                                return null;
                                            }
                                        }
                                );
                    } catch (Exception e) {
                        mRefresher.setRefreshing(false);
                    }
                } else {
                    mRefresher.setRefreshing(false);
                }
            }
        });

        mFailureListener = new UiUtils.ToastFailureListener(getActivity());

        // Send message on button click
        view.findViewById(R.id.chatSendButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendText();
            }
        });

        // Send image button
        view.findViewById(R.id.attachImage).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openFileSelector("image/*", R.string.select_image, ACTION_ATTACH_IMAGE);
            }
        });

        // Send file button
        view.findViewById(R.id.attachFile).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openFileSelector("*/*", R.string.select_file, ACTION_ATTACH_FILE);
            }
        });

        EditText editor = view.findViewById(R.id.editMessage);
        // Send message on Enter
        editor.setOnEditorActionListener(
                new TextView.OnEditorActionListener() {
                    @Override
                    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                        sendText();
                        return true;
                    }
                });

        // Send notification on key presses
        editor.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int start, int before, int count) {
                if (count > 0 || before > 0) {
                    activity.sendKeyPress();
                }
            }

            @Override
            public void afterTextChanged(Editable editable) {
            }
        });

        view.findViewById(R.id.enablePeerButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Enable peer.
                Acs am = new Acs(mTopic.getAccessMode());
                am.update(new AccessChange(null, "+RW"));
                try {
                    mTopic.setMeta(new MsgSetMeta<VxCard, PrivateType>(new MetaSetSub(mTopic.getName(), am.getGiven())))
                            .thenCatch(new UiUtils.ToastFailureListener(activity));
                } catch (NotConnectedException ignored) {
                    Toast.makeText(activity, R.string.no_connection, Toast.LENGTH_SHORT).show();
                } catch (Exception err) {
                    Log.w(TAG,"Failed to enable peer", err);
                    Toast.makeText(activity, R.string.action_failed, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    @Override
    @SuppressWarnings("unchecked")
    public void onResume() {
        super.onResume();

        setHasOptionsMenu(true);

        Bundle args = getArguments();
        if (args != null) {
            mTopicName = args.getString("topic");
            mMessageToSend = args.getString(MESSAGE_TO_SEND);
        } else {
            mTopicName = null;
        }

        if (mTopicName != null) {
            mTopic = (ComTopic<VxCard>) Cache.getTinode().getTopic(mTopicName);
            runMessagesLoader(mTopicName);
        } else {
            mTopic = null;
        }

        // Check periodically if all messages were read;
        mNoteTimer = new Timer();
        mNoteTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                sendReadNotification();
            }
        }, READ_DELAY, READ_DELAY);

        mRefresher.setRefreshing(false);

        updateFormValues();
    }

    void runMessagesLoader(String topicName) {
        mMessagesAdapter.resetContent(topicName);
    }

    private void updateFormValues() {
        final MessageActivity activity = (MessageActivity) getActivity();
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            return;
        }

        if (mTopic == null) {
            // Default view when the topic is not available.
            activity.findViewById(R.id.notReadable).setVisibility(View.VISIBLE);
            activity.findViewById(R.id.notReadableNote).setVisibility(View.VISIBLE);
            activity.findViewById(R.id.sendMessagePanel).setVisibility(View.GONE);
            activity.findViewById(R.id.peersMessagingDisabled).setVisibility(View.GONE);
            activity.findViewById(R.id.sendMessageDisabled).setVisibility(View.VISIBLE);
            return;
        }

        Acs acs = mTopic.getAccessMode();
        if (acs == null) {
            return;
        }

        if (mTopic.isReader()) {
            activity.findViewById(R.id.notReadable).setVisibility(View.GONE);
        } else {
            activity.findViewById(R.id.notReadable).setVisibility(View.VISIBLE);
            activity.findViewById(R.id.notReadableNote).setVisibility(
                    acs.isReader(Acs.Side.GIVEN) ? View.GONE : View.VISIBLE);
        }

        if (mTopic.isWriter()) {
            activity.findViewById(R.id.sendMessageDisabled).setVisibility(View.GONE);

            Subscription peer = mTopic.getPeer();
            boolean isJoiner = peer != null && peer.acs != null && peer.acs.isJoiner(Acs.Side.WANT);
            AcsHelper missing = peer != null && peer.acs != null ? peer.acs.getMissing() : new AcsHelper();
            if (isJoiner && (missing.isReader() || missing.isWriter())) {
                activity.findViewById(R.id.peersMessagingDisabled).setVisibility(View.VISIBLE);
                activity.findViewById(R.id.sendMessagePanel).setVisibility(View.GONE);
            } else {
                if (!TextUtils.isEmpty(mMessageToSend)) {
                    EditText input = activity.findViewById(R.id.editMessage);
                    input.setText(mMessageToSend);
                    mMessageToSend = null;
                }

                activity.findViewById(R.id.peersMessagingDisabled).setVisibility(View.GONE);
                activity.findViewById(R.id.sendMessagePanel).setVisibility(View.VISIBLE);
            }
        } else {
            activity.findViewById(R.id.sendMessagePanel).setVisibility(View.GONE);
            activity.findViewById(R.id.peersMessagingDisabled).setVisibility(View.GONE);
            activity.findViewById(R.id.sendMessageDisabled).setVisibility(View.VISIBLE);
        }

        if (acs.isJoiner(Acs.Side.GIVEN) && acs.getExcessive().toString().contains("RW")) {
            showChatInvitationDialog();
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        // Stop reporting read messages
        mNoteTimer.cancel();
        mNoteTimer = null;

        final MessageActivity activity = (MessageActivity) getActivity();
        if (activity == null) {
            return;
        }

        // Save the text in the send field.
        EditText input = activity.findViewById(R.id.editMessage);
        String draft = input.getText().toString().trim();
        Bundle args = getArguments();
        if (args != null) {
            args.putString(MESSAGE_TO_SEND, draft);
            setArguments(args);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        mUploadProgress = null;
        // Close cursor.
        mMessagesAdapter.resetContent(null);
    }


    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        // Inflate the menu; this adds items to the action bar if it is present.
        inflater.inflate(R.menu.menu_topic, menu);
    }

    @Override
    public void onPrepareOptionsMenu(@NonNull final Menu menu) {
        if (mTopic != null) {
            menu.findItem(R.id.action_unmute).setVisible(mTopic.isMuted());
            menu.findItem(R.id.action_mute).setVisible(!mTopic.isMuted());

            menu.findItem(R.id.action_delete).setVisible(mTopic.isOwner());
            menu.findItem(R.id.action_leave).setVisible(!mTopic.isOwner());

            menu.findItem(R.id.action_archive).setVisible(!mTopic.isArchived());
            menu.findItem(R.id.action_unarchive).setVisible(mTopic.isArchived());
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        final Activity activity = getActivity();
        if (activity == null) {
            return false;
        }

        int id = item.getItemId();
        try {
            switch (id) {
                case R.id.action_clear:
                    mTopic.delMessages(false).thenApply(new PromisedReply.SuccessListener<ServerMessage>() {
                        @Override
                        public PromisedReply<ServerMessage> onSuccess(ServerMessage result) {
                            runMessagesLoader(mTopicName);
                            return null;
                        }
                    }, mFailureListener);
                    return true;

                case R.id.action_unmute:
                case R.id.action_mute:
                    mTopic.updateMuted(!mTopic.isMuted());
                    activity.invalidateOptionsMenu();
                    return true;

                case R.id.action_leave:
                case R.id.action_delete:
                    showDeleteTopicConfirmationDialog(activity, id == R.id.action_delete);
                    return true;

                case R.id.action_offline:
                    Cache.getTinode().reconnectNow(true,false);
                    break;
                default:
                    return super.onOptionsItemSelected(item);
            }
        } catch (NotConnectedException ignored) {
            Toast.makeText(activity, R.string.no_connection, Toast.LENGTH_SHORT).show();
        } catch (Exception ex) {
            Log.w(TAG, "Muting failed", ex);
            Toast.makeText(activity, R.string.action_failed, Toast.LENGTH_SHORT).show();
        }


        return true;
    }

    // Confirmation dialog "Do you really want to do X?"
    private void showDeleteTopicConfirmationDialog(final Activity activity, boolean del) {
        final AlertDialog.Builder confirmBuilder = new AlertDialog.Builder(activity);
        confirmBuilder.setNegativeButton(android.R.string.cancel, null);
        confirmBuilder.setMessage(del ? R.string.confirm_delete_topic :
                R.string.confirm_leave_topic);

        confirmBuilder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                try {
                    mTopic.delete().thenApply(new PromisedReply.SuccessListener<ServerMessage>() {
                        @Override
                        public PromisedReply<ServerMessage> onSuccess(ServerMessage result) {
                            Intent intent = new Intent(activity, ChatsActivity.class);
                            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                            startActivity(intent);
                            activity.finish();
                            return null;
                        }
                    }, mFailureListener);
                } catch (NotConnectedException ignored) {
                    Toast.makeText(activity, R.string.no_connection, Toast.LENGTH_SHORT).show();
                } catch (Exception err) {
                    Log.w(TAG,"Failed to delete topic", err);
                    Toast.makeText(activity, R.string.action_failed, Toast.LENGTH_SHORT).show();
                }
            }
        });
        confirmBuilder.show();
    }

    private void showChatInvitationDialog() {
        if (mChatInvitationShown) {
            return;
        }

        final Activity activity = getActivity();
        if (activity == null) {
            return;
        }

        mChatInvitationShown = true;

        final BottomSheetDialog invitation = new BottomSheetDialog(activity);
        final LayoutInflater inflater = LayoutInflater.from(invitation.getContext());
        @SuppressLint("InflateParams")
        final View view = inflater.inflate(R.layout.dialog_accept_chat, null);
        invitation.setContentView(view);
        invitation.setCancelable(false);
        invitation.setCanceledOnTouchOutside(false);

        //final AlertDialog invitation = builder.create();

        View.OnClickListener l = new View.OnClickListener() {
            @Override
            public void onClick(final View view) {
                PromisedReply<ServerMessage> response = null;
                try {
                    switch (view.getId()) {
                        case R.id.buttonAccept:
                            final String mode = mTopic.getAccessMode().getGiven();
                            response = mTopic.setMeta(new MsgSetMeta<VxCard, PrivateType>(new MetaSetSub(mode)));
                            if (mTopic.isP2PType()) {
                                // For P2P topics change 'given' permission of the peer too.
                                // In p2p topics the other user has the same name as the topic.
                                response = response.thenApply(new PromisedReply.SuccessListener<ServerMessage>() {
                                    @Override
                                    public PromisedReply<ServerMessage> onSuccess(ServerMessage result) {
                                        return mTopic.setMeta(
                                                new MsgSetMeta<VxCard, PrivateType>(new MetaSetSub(mTopic.getName(), mode)));
                                    }
                                });
                            }
                            break;

                        case R.id.buttonIgnore:
                            response = mTopic.delete();
                            break;

                        case R.id.buttonBlock:
                            mTopic.updateMode(null, "-JP");
                            break;

                        case R.id.buttonReport:
                            mTopic.updateMode(null, "-JP");
                            HashMap<String, Object> json = new HashMap<>();
                            json.put("action", "report");
                            json.put("tagret", mTopic.getName());
                            Cache.getTinode().publish(Tinode.TOPIC_SYS, new Drafty().attachJSON(json));
                            break;

                        default:
                            throw new IllegalArgumentException("Unexpected action in showChatInvitationDialog");
                    }
                } catch (NotConnectedException ignored) {
                    Toast.makeText(activity, R.string.no_connection, Toast.LENGTH_SHORT).show();
                } catch (Exception err) {
                    Log.w(TAG,"Failed to handle chat invitation", err);
                    Toast.makeText(activity, R.string.action_failed, Toast.LENGTH_SHORT).show();
                }

                invitation.dismiss();

                if (response == null) {
                    return;
                }

                response.thenApply(new PromisedReply.SuccessListener<ServerMessage>() {
                    @Override
                    public PromisedReply<ServerMessage> onSuccess(ServerMessage result) {
                        final int id = view.getId();
                        if (id == R.id.buttonIgnore || id == R.id.buttonBlock) {
                            Intent intent = new Intent(activity, ChatsActivity.class);
                            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                            startActivity(intent);
                        } else {
                            activity.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    updateFormValues();
                                }
                            });
                        }
                        return null;
                    }
                }).thenCatch(new UiUtils.ToastFailureListener(activity));
            }
        };

        view.findViewById(R.id.buttonAccept).setOnClickListener(l);
        view.findViewById(R.id.buttonIgnore).setOnClickListener(l);
        view.findViewById(R.id.buttonBlock).setOnClickListener(l);

        invitation.show();
    }

    void notifyDataSetChanged(boolean meta) {
        if (meta) {
            updateFormValues();
        } else {
            mMessagesAdapter.notifyDataSetChanged();
        }
    }

    private void openFileSelector(String mimeType, int title, int resultCode) {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType(mimeType);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(
                    Intent.createChooser(intent, getString(title)), resultCode);
        } catch (ActivityNotFoundException ex) {
            Toast.makeText(getActivity(), R.string.file_manager_not_found, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK) {
            switch (requestCode) {
                case ACTION_ATTACH_IMAGE:
                case ACTION_ATTACH_FILE: {
                    final FragmentActivity activity = getActivity();
                    if (activity == null) {
                        return;
                    }

                    if (!UiUtils.isPermissionGranted(activity, android.Manifest.permission.READ_EXTERNAL_STORAGE)) {
                        ActivityCompat.requestPermissions(activity, new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE},
                            READ_EXTERNAL_STORAGE_PERMISSION);
                        Toast.makeText(activity, R.string.some_permissions_missing, Toast.LENGTH_SHORT).show();
                    } else {
                        final Bundle args = new Bundle();
                        args.putParcelable("uri", data.getData());
                        args.putInt("requestCode", requestCode);
                        args.putString("topic", mTopicName);

                        // Must use unique ID for each upload. Otherwise trouble.
                        LoaderManager.getInstance(activity).initLoader(Cache.getUniqueCounter(), args, this);
                    }
                    break;
                }
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private boolean sendMessage(Drafty content) {
        MessageActivity  activity = (MessageActivity) getActivity();
        if (activity != null) {
            return activity.sendMessage(content);
        }
        return false;
    }

    private void sendText() {
        final Activity activity = getActivity();
        if (activity == null) {
            return;
        }
        final EditText inputField = activity.findViewById(R.id.editMessage);
        if (inputField == null) {
            return;
        }
        String message = inputField.getText().toString().trim();
        if (!message.equals("")) {
            if (sendMessage(Drafty.parse(message))) {
                // Message is successfully queued, clear text from the input field and redraw the list.
                inputField.getText().clear();
            }
        }
    }

    // Send image in-band
    private static Drafty draftyImage(String mimeType, byte[] bits, int width, int height, String fname) {
        Drafty content = Drafty.parse(" ");
        content.insertImage(0, mimeType, bits, width, height, fname);
        return content;
    }

    // Send file in-band
    private static Drafty draftyFile(String mimeType, byte[] bits, String fname) {
        Drafty content = new Drafty();
        content.attachFile(mimeType, bits, fname);
        return content;
    }

    // Send file as a link.
    private static Drafty draftyAttachment(String mimeType, String fname, String refUrl, long size) {
        Drafty content = new Drafty();
        content.attachFile(mimeType, fname, refUrl, size);
        return content;
    }


    private void sendReadNotification() {
        if (mTopic != null) {
            mTopic.noteRead();
        }
    }

    void topicSubscribed() {
        updateFormValues();
    }

    private int findItemPositionById(long id) {
        final int first = mMessageViewLayoutManager.findFirstVisibleItemPosition();
        final int last = mMessageViewLayoutManager.findLastVisibleItemPosition();
        if (last == RecyclerView.NO_POSITION) {
            return -1;
        }

        return mMessagesAdapter.getItemPositionById(id, first, last);
    }

    @NonNull
    @Override
    public Loader<UploadResult> onCreateLoader(int id, Bundle args) {
        return new FileUploader(getActivity(), args);
    }

    @Override
    public void onLoadFinished(@NonNull Loader<UploadResult> loader, final UploadResult data) {
        final MessageActivity activity = (MessageActivity) getActivity();

        if (activity != null) {
            // Kill the loader otherwise it will keep uploading the same file whenever the activity
            // is created.
            LoaderManager.getInstance(activity).destroyLoader(loader.getId());
        } else {
            return;
        }

        // Avoid processing the same result twice;
        if (data.processed) {
            return;
        } else {
            data.processed = true;
        }

        if (data.msgId > 0) {
            activity.syncMessages(data.msgId, true);
        } else if (data.error != null) {
            runMessagesLoader(mTopicName);
            Toast.makeText(activity, data.error, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onLoaderReset(@NonNull Loader<UploadResult> loader) {
    }

    void setProgressIndicator(boolean active) {
        if (!isAdded()) {
            return;
        }
        mRefresher.setRefreshing(active);
    }

    private static class FileUploader extends AsyncTaskLoader<UploadResult> {
        private static WeakReference<UploadProgress> sProgress;
        private final Bundle mArgs;
        private UploadResult mResult = null;

        FileUploader(Activity activity, Bundle args) {
            super(activity);
            mArgs = args;
        }

        static void setProgressHandler(UploadProgress progress) {
            sProgress = new WeakReference<>(progress);
        }

        @Override
        public void onStartLoading() {

            if (mResult != null) {
                // Loader has result already. Deliver it.
                deliverResult(mResult);
            } else if (mArgs.getLong("msgId") <= 0) {
                // Create a new message which will be updated with upload progress.
                Storage store = BaseDb.getInstance().getStore();
                long msgId = store.msgDraft(Cache.getTinode().getTopic(mArgs.getString("topic")), new Drafty());
                mArgs.putLong("msgId", msgId);
                UploadProgress p = sProgress.get();
                if (p != null) {
                    p.onStart(msgId);
                }
                forceLoad();
            }
        }

        @Nullable
        @Override
        public UploadResult loadInBackground() {
            // Don't upload again if upload was completed already.
            if (mResult == null) {
                mResult = doUpload(getId(), getContext(), mArgs, sProgress);
            }
            return mResult;
        }

        @Override
        public void onStopLoading() {
            super.onStopLoading();
            cancelLoad();
        }
    }

    private static Bundle getFileDetails(final Context context, Uri uri) {
        final ContentResolver resolver = context.getContentResolver();
        String fname = null;
        long fsize = 0L;

        String mimeType = resolver.getType(uri);
        if (mimeType == null) {
            mimeType = UiUtils.getMimeType(uri);
        }

        Cursor cursor = resolver.query(uri, null, null, null, null);
        if (cursor != null) {
            cursor.moveToFirst();
            fname = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
            fsize = cursor.getLong(cursor.getColumnIndex(OpenableColumns.SIZE));
            cursor.close();
        }

        // Still no size? Try opening directly.
        if (fsize == 0) {
            String path = UiUtils.getPath(context, uri);
            if (path != null) {
                File file = new File(path);
                if (fname == null) {
                    fname = file.getName();
                }
                fsize = file.length();
            }
        }

        Bundle result = new Bundle();
        result.putString("mime", mimeType);
        result.putString("name", fname);
        result.putLong("size", fsize);
        return result;
    }


    private static UploadResult doUpload(final int loaderId, final Context context, final Bundle args,
                                 final WeakReference<UploadProgress> callbackProgress) {

        final UploadResult result = new UploadResult();

        Storage store = BaseDb.getInstance().getStore();

        final int requestCode = args.getInt("requestCode");
        final String topicName = args.getString("topic");
        final Uri uri = args.getParcelable("uri");
        result.msgId = args.getLong("msgId");

        if (uri == null) {
            Log.w(TAG, "Received null URI");
            result.error = "Null URI";
            return result;
        }

        final Topic topic = Cache.getTinode().getTopic(topicName);

        Drafty content = null;
        boolean success = false;
        InputStream is = null;
        ByteArrayOutputStream baos = null;
        try {
            int imageWidth = 0, imageHeight = 0;

            Bundle fileDetails = getFileDetails(context, uri);
            String fname = fileDetails.getString("name");
            long fsize = fileDetails.getLong("size");
            String mimeType = fileDetails.getString("mime");

            if (fsize == 0) {
                Log.w(TAG, "File size is zero " + uri);
                result.error = context.getString(R.string.invalid_file);
                return result;
            }

            if (fname == null) {
                fname = context.getString(R.string.default_attachment_name);
            }

            final ContentResolver resolver = context.getContentResolver();

            // Image is being attached.
            if (requestCode == ACTION_ATTACH_IMAGE) {
                Bitmap bmp = null;

                // Make sure the image is not too large.
                if (fsize > MAX_INBAND_ATTACHMENT_SIZE) {
                    // Resize image to ensure it's under the maximum in-band size.
                    is = resolver.openInputStream(uri);
                    bmp = BitmapFactory.decodeStream(is, null, null);
                    //noinspection ConstantConditions
                    is.close();

                    // noinspection ConstantConditions: NullPointerException is handled explicitly.
                    bmp = UiUtils.scaleBitmap(bmp);
                }

                // Also ensure the image has correct orientation.
                try {
                    is = resolver.openInputStream(uri);
                    //noinspection ConstantConditions
                    ExifInterface exif = new ExifInterface(is);
                    is.close();
                    int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED);
                    if (orientation != ExifInterface.ORIENTATION_UNDEFINED &&
                            orientation != ExifInterface.ORIENTATION_NORMAL) {
                        // Rotate image to ensure correct orientation.
                        if (bmp == null) {
                            is = resolver.openInputStream(uri);
                            bmp = BitmapFactory.decodeStream(is, null, null);
                            //noinspection ConstantConditions
                            is.close();
                        }

                        bmp = UiUtils.rotateBitmap(bmp, orientation);
                    }
                } catch (IOException ex) {
                    Log.w(TAG, "Failed to obtain image orientation", ex);
                }

                if (bmp != null) {
                    imageWidth = bmp.getWidth();
                    imageHeight = bmp.getHeight();

                    is = UiUtils.bitmapToStream(bmp, mimeType);
                    fsize = (long) is.available();
                }
            }

            if (fsize > MAX_ATTACHMENT_SIZE) {
                Log.w(TAG, "Unable to process attachment: too big, size=" + fsize);
                result.error = context.getString(R.string.attachment_too_large,
                        UiUtils.bytesToHumanSize(fsize), UiUtils.bytesToHumanSize(MAX_ATTACHMENT_SIZE));
            } else {
                if (is == null) {
                    is = resolver.openInputStream(uri);
                }

                if (requestCode == ACTION_ATTACH_FILE && fsize > MAX_INBAND_ATTACHMENT_SIZE) {

                    // Update draft with file data.
                    store.msgDraftUpdate(topic, result.msgId, draftyAttachment(mimeType, fname, uri.toString(), -1));

                    UploadProgress start = callbackProgress.get();
                    if (start != null) {
                        start.onStart(result.msgId);
                        // This assignment is needed to ensure that the loader does not keep
                        // a strong reference to activity while potentially slow upload process
                        // is running.
                        //noinspection UnusedAssignment
                        start = null;
                    }

                    // Upload then send message with a link. This is a long-running blocking call.
                    final LargeFileHelper uploader = Cache.getTinode().getFileUploader();
                    MsgServerCtrl ctrl = uploader.upload(is, fname, mimeType, fsize,
                            new LargeFileHelper.FileHelperProgress() {
                                @Override
                                public void onProgress(long progress, long size) {
                                    UploadProgress p = callbackProgress.get();
                                    if (p != null) {
                                        if (!p.onProgress(loaderId, result.msgId, progress, size)) {
                                            uploader.cancel();
                                        }
                                    }
                                }
                            });
                    success = (ctrl != null && ctrl.code == 200);
                    if (success) {
                        content = draftyAttachment(mimeType, fname, ctrl.getStringParam("url", null), fsize);
                    }
                } else {
                    baos = new ByteArrayOutputStream();
                    byte[] buffer = new byte[16384];
                    int len;
                    // noinspection ConstantConditions: NullPointerException is handled explicitly.
                    while ((len = is.read(buffer)) > 0) {
                        baos.write(buffer, 0, len);
                    }

                    byte[] bits = baos.toByteArray();
                    if (requestCode == ACTION_ATTACH_FILE) {
                        store.msgDraftUpdate(topic, result.msgId, draftyFile(mimeType, bits, fname));
                    } else {
                        if (imageWidth == 0) {
                            BitmapFactory.Options options = new BitmapFactory.Options();
                            options.inJustDecodeBounds = true;
                            InputStream bais = new ByteArrayInputStream(bits);
                            BitmapFactory.decodeStream(bais, null, options);
                            bais.close();

                            imageWidth = options.outWidth;
                            imageHeight = options.outHeight;
                        }
                        store.msgDraftUpdate(topic, result.msgId,
                                draftyImage(mimeType, bits, imageWidth, imageHeight, fname));
                    }
                    success = true;
                    UploadProgress start = callbackProgress.get();
                    if (start != null) {
                        start.onStart(result.msgId);
                    }
                }
            }
        } catch (IOException | NullPointerException ex) {
            result.error = ex.getMessage();
            if (!"cancelled".equals(result.error)) {
                Log.w(TAG, "Failed to attach file", ex);
            }
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException ignored) {}
            }
            if (baos != null) {
                try {
                    baos.close();
                } catch (IOException ignored) {}
            }
        }

        if (result.msgId > 0) {
            if (success) {
                // Success: mark message as ready for delivery. If content==null it won't be saved.
                store.msgReady(topic, result.msgId, content);
            } else {
                // Failure: discard draft.
                store.msgDiscard(topic, result.msgId);
                result.msgId = -1;
            }
        }

        return result;
    }

    static class UploadResult {
        String error;
        long msgId = -1;
        boolean processed = false;

        UploadResult() {
        }

        @NonNull
        public String toString() {
            return "msgId=" + msgId + ", error='" + error + "'";
        }
    }

    private class UploadProgress {

        UploadProgress() {
        }

        void onStart(final long msgId) {
            // Reload the cursor.
            runMessagesLoader(mTopicName);
        }

        // Returns true to continue the upload, false to cancel.
        boolean onProgress(final int loaderId, final long msgId, final long progress, final long total) {
            // DEBUG -- slow down the upload progress.
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            // debug


            // Check for cancellation.
            Integer oldLoaderId = mMessagesAdapter.getLoaderMapping(msgId);
            if (oldLoaderId == null) {
                mMessagesAdapter.addLoaderMapping(msgId, loaderId);
            } else if (oldLoaderId != loaderId) {
                // Loader id has changed, cancel.
                return false;
            }

            Activity activity = getActivity();
            if (activity == null) {
                return true;
            }

            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    final int position = findItemPositionById(msgId);
                    if (position < 0) {
                        return;
                    }
                    mMessagesAdapter.notifyItemChanged(position,
                            total > 0 ? (float) progress / total : (float) progress);
                }
            });

            return true;
        }
    }
}
