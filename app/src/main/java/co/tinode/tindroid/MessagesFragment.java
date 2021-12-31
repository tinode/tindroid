package co.tinode.tindroid;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Parcelable;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
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

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Locale;
import java.util.Map;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.work.Data;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import co.tinode.tindroid.db.BaseDb;
import co.tinode.tindroid.db.StoredTopic;
import co.tinode.tindroid.format.SendForwardedFormatter;
import co.tinode.tindroid.format.SendReplyFormatter;
import co.tinode.tindroid.media.VxCard;
import co.tinode.tinodesdk.ComTopic;
import co.tinode.tinodesdk.PromisedReply;
import co.tinode.tinodesdk.Tinode;
import co.tinode.tinodesdk.model.AccessChange;
import co.tinode.tinodesdk.model.Acs;
import co.tinode.tinodesdk.model.AcsHelper;
import co.tinode.tinodesdk.model.Drafty;
import co.tinode.tinodesdk.model.MetaSetSub;
import co.tinode.tinodesdk.model.MsgSetMeta;
import co.tinode.tinodesdk.model.ServerMessage;
import co.tinode.tinodesdk.model.Subscription;

/**
 * Fragment handling message display and message sending.
 */
public class MessagesFragment extends Fragment {
    private static final String TAG = "MessageFragment";
    private static final int MESSAGES_TO_LOAD = 24;

    static final String MESSAGE_TO_SEND = "messageText";
    static final String MESSAGE_REPLY = "reply";
    static final String MESSAGE_REPLY_ID = "replyID";

    private ComTopic<VxCard> mTopic;

    private LinearLayoutManager mMessageViewLayoutManager;
    private RecyclerView mRecyclerView;
    private MessagesAdapter mMessagesAdapter;
    private SwipeRefreshLayout mRefresher;

    private String mTopicName = null;
    private String mMessageToSend = null;
    private boolean mChatInvitationShown = false;

    private String mCurrentPhotoFile;
    private Uri mCurrentPhotoUri;

    private int mReplySeqID = -1;
    private Drafty mReply = null;
    private Drafty mContentToForward = null;
    private Drafty mForwardSender = null;

    private PromisedReply.FailureListener<ServerMessage> mFailureListener;

    private final ActivityResultLauncher<String> mFileOpenerRequestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
        // Check if permission is granted.
        if (isGranted) {
            openFileSelector(getActivity());
        }
    });

    private final ActivityResultLauncher<String[]> mImagePickerRequestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                for (Map.Entry<String,Boolean> e : result.entrySet()) {
                    // Check if all required permissions are granted.
                    if (!e.getValue()) {
                        return;
                    }
                }
                // Try to open the image selector again.
                openImageSelector(getActivity());
            });

    private final ActivityResultLauncher<String> mFilePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), content -> {
                if (content == null) {
                    return;
                }

                final MessageActivity activity = (MessageActivity) getActivity();
                if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
                    return;
                }

                final Bundle args = new Bundle();
                args.putParcelable(AttachmentHandler.ARG_SRC_LOCAL_URI, content);
                args.putString(AttachmentHandler.ARG_OPERATION, AttachmentHandler.ARG_OPERATION_FILE);
                args.putString(AttachmentHandler.ARG_TOPIC_NAME, mTopicName);
                // Show attachment preview.
                activity.showFragment(MessageActivity.FRAGMENT_FILE_PREVIEW, args, true);
            });

    private final ActivityResultLauncher<Intent> mImagePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result == null || result.getResultCode() != Activity.RESULT_OK) {
                    return;
                }

                final Intent data = result.getData();
                final MessageActivity activity = (MessageActivity) getActivity();
                if (data == null || activity == null || activity.isFinishing() || activity.isDestroyed()) {
                    return;
                }

                final Bundle args = new Bundle();
                if (data.getData() == null) {
                    // Image from the camera.
                    args.putString(AttachmentHandler.ARG_FILE_PATH, mCurrentPhotoFile);
                    args.putParcelable(AttachmentHandler.ARG_SRC_LOCAL_URI, mCurrentPhotoUri);
                    mCurrentPhotoFile = null;
                    mCurrentPhotoUri = null;
                } else {
                    // Image from the gallery.
                    args.putParcelable(AttachmentHandler.ARG_SRC_LOCAL_URI, data.getData());
                }

                args.putString(AttachmentHandler.ARG_OPERATION,AttachmentHandler.ARG_OPERATION_IMAGE);
                args.putString(AttachmentHandler.ARG_TOPIC_NAME, mTopicName);
                // Show attachment preview.
                activity.showFragment(MessageActivity.FRAGMENT_VIEW_IMAGE, args, true);
            });

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        setHasOptionsMenu(true);
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
                // This is a hack for IndexOutOfBoundsException:
                //  Inconsistency detected. Invalid view holder adapter positionViewHolder
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
        mMessageViewLayoutManager.setReverseLayout(true);

        mRecyclerView = view.findViewById(R.id.messages_container);
        mRecyclerView.setLayoutManager(mMessageViewLayoutManager);

        mRefresher = view.findViewById(R.id.swipe_refresher);
        mMessagesAdapter = new MessagesAdapter(activity, mRefresher);
        mRecyclerView.setAdapter(mMessagesAdapter);
        mRefresher.setOnRefreshListener(() -> {
            if (!mMessagesAdapter.loadNextPage() && !StoredTopic.isAllDataLoaded(mTopic)) {
                try {
                    mTopic.getMeta(mTopic.getMetaGetBuilder().withEarlierData(MESSAGES_TO_LOAD).build())
                            .thenApply(
                                    new PromisedReply.SuccessListener<ServerMessage>() {
                                        @Override
                                        public PromisedReply<ServerMessage> onSuccess(ServerMessage result) {
                                            activity.runOnUiThread(() -> mRefresher.setRefreshing(false));
                                            return null;
                                        }
                                    },
                                    new PromisedReply.FailureListener<ServerMessage>() {
                                        @Override
                                        public PromisedReply<ServerMessage> onFailure(Exception err) {
                                            activity.runOnUiThread(() -> mRefresher.setRefreshing(false));
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
        });

        mFailureListener = new UiUtils.ToastFailureListener(activity);

        // Send message on button click
        view.findViewById(R.id.chatSendButton).setOnClickListener(v -> sendText(activity));
        view.findViewById(R.id.chatForwardButton).setOnClickListener(v -> sendText(activity));

        // Send image button
        view.findViewById(R.id.attachImage).setOnClickListener(v -> openImageSelector(activity));

        // Send file button
        view.findViewById(R.id.attachFile).setOnClickListener(v -> openFileSelector(activity));

        // Cancel reply preview button.
        view.findViewById(R.id.cancelPreview).setOnClickListener(v -> cancelPreview(activity));
        view.findViewById(R.id.cancelForwardingPreview).setOnClickListener(v -> cancelPreview(activity));

        EditText editor = view.findViewById(R.id.editMessage);
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

        view.findViewById(R.id.enablePeerButton).setOnClickListener(view1 -> {
            // Enable peer.
            Acs am = new Acs(mTopic.getAccessMode());
            am.update(new AccessChange(null, "+RW"));
            mTopic.setMeta(new MsgSetMeta<>(new MetaSetSub(mTopic.getName(), am.getGiven())))
                    .thenCatch(new UiUtils.ToastFailureListener(activity));
        });

        // Monitor status of attachment uploads and update messages accordingly.
        WorkManager.getInstance(activity).getWorkInfosByTagLiveData(AttachmentHandler.TAG_UPLOAD_WORK)
                .observe(activity, workInfos -> {
                    for (WorkInfo wi : workInfos) {
                        WorkInfo.State state = wi.getState();
                        switch (state) {
                            case RUNNING: {
                                Data data = wi.getProgress();
                                String topicName = data.getString(AttachmentHandler.ARG_TOPIC_NAME);
                                if (topicName == null) {
                                    // Not a progress report, just a status change.
                                    break;
                                }
                                if (!topicName.equals(mTopicName)) {
                                    break;
                                }
                                long progress = data.getLong(AttachmentHandler.ARG_PROGRESS, -1);
                                if (progress < 0) {
                                    break;
                                }
                                if (progress == 0) {
                                    // New message. Update.
                                    runMessagesLoader(mTopicName);
                                    break;
                                }
                                long msgId = data.getLong(AttachmentHandler.ARG_MSG_ID, -1L);
                                final int position = findItemPositionById(msgId);
                                if (position >= 0) {
                                    long total = data.getLong(AttachmentHandler.ARG_FILE_SIZE, 1L);
                                    mMessagesAdapter.notifyItemChanged(position, (float) progress / total);
                                }
                                break;
                            }
                            case SUCCEEDED: {
                                Data result = wi.getOutputData();
                                String topicName = result.getString(AttachmentHandler.ARG_TOPIC_NAME);
                                if (topicName == null) {
                                    break;
                                }
                                long msgId = result.getLong(AttachmentHandler.ARG_MSG_ID, -1L);
                                if (msgId > 0 && topicName.equals(mTopicName)) {
                                    activity.syncMessages(msgId, true);
                                }
                                break;
                            }
                            case CANCELLED:
                                // When cancelled wi.getOutputData() returns empty Data.
                            case ENQUEUED:
                            case BLOCKED:
                                // Do nothing.
                                break;

                            case FAILED: {
                                Data failure = wi.getOutputData();
                                String topicName = failure.getString(AttachmentHandler.ARG_TOPIC_NAME);
                                if (topicName == null) {
                                    break;
                                }
                                if (topicName.equals(mTopicName)) {
                                    long msgId = failure.getLong(AttachmentHandler.ARG_MSG_ID, -1L);
                                    if (BaseDb.getInstance().getStore().getMessageById(msgId) != null) {
                                        runMessagesLoader(mTopicName);
                                        String error = failure.getString(AttachmentHandler.ARG_ERROR);
                                        Toast.makeText(activity, error, Toast.LENGTH_SHORT).show();
                                    }
                                }
                                break;
                            }
                        }
                    }
                });
    }

    @Override
    @SuppressWarnings("unchecked")
    public void onResume() {
        super.onResume();

        final MessageActivity activity = (MessageActivity) getActivity();
        if (activity == null) {
            return;
        }

        Bundle args = getArguments();
        if (args != null) {
            mTopicName = args.getString("topic");
        }

        if (mTopicName != null) {
            mTopic = (ComTopic<VxCard>) Cache.getTinode().getTopic(mTopicName);
            runMessagesLoader(mTopicName);
        } else {
            mTopic = null;
        }

        mRefresher.setRefreshing(false);

        updateFormValues(args);
        activity.sendNoteRead(0);
    }

    void runMessagesLoader(String topicName) {
        mMessagesAdapter.resetContent(topicName);
    }

    private void updateFormValues(Bundle args) {
        if (!isAdded()) {
            return;
        }

        final MessageActivity activity = (MessageActivity) getActivity();
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            return;
        }

        activity.findViewById(R.id.replyPreviewWrapper).setVisibility(View.GONE);
        activity.findViewById(R.id.forwardMessagePanel).setVisibility(View.GONE);

        if (mTopic == null) {
            // Default view when the topic is not available.
            activity.findViewById(R.id.notReadable).setVisibility(View.VISIBLE);
            activity.findViewById(R.id.notReadableNote).setVisibility(View.VISIBLE);
            activity.findViewById(R.id.sendMessagePanel).setVisibility(View.GONE);
            activity.findViewById(R.id.peersMessagingDisabled).setVisibility(View.GONE);
            activity.findViewById(R.id.sendMessageDisabled).setVisibility(View.VISIBLE);
            UiUtils.setupToolbar(activity, null, mTopicName, false, null);
            return;
        }

        UiUtils.setupToolbar(activity, mTopic.getPub(), mTopicName, mTopic.getOnline(), mTopic.getLastSeen());

        Acs acs = mTopic.getAccessMode();
        if (acs == null || !acs.isModeDefined()) {
            return;
        }

        if (mTopic.isReader()) {
            activity.findViewById(R.id.notReadable).setVisibility(View.GONE);
        } else {
            activity.findViewById(R.id.notReadable).setVisibility(View.VISIBLE);
            activity.findViewById(R.id.notReadableNote).setVisibility(
                    acs.isReader(Acs.Side.GIVEN) ? View.GONE : View.VISIBLE);
        }

        if (args != null) {
            mMessageToSend = args.getString(MESSAGE_TO_SEND);
            mReplySeqID = args.getInt(MESSAGE_REPLY_ID);
            mReply = (Drafty) args.getSerializable(MESSAGE_REPLY);
            mContentToForward = (Drafty) args.getSerializable(ForwardToFragment.CONTENT_TO_FORWARD);
            mForwardSender = (Drafty) args.getSerializable(ForwardToFragment.FORWARDING_FROM_USER);
            // Clear used arguments.
            args.remove(MESSAGE_TO_SEND);
            args.remove(MESSAGE_REPLY_ID);
            args.remove(MESSAGE_REPLY);
            args.remove(ForwardToFragment.CONTENT_TO_FORWARD);
            args.remove(ForwardToFragment.FORWARDING_FROM_USER);
        }

        if (mContentToForward != null) {
            showContentToForward(activity, mForwardSender, mContentToForward);
        } else if (mTopic.isWriter() && !mTopic.isBlocked()) {
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


    private void scrollToBottom() {
        mRecyclerView.scrollToPosition(0);
    }

    @Override
    public void onPause() {
        super.onPause();

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
            args.putInt(MESSAGE_REPLY_ID, mReplySeqID);
            args.putSerializable(MESSAGE_REPLY, mReply);
            args.putSerializable(ForwardToFragment.CONTENT_TO_FORWARD, mContentToForward);
            args.putSerializable(ForwardToFragment.FORWARDING_FROM_USER, mForwardSender);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        // Close cursor.
        if (mMessagesAdapter != null) {
            mMessagesAdapter.resetContent(null);
        }
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
        if (id == R.id.action_clear) {
            mTopic.delMessages(false).thenApply(new PromisedReply.SuccessListener<ServerMessage>() {
                @Override
                public PromisedReply<ServerMessage> onSuccess(ServerMessage result) {
                    runMessagesLoader(mTopicName);
                    return null;
                }
            }, mFailureListener);
            return true;
        } else if (id == R.id.action_unmute || id == R.id.action_mute) {
            mTopic.updateMuted(!mTopic.isMuted());
            activity.invalidateOptionsMenu();
            return true;
        } else if (id == R.id.action_leave || id == R.id.action_delete) {
            showDeleteTopicConfirmationDialog(activity, id == R.id.action_delete);
            return true;
        } else if (id == R.id.action_offline) {
            Cache.getTinode().reconnectNow(true, false, false);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    void setRefreshing(boolean active) {
        if (!isAdded()) {
            return;
        }
        mRefresher.setRefreshing(active);
    }

    // Confirmation dialog "Do you really want to do X?"
    private void showDeleteTopicConfirmationDialog(final Activity activity, boolean del) {
        final AlertDialog.Builder confirmBuilder = new AlertDialog.Builder(activity);
        confirmBuilder.setNegativeButton(android.R.string.cancel, null);
        confirmBuilder.setMessage(del ? R.string.confirm_delete_topic :
                R.string.confirm_leave_topic);

        confirmBuilder.setPositiveButton(android.R.string.ok, (dialog, which) ->
                mTopic.delete(true).thenApply(new PromisedReply.SuccessListener<ServerMessage>() {
                    @Override
                    public PromisedReply<ServerMessage> onSuccess(ServerMessage result) {
                        Intent intent = new Intent(activity, ChatsActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                        startActivity(intent);
                        activity.finish();
                        return null;
                    }
                }, mFailureListener));
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
        @SuppressLint("InflateParams") final View view = inflater.inflate(R.layout.dialog_accept_chat, null);
        invitation.setContentView(view);
        invitation.setCancelable(false);
        invitation.setCanceledOnTouchOutside(false);

        View.OnClickListener l = view1 -> {
            PromisedReply<ServerMessage> response = null;
            int id = view1.getId();
            if (id == R.id.buttonAccept) {
                final String mode = mTopic.getAccessMode().getGiven();
                response = mTopic.setMeta(new MsgSetMeta<>(new MetaSetSub(mode)));
                if (mTopic.isP2PType()) {
                    // For P2P topics change 'given' permission of the peer too.
                    // In p2p topics the other user has the same name as the topic.
                    response = response.thenApply(new PromisedReply.SuccessListener<ServerMessage>() {
                        @Override
                        public PromisedReply<ServerMessage> onSuccess(ServerMessage result) {
                            return mTopic.setMeta(
                                    new MsgSetMeta<>(new MetaSetSub(mTopic.getName(), mode)));
                        }
                    });
                }
            } else if (id == R.id.buttonIgnore) {
                response = mTopic.delete(true);
            } else if (id == R.id.buttonBlock) {
                mTopic.updateMode(null, "-JP");
            } else if (id == R.id.buttonReport) {
                mTopic.updateMode(null, "-JP");
                HashMap<String, Object> json = new HashMap<>();
                json.put("action", "report");
                json.put("target", mTopic.getName());
                Drafty msg = new Drafty().attachJSON(json);
                Cache.getTinode().publish(Tinode.TOPIC_SYS, msg, Tinode.draftyHeadersFor(msg));
            } else {
                throw new IllegalArgumentException("Unexpected action in showChatInvitationDialog");
            }

            invitation.dismiss();

            if (response == null) {
                return;
            }

            response.thenApply(new PromisedReply.SuccessListener<ServerMessage>() {
                @Override
                public PromisedReply<ServerMessage> onSuccess(ServerMessage result) {
                    final int id = view1.getId();
                    if (id == R.id.buttonIgnore || id == R.id.buttonBlock) {
                        Intent intent = new Intent(activity, ChatsActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                        startActivity(intent);
                    } else {
                        activity.runOnUiThread(() -> updateFormValues(null));
                    }
                    return null;
                }
            }).thenCatch(new UiUtils.ToastFailureListener(activity));
        };

        view.findViewById(R.id.buttonAccept).setOnClickListener(l);
        view.findViewById(R.id.buttonIgnore).setOnClickListener(l);
        view.findViewById(R.id.buttonBlock).setOnClickListener(l);

        invitation.show();
    }

    @SuppressLint("NotifyDataSetChanged")
    void notifyDataSetChanged(boolean meta) {
        if (meta) {
            updateFormValues(null);
        } else {
            mMessagesAdapter.notifyDataSetChanged();
        }
    }

    private void openFileSelector(@Nullable Activity activity) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            return;
        }

        if (!UiUtils.isPermissionGranted(activity, Manifest.permission.READ_EXTERNAL_STORAGE)) {
            mFileOpenerRequestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE);
            return;
        }

        mFilePickerLauncher.launch("*/*");
    }

    private void openImageSelector(@Nullable final Activity activity) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            return;
        }

        LinkedList<String> missing = UiUtils.getMissingPermissions(activity,
                new String[]{Manifest.permission.CAMERA, Manifest.permission.READ_EXTERNAL_STORAGE});
        if (!missing.isEmpty()) {
            mImagePickerRequestPermissionLauncher.launch(missing.toArray(new String[]{}));
            return;
        }

        // Pick image from gallery.
        Intent galleryIntent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        galleryIntent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/jpeg", "image/png"});

        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        // Make sure camera is available.
        if (cameraIntent.resolveActivity(activity.getPackageManager()) != null) {
            // Create temp file for storing the photo.
            File photoFile = null;
            try {
                photoFile = createImageFile(activity);
            } catch (IOException ex) {
                // Error occurred while creating the File
                Log.w(TAG, "Unable to create temp file for storing camera photo", ex);
            }
            // Continue only if the File was successfully created
            if (photoFile != null) {
                Uri photoUri = FileProvider.getUriForFile(activity,
                        "co.tinode.tindroid.provider", photoFile);

                cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
                // See explanation here: http://medium.com/@quiro91/ceb9bb0eec3a
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.LOLLIPOP) {
                    cameraIntent.setClipData(ClipData.newRawUri("", photoUri));
                    cameraIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                }

                mCurrentPhotoFile = photoFile.getAbsolutePath();
                mCurrentPhotoUri = photoUri;

            } else {
                cameraIntent = null;
            }
        } else {
            cameraIntent = null;
        }

        // Pack two intents into a chooser.
        Intent chooserIntent = Intent.createChooser(galleryIntent, getString(R.string.select_image));
        if (cameraIntent != null) {
            chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Parcelable[]{cameraIntent});
        }
        mImagePickerLauncher.launch(chooserIntent);
    }

    private File createImageFile(Activity activity) throws IOException {
        // Create an image file name
        String imageFileName = "IMG_" +
                new SimpleDateFormat("yyMMdd_HHmmss", Locale.getDefault()).format(new Date()) + "_";
        File storageDir = activity.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File imageFile = File.createTempFile(
                imageFileName,  // prefix
                ".jpg",  // suffix
                storageDir      // directory
        );

        // Make sure directories exist.
        File path = imageFile.getParentFile();
        if (path != null) {
            path.mkdirs();
        }

        return imageFile;
    }

    private boolean sendMessage(Drafty content, int replyTo) {
        MessageActivity activity = (MessageActivity) getActivity();
        if (activity != null) {
            boolean done = activity.sendMessage(content, replyTo);
            if (done) {
                scrollToBottom();
            }
            return done;
        }
        return false;
    }

    private void sendText(Activity activity) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            return;
        }
        final EditText inputField = activity.findViewById(R.id.editMessage);
        if (inputField == null) {
            return;
        }

        if (mContentToForward != null) {
            if (sendMessage(mForwardSender.appendLineBreak().append(mContentToForward), -1)) {
                mForwardSender = null;
                mContentToForward = null;
            }
            activity.findViewById(R.id.forwardMessagePanel).setVisibility(View.GONE);
            activity.findViewById(R.id.sendMessagePanel).setVisibility(View.VISIBLE);
            return;
        }

        String message = inputField.getText().toString().trim();
        if (!message.equals("")) {
            Drafty msg = Drafty.parse(message);
            if (mReply != null) {
                msg = mReply.append(msg);
            }
            if (sendMessage(msg, mReplySeqID)) {
                // Message is successfully queued, clear text from the input field and redraw the list.
                inputField.getText().clear();
                if (mReplySeqID > 0) {
                    mReplySeqID = -1;
                    mReply = null;
                    activity.findViewById(R.id.replyPreviewWrapper).setVisibility(View.GONE);
                }
            }
        }
    }

    private void cancelPreview(Activity activity) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            return;
        }

        mReplySeqID = -1;
        mReply = null;
        mContentToForward = null;
        mForwardSender = null;

        activity.findViewById(R.id.replyPreviewWrapper).setVisibility(View.GONE);
        activity.findViewById(R.id.forwardMessagePanel).setVisibility(View.GONE);
        activity.findViewById(R.id.sendMessagePanel).setVisibility(View.VISIBLE);
    }

    void showReply(Activity activity, Drafty reply, int seq) {
        mReply = reply;
        mReplySeqID = seq;
        mContentToForward = null;
        mForwardSender = null;

        activity.findViewById(R.id.forwardMessagePanel).setVisibility(View.GONE);
        activity.findViewById(R.id.sendMessagePanel).setVisibility(View.VISIBLE);
        activity.findViewById(R.id.replyPreviewWrapper).setVisibility(View.VISIBLE);
        TextView replyHolder = activity.findViewById(R.id.contentPreview);
        replyHolder.setText(reply.format(new SendReplyFormatter(replyHolder)));
    }

    private void showContentToForward(Activity activity, Drafty sender, Drafty content) {
        mReplySeqID = -1;
        mReply = null;

        activity.findViewById(R.id.sendMessagePanel).setVisibility(View.GONE);
        TextView previewHolder = activity.findViewById(R.id.forwardedContentPreview);
        content = new Drafty().append(sender).appendLineBreak().append(content.preview(UiUtils.QUOTED_REPLY_LENGTH));
        previewHolder.setText(content.format(new SendForwardedFormatter(previewHolder)));
        activity.findViewById(R.id.forwardMessagePanel).setVisibility(View.VISIBLE);
    }

    void topicSubscribed(String topicName, boolean reset) {
        mTopicName = topicName;
        if (mTopicName != null) {
            //noinspection unchecked
            mTopic = (ComTopic<VxCard>) Cache.getTinode().getTopic(mTopicName);
        } else {
            mTopic = null;
        }
        updateFormValues(getArguments());
        if (reset) {
            runMessagesLoader(mTopicName);
        }
    }

    private int findItemPositionById(long id) {
        final int first = mMessageViewLayoutManager.findFirstVisibleItemPosition();
        final int last = mMessageViewLayoutManager.findLastVisibleItemPosition();
        if (last == RecyclerView.NO_POSITION) {
            return -1;
        }

        return mMessagesAdapter.findItemPositionById(id, first, last);
    }
}
