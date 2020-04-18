package co.tinode.tindroid;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.DialogInterface;
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

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.Loader;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import co.tinode.tindroid.db.StoredTopic;
import co.tinode.tindroid.media.VxCard;
import co.tinode.tinodesdk.ComTopic;
import co.tinode.tinodesdk.NotConnectedException;
import co.tinode.tinodesdk.PromisedReply;
import co.tinode.tinodesdk.Tinode;
import co.tinode.tinodesdk.model.AccessChange;
import co.tinode.tinodesdk.model.Acs;
import co.tinode.tinodesdk.model.AcsHelper;
import co.tinode.tinodesdk.model.Drafty;
import co.tinode.tinodesdk.model.MetaSetSub;
import co.tinode.tinodesdk.model.MsgSetMeta;
import co.tinode.tinodesdk.model.PrivateType;
import co.tinode.tinodesdk.model.ServerMessage;
import co.tinode.tinodesdk.model.Subscription;

import static android.app.Activity.RESULT_OK;

/**
 * Fragment handling message display and message sending.
 */
public class MessagesFragment extends Fragment
        implements LoaderManager.LoaderCallbacks<AttachmentUploader.Result> {
    private static final String TAG = "MessageFragment";

    static final String MESSAGE_TO_SEND = "messageText";

    private static final int MESSAGES_TO_LOAD = 24;

    private static final int ACTION_ATTACH_FILE = 100;
    private static final int ACTION_ATTACH_IMAGE = 101;

    private static final int READ_EXTERNAL_STORAGE_PERMISSION = 1;
    private static final int USE_CAMERA_PERMISSION = 2;

    private ComTopic<VxCard> mTopic;

    private LinearLayoutManager mMessageViewLayoutManager;
    private RecyclerView mRecyclerView;
    private MessagesAdapter mMessagesAdapter;
    private SwipeRefreshLayout mRefresher;

    // It cannot be local.
    private UploadProgress mUploadProgress;

    private String mTopicName = null;
    private String mMessageToSend = null;
    private boolean mChatInvitationShown = false;

    private String mCurrentPhotoFile;
    private Uri mCurrentPhotoUri;

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

        mRecyclerView = view.findViewById(R.id.messages_container);
        mRecyclerView.setLayoutManager(mMessageViewLayoutManager);

        // Creating a strong reference from this Fragment, otherwise it will be immediately garbage collected.
        mUploadProgress = new UploadProgress();
        // This needs to be rebound on activity creation.
        AttachmentUploader.FileUploader.setProgressHandler(mUploadProgress);

        mRefresher = view.findViewById(R.id.swipe_refresher);
        mMessagesAdapter = new MessagesAdapter(activity, mRefresher);
        mRecyclerView.setAdapter(mMessagesAdapter);
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
                                                activity.runOnUiThread(new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        mRefresher.setRefreshing(false);
                                                    }
                                                });
                                                return null;
                                            }
                                        },
                                        new PromisedReply.FailureListener<ServerMessage>() {
                                            @Override
                                            public PromisedReply<ServerMessage> onFailure(Exception err) {
                                                activity.runOnUiThread(new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        mRefresher.setRefreshing(false);
                                                    }
                                                });
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
                openImageSelector(activity);
            }
        });

        // Send file button
        view.findViewById(R.id.attachFile).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openFileSelector(activity);
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

        mRefresher.setRefreshing(false);

        final MessageActivity activity = (MessageActivity) getActivity();
        if (activity != null) {
            updateFormValues();
            activity.sendNoteRead(0);
        }
    }

    void runMessagesLoader(String topicName) {
        mMessagesAdapter.resetContent(topicName);
    }

    private void updateFormValues() {
        if (!isAdded()) {
            return;
        }

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
                    mTopic.delete(true).thenApply(new PromisedReply.SuccessListener<ServerMessage>() {
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
                            response = mTopic.delete(true);
                            break;

                        case R.id.buttonBlock:
                            mTopic.updateMode(null, "-JP");
                            break;

                        case R.id.buttonReport:
                            mTopic.updateMode(null, "-JP");
                            HashMap<String, Object> json = new HashMap<>();
                            json.put("action", "report");
                            json.put("tagret", mTopic.getName());
                            Drafty msg = new Drafty().attachJSON(json);
                            Cache.getTinode().publish(Tinode.TOPIC_SYS, msg, Tinode.draftyHeadersFor(msg));
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

    private void openFileSelector(Activity activity) {
        if (activity.isFinishing() || activity.isDestroyed()) {
            return;
        }

        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(
                    Intent.createChooser(intent, getString(R.string.select_file)), ACTION_ATTACH_FILE);
        } catch (ActivityNotFoundException ex) {
            Toast.makeText(getActivity(), R.string.file_manager_not_found, Toast.LENGTH_SHORT).show();
        }
    }

    private void openImageSelector(Activity activity) {
        if (activity.isFinishing() || activity.isDestroyed()) {
            return;
        }

        if (!UiUtils.isPermissionGranted(activity, Manifest.permission.CAMERA)) {
            ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.CAMERA},
                    USE_CAMERA_PERMISSION);
            Toast.makeText(activity, R.string.some_permissions_missing, Toast.LENGTH_SHORT).show();

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
                    cameraIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_READ_URI_PERMISSION);
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

        startActivityForResult(chooserIntent, ACTION_ATTACH_IMAGE);
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

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK) {
            switch (requestCode) {
                case ACTION_ATTACH_IMAGE:
                case ACTION_ATTACH_FILE: {
                    final MessageActivity activity = (MessageActivity) getActivity();
                    if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
                        return;
                    }

                    if (!UiUtils.isPermissionGranted(activity, android.Manifest.permission.READ_EXTERNAL_STORAGE)) {
                        ActivityCompat.requestPermissions(activity, new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE},
                            READ_EXTERNAL_STORAGE_PERMISSION);
                        return;
                    }

                    final Bundle args = new Bundle();
                    if (data == null || data.getData() == null) {
                        // Camera
                        args.putString("file", mCurrentPhotoFile);
                        args.putParcelable("uri", mCurrentPhotoUri);
                        mCurrentPhotoFile = null;
                        mCurrentPhotoUri = null;
                    } else {
                        // Gallery
                        args.putParcelable("uri", data.getData());
                    }

                    args.putString("operation", requestCode == ACTION_ATTACH_IMAGE ? "image" : "file");
                    args.putString("topic", mTopicName);

                    // Show attachment preview.
                    activity.showFragment(requestCode == ACTION_ATTACH_IMAGE ?
                            MessageActivity.FRAGMENT_VIEW_IMAGE :
                            MessageActivity.FRAGMENT_FILE_PREVIEW,
                            args, true);

                    return;
                }
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private boolean sendMessage(Drafty content) {
        MessageActivity  activity = (MessageActivity) getActivity();
        if (activity != null) {
            boolean done = activity.sendMessage(content);
            if (done) {
                scrollToBottom();
            }
            return done;
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

    void topicSubscribed() {
        updateFormValues();
    }

    private int findItemPositionById(long id) {
        final int first = mMessageViewLayoutManager.findFirstVisibleItemPosition();
        final int last = mMessageViewLayoutManager.findLastVisibleItemPosition();
        if (last == RecyclerView.NO_POSITION) {
            return -1;
        }

        return mMessagesAdapter.findItemPositionById(id, first, last);
    }

    // Loader interface

    @NonNull
    @Override
    public Loader<AttachmentUploader.Result> onCreateLoader(int id, Bundle args) {
        return new AttachmentUploader.FileUploader(getActivity(), args);
    }

    @Override
    public void onLoadFinished(@NonNull Loader<AttachmentUploader.Result> loader, final AttachmentUploader.Result data) {
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
    public void onLoaderReset(@NonNull Loader<AttachmentUploader.Result> loader) {
    }

    void setProgressIndicator(boolean active) {
        if (!isAdded()) {
            return;
        }
        mRefresher.setRefreshing(active);
    }

    private class UploadProgress implements AttachmentUploader.Progress {

        UploadProgress() {
        }

        public void onStart(final String topicName, final long msgId) {
            // Reload the cursor.
            runMessagesLoader(mTopicName);
        }

        // Returns true to continue the upload, false to cancel.
        public boolean onProgress(final String topicName, final int loaderId, final long msgId,
                                  final long progress, final long total) {
            // DEBUG -- slow down the upload progress.
            /*
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            */
            // debug

            // Check for cancellation.
            Integer oldLoaderId = mMessagesAdapter.getLoaderMapping(msgId);
            if (oldLoaderId == null) {
                mMessagesAdapter.addLoaderMapping(msgId, loaderId);
            } else if (oldLoaderId != loaderId) {
                // Loader id has changed, cancel.
                return false;
            }

            if (!isAdded() || !isVisible() || mTopicName == null || !mTopicName.equals(topicName)) {
                return true;
            }

            Activity activity = getActivity();
            if (activity == null || activity.isDestroyed() || activity.isFinishing()) {
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
