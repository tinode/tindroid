package co.tinode.tindroid;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.NotificationManager;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.media.audiofx.AcousticEchoCanceler;
import android.media.audiofx.AutomaticGainControl;
import android.media.audiofx.NoiseSuppressor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.util.Pair;
import android.view.GestureDetector;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.core.content.ContextCompat;
import androidx.core.view.ContentInfoCompat;
import androidx.core.view.MenuHost;
import androidx.core.view.MenuProvider;
import androidx.core.view.OnReceiveContentListener;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.work.Data;
import androidx.work.Operation;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import co.tinode.tindroid.db.BaseDb;
import co.tinode.tindroid.db.SqlStore;
import co.tinode.tindroid.db.StoredTopic;
import co.tinode.tindroid.format.SendForwardedFormatter;
import co.tinode.tindroid.format.SendReplyFormatter;
import co.tinode.tindroid.media.VxCard;
import co.tinode.tindroid.widgets.MovableActionButton;
import co.tinode.tindroid.widgets.WaveDrawable;
import co.tinode.tinodesdk.ComTopic;
import co.tinode.tinodesdk.PromisedReply;
import co.tinode.tinodesdk.Storage;
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

/**
 * Fragment handling message display and message sending.
 */
public class MessagesFragment extends Fragment implements MenuProvider {
    private static final String TAG = "MessageFragment";
    private static final int MESSAGES_TO_LOAD = 24;

    private static final String[] SUPPORTED_MIME_TYPES = new String[]{"image/*"};

    static final String MESSAGE_TO_SEND = "messageText";
    static final String MESSAGE_TEXT_ACTION = "messageTextAction";
    static final String MESSAGE_QUOTED = "quotedDrafty";
    static final String MESSAGE_QUOTED_SEQ_ID = "quotedSeqID";

    static final int ZONE_CANCEL = 0;
    static final int ZONE_LOCK = 1;

    // Number of milliseconds between audio samples for recording visualization.
    static final int AUDIO_SAMPLING = 100;
    // Minimum duration of an audio recording in milliseconds.
    static final int MIN_DURATION = 2000;
    // Maximum duration of an audio recording in milliseconds.
    static final int MAX_DURATION = 600_000;

    private ComTopic<VxCard> mTopic;

    private LinearLayoutManager mMessageViewLayoutManager;
    private RecyclerView mRecyclerView;
    private MessagesAdapter mMessagesAdapter;
    private SwipeRefreshLayout mRefresher;

    private FloatingActionButton mGoToLatest;

    private String mTopicName = null;
    private String mMessageToSend = null;
    private boolean mChatInvitationShown = false;

    private UiUtils.MsgAction mTextAction = UiUtils.MsgAction.NONE;
    private int mQuotedSeqID = -1;
    private Drafty mQuote = null;
    private Drafty mContentToForward = null;
    private Drafty mForwardSender = null;

    private MediaRecorder mAudioRecorder = null;
    private File mAudioRecord = null;
    // Timestamp when the recording was started.
    private long mRecordingStarted = 0;
    // Duration of audio recording.
    private int mAudioRecordDuration = 0;
    private AcousticEchoCanceler mEchoCanceler;
    private NoiseSuppressor mNoiseSuppressor;
    private AutomaticGainControl mGainControl;

    // Playback of audio recording.
    private MediaPlayer mAudioPlayer = null;
    // Preview or audio amplitudes.
    private AudioSampler mAudioSampler = null;

    private final Handler mAudioSamplingHandler = new Handler(Looper.getMainLooper());

    private int mVisibleSendPanel = R.id.sendMessagePanel;

    private PromisedReply.FailureListener<ServerMessage> mFailureListener;

    private final ActivityResultLauncher<String> mFileOpenerRequestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
        // Check if permission is granted.
        if (isGranted) {
            openFileSelector(requireActivity(), false);
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
                openMediaSelector(requireActivity(), false);
            });

    private final ActivityResultLauncher<String[]> mAudioRecorderPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                for (Map.Entry<String,Boolean> e : result.entrySet()) {
                    if (!e.getValue()) {
                        // Some permission is missing. Disable audio recording button.
                        Activity activity = requireActivity();
                        if (activity.isFinishing() || activity.isDestroyed()) {
                            return;
                        }
                        activity.findViewById(R.id.audioRecorder).setEnabled(false);
                        return;
                    }
                }
            });

    private final ActivityResultLauncher<String> mFilePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri == null) {
                    return;
                }

                final MessageActivity activity = (MessageActivity) requireActivity();
                if (activity.isFinishing() || activity.isDestroyed()) {
                    return;
                }

                final Bundle args = new Bundle();
                args.putParcelable(AttachmentHandler.ARG_LOCAL_URI, uri);
                args.putString(AttachmentHandler.ARG_OPERATION, AttachmentHandler.ARG_OPERATION_FILE);
                args.putString(Const.INTENT_EXTRA_TOPIC, mTopicName);
                // Show attachment preview.
                activity.showFragment(MessageActivity.FRAGMENT_FILE_PREVIEW, args, true);
            });

    private final ActivityResultLauncher<String> mNotificationsPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (!isGranted) {
                    final MessageActivity activity = (MessageActivity) requireActivity();
                    if (activity.isFinishing() || activity.isDestroyed()) {
                        return;
                    }
                    Toast.makeText(activity, R.string.permission_missing, Toast.LENGTH_LONG).show();
                }
            });

    private final ActivityResultLauncher<Object> mMediaPickerLauncher =
            registerForActivityResult(new MediaPickerContract(), uri -> {
                if (uri == null) {
                    return;
                }

                final MessageActivity activity = (MessageActivity) requireActivity();
                if (activity.isFinishing() || activity.isDestroyed()) {
                    return;
                }

                String mimeType = activity.getContentResolver().getType(uri);
                boolean isVideo = mimeType != null && mimeType.startsWith("video");

                final Bundle args = new Bundle();
                args.putParcelable(AttachmentHandler.ARG_LOCAL_URI, uri);
                args.putString(AttachmentHandler.ARG_FILE_NAME, uri.getLastPathSegment());
                args.putString(AttachmentHandler.ARG_FILE_PATH, uri.getPath());
                args.putString(AttachmentHandler.ARG_OPERATION,
                        isVideo ? AttachmentHandler.ARG_OPERATION_VIDEO :
                                AttachmentHandler.ARG_OPERATION_IMAGE);
                args.putString(Const.INTENT_EXTRA_TOPIC, mTopicName);

                // Show attachment preview.
                activity.showFragment(isVideo ? MessageActivity.FRAGMENT_VIEW_VIDEO :
                        MessageActivity.FRAGMENT_VIEW_IMAGE, args, true);
            });

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_messages, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstance) {

        final MessageActivity activity = (MessageActivity) requireActivity();

        ((MenuHost) activity).addMenuProvider(this, getViewLifecycleOwner(),
                Lifecycle.State.RESUMED);

        mGoToLatest = activity.findViewById(R.id.goToLatest);
        mGoToLatest.setOnClickListener(v -> scrollToBottom(true));

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
        mRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                RecyclerView.Adapter adapter = mRecyclerView.getAdapter();
                if (adapter == null) {
                    return;
                }
                int itemCount = adapter.getItemCount();
                int pos = mMessageViewLayoutManager.findLastVisibleItemPosition();
                if (itemCount - pos < 4) {
                    ((MessagesAdapter) adapter).loadNextPage();
                }
            }

            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                int pos = mMessageViewLayoutManager.findFirstVisibleItemPosition();
                if (dy > 5 && pos > 2) {
                    mGoToLatest.show();
                } else if (dy < -5 || pos == 0) {
                    mGoToLatest.hide();
                }
            }
        });

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

        AppCompatImageButton audio = setupAudioForms(activity, view);

        AppCompatImageButton send = view.findViewById(R.id.chatSendButton);
        send.setOnClickListener(v -> sendText(activity));
        view.findViewById(R.id.chatForwardButton).setOnClickListener(v -> sendText(activity));
        AppCompatImageButton doneEditing = view.findViewById(R.id.chatEditDoneButton);
        doneEditing.setOnClickListener(v -> sendText(activity));

        // Send image button
        view.findViewById(R.id.attachImage).setOnClickListener(v -> openMediaSelector(activity, true));

        // Send file button
        view.findViewById(R.id.attachFile).setOnClickListener(v -> openFileSelector(activity, true));

        // Cancel reply preview button.
        view.findViewById(R.id.cancelPreview).setOnClickListener(v -> cancelPreview(activity));
        view.findViewById(R.id.cancelForwardingPreview).setOnClickListener(v -> cancelPreview(activity));

        EditText editor = view.findViewById(R.id.editMessage);
        ViewCompat.setOnReceiveContentListener(editor, SUPPORTED_MIME_TYPES, new StickerReceiver());

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

                // Show either [send] or [record audio] or [done editing] button.
                if (mTextAction == UiUtils.MsgAction.EDIT) {
                    doneEditing.setVisibility(View.VISIBLE);
                    audio.setVisibility(View.INVISIBLE);
                    send.setVisibility(View.INVISIBLE);
                } else if (charSequence.length() > 0) {
                    doneEditing.setVisibility(View.INVISIBLE);
                    audio.setVisibility(View.INVISIBLE);
                    send.setVisibility(View.VISIBLE);
                } else {
                    doneEditing.setVisibility(View.INVISIBLE);
                    audio.setVisibility(View.VISIBLE);
                    send.setVisibility(View.INVISIBLE);
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
            mTopic.setMeta(new MsgSetMeta.Builder<VxCard, PrivateType>()
                            .with(new MetaSetSub(mTopic.getName(), am.getGiven())).build())
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
                                    boolean fatal = failure.getBoolean(AttachmentHandler.ARG_FATAL, false);
                                    SqlStore store = BaseDb.getInstance().getStore();
                                    Storage.Message msg = store.getMessageById(msgId);
                                    if (msg != null && BaseDb.isUnsentSeq(msg.getSeqId())) {
                                        if (fatal) {
                                            store.msgDiscard(mTopic, msgId);
                                        }
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

        final MessageActivity activity = (MessageActivity) requireActivity();

        Bundle args = getArguments();
        if (args != null) {
            mTopicName = args.getString(Const.INTENT_EXTRA_TOPIC);
        }

        if (mTopicName != null) {
            mTopic = (ComTopic<VxCard>) Cache.getTinode().getTopic(mTopicName);
            runMessagesLoader(mTopicName);
        } else {
            mTopic = null;
        }

        mRefresher.setRefreshing(false);

        updateFormValues();
        activity.sendNoteRead(0);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            NotificationManager nm = (NotificationManager) activity.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null && !nm.areNotificationsEnabled()) {
                mNotificationsPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS);
            }
        }
    }

    void runMessagesLoader(String topicName) {
        if (mMessagesAdapter != null) {
            mMessagesAdapter.resetContent(topicName);
        }
    }

    private void setSendPanelVisible(Activity activity, int id) {
        if (mVisibleSendPanel == id) {
            return;
        }
        activity.findViewById(id).setVisibility(View.VISIBLE);
        activity.findViewById(mVisibleSendPanel).setVisibility(View.GONE);
        mVisibleSendPanel = id;
    }

    @SuppressLint("ClickableViewAccessibility")
    private AppCompatImageButton setupAudioForms(AppCompatActivity activity, View view) {
        // Audio recorder button.
        MovableActionButton mab = view.findViewById(R.id.audioRecorder);
        // Lock button
        ImageView lockFab = view.findViewById(R.id.lockAudioRecording);
        // Lock button
        ImageView deleteFab = view.findViewById(R.id.deleteAudioRecording);

        // Play button in locked recording panel.
        AppCompatImageButton playButton = view.findViewById(R.id.playRecording);
        // Pause button in locked recording panel when playing back.
        AppCompatImageButton pauseButton = view.findViewById(R.id.pauseRecording);
        // Stop recording button in locked recording panel.
        AppCompatImageButton stopButton = view.findViewById(R.id.stopRecording);
        // ImageView with waveform visualization.
        ImageView wave = view.findViewById(R.id.audioWave);
        wave.setBackground(new WaveDrawable(getResources(), 5));
        wave.setOnTouchListener((v, event) -> {
            if (mAudioRecordDuration > 0 && mAudioPlayer != null && event.getAction() == MotionEvent.ACTION_DOWN) {
                float fraction = event.getX() / v.getWidth();
                mAudioPlayer.seekTo((int) (fraction * mAudioRecordDuration));
                ((WaveDrawable) v.getBackground()).seekTo(fraction);
                return true;
            }
            return false;
        });
        ImageView waveShort = view.findViewById(R.id.audioWaveShort);
        waveShort.setBackground(new WaveDrawable(getResources()));
        // Recording timer.
        TextView timerView = view.findViewById(R.id.duration);
        TextView timerShortView = view.findViewById(R.id.durationShort);
        // Launch audio recorder.
        AppCompatImageButton audio = view.findViewById(R.id.chatAudioButton);
        final Runnable visualizer = new Runnable() {
            @Override
            public void run() {
                if (mAudioRecorder != null) {
                    int x = mAudioRecorder.getMaxAmplitude();
                    mAudioSampler.put(x);
                    if (mVisibleSendPanel == R.id.recordAudioPanel) {
                        ((WaveDrawable) wave.getBackground()).put(x);
                        timerView.setText(UiUtils.millisToTime((int) (SystemClock.uptimeMillis() - mRecordingStarted)));
                    } else if (mVisibleSendPanel == R.id.recordAudioShortPanel) {
                        ((WaveDrawable) waveShort.getBackground()).put(x);
                        timerShortView.setText(UiUtils.millisToTime((int) (SystemClock.uptimeMillis() - mRecordingStarted)));
                    }
                    mAudioSamplingHandler.postDelayed(this, AUDIO_SAMPLING);
                    ((MessageActivity) activity).sendRecordingProgress(true);
                }
            }
        };

        mab.setConstraintChecker((newPos, startPos, buttonRect, parentRect) -> {
            // Constrain button moves to strictly vertical UP or horizontal LEFT (no diagonal).
            float dX = Math.min(0, newPos.x - startPos.x);
            float dY = Math.min(0, newPos.y - startPos.y);

            if (Math.abs(dX) > Math.abs(dY)) {
                // Horizontal move.
                newPos.x = Math.max(parentRect.left, newPos.x);
                newPos.y = startPos.y;
            } else {
                // Vertical move.
                newPos.x = startPos.x;
                newPos.y = Math.max(parentRect.top, newPos.y);
            }
            return newPos;
        });
        mab.setOnActionListener(new MovableActionButton.ActionListener() {
            @Override
            public boolean onUp(float x, float y) {
                if (mAudioRecorder != null) {
                    releaseAudio(true);
                    sendAudio(activity);
                }

                mab.setVisibility(View.INVISIBLE);
                lockFab.setVisibility(View.GONE);
                deleteFab.setVisibility(View.GONE);
                audio.setVisibility(View.VISIBLE);
                setSendPanelVisible(activity, R.id.sendMessagePanel);
                return true;
            }

            @Override
            public boolean onZoneReached(int id) {
                mab.performHapticFeedback(android.os.Build.VERSION.SDK_INT > Build.VERSION_CODES.Q ?
                        (id == ZONE_CANCEL ? HapticFeedbackConstants.REJECT : HapticFeedbackConstants.CONFIRM) :
                        HapticFeedbackConstants.CONTEXT_CLICK
                );
                mab.setVisibility(View.INVISIBLE);
                lockFab.setVisibility(View.GONE);
                deleteFab.setVisibility(View.GONE);
                audio.setVisibility(View.VISIBLE);
                if (id == ZONE_CANCEL) {
                    if (mAudioRecorder != null) {
                        releaseAudio(false);
                    }
                    setSendPanelVisible(activity, R.id.sendMessagePanel);
                    releaseAudio(false);
                } else {
                    playButton.setVisibility(View.GONE);
                    stopButton.setVisibility(View.VISIBLE);
                    setSendPanelVisible(activity, R.id.recordAudioPanel);
                }
                return true;
            }
        });
        GestureDetector gd = new GestureDetector(getContext(), new GestureDetector.SimpleOnGestureListener() {
            public void onLongPress(@NonNull MotionEvent e) {
                if (!UiUtils.isPermissionGranted(activity, Manifest.permission.RECORD_AUDIO)) {
                    mAudioRecorderPermissionLauncher.launch(new String[] {
                            Manifest.permission.RECORD_AUDIO, Manifest.permission.MODIFY_AUDIO_SETTINGS
                    });
                    return;
                }

                if (mAudioRecorder == null) {
                    initAudioRecorder(activity);
                }
                try {
                    mAudioRecorder.start();
                    mRecordingStarted = SystemClock.uptimeMillis();
                    visualizer.run();
                } catch (RuntimeException ex) {
                    Log.e(TAG, "Failed to start audio recording", ex);
                    Toast.makeText(activity, R.string.audio_recording_failed, Toast.LENGTH_SHORT).show();
                    return;
                }

                mab.setVisibility(View.VISIBLE);
                lockFab.setVisibility(View.VISIBLE);
                deleteFab.setVisibility(View.VISIBLE);
                audio.setVisibility(View.INVISIBLE);
                mab.requestFocus();
                setSendPanelVisible(activity, R.id.recordAudioShortPanel);
                // Cancel zone on the left.
                int x = mab.getLeft();
                int y = mab.getTop();
                int width = mab.getWidth();
                int height = mab.getHeight();
                mab.addActionZone(ZONE_CANCEL, new Rect(x - (int) (width * 1.5), y,
                        x - (int) (width * 0.5), y + height));
                // Lock zone above.
                mab.addActionZone(ZONE_LOCK, new Rect(x, y - (int) (height * 1.5),
                        x + width, y - (int) (height * 0.5)));
                MotionEvent motionEvent = MotionEvent.obtain(
                        e.getDownTime(), e.getEventTime(),
                        MotionEvent.ACTION_DOWN,
                        e.getRawX(),
                        e.getRawY(),
                        0
                );
                mab.dispatchTouchEvent(motionEvent);
            }
        });
        // Ignore the warning: click detection is not needed here.
        audio.setOnTouchListener((v, event) -> {
            int action = event.getAction();
            if (mab.getVisibility() == View.VISIBLE) {
                if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    audio.setPressed(false);
                }
                return mab.dispatchTouchEvent(event);
            }
            return gd.onTouchEvent(event);
        });

        view.findViewById(R.id.deleteRecording).setOnClickListener(v -> {
            WaveDrawable wd = (WaveDrawable) wave.getBackground();
            wd.release();
            releaseAudio(false);
            setSendPanelVisible(activity, R.id.sendMessagePanel);
        });
        playButton.setOnClickListener(v -> {
            pauseButton.setVisibility(View.VISIBLE);
            playButton.setVisibility(View.GONE);
            WaveDrawable wd = (WaveDrawable) wave.getBackground();
            wd.start();
            initAudioPlayer(wd, playButton, pauseButton);
            mAudioPlayer.start();
        });
        pauseButton.setOnClickListener(v -> {
            playButton.setVisibility(View.VISIBLE);
            pauseButton.setVisibility(View.GONE);
            WaveDrawable wd = (WaveDrawable) wave.getBackground();
            wd.stop();
            mAudioPlayer.pause();
        });
        stopButton.setOnClickListener(v -> {
            playButton.setVisibility(View.VISIBLE);
            v.setVisibility(View.GONE);
            releaseAudio(true);
            WaveDrawable wd = (WaveDrawable) wave.getBackground();
            wd.reset();
            wd.setDuration(mAudioRecordDuration);
            wd.put(mAudioSampler.obtain(96));
            wd.seekTo(0f);
        });
        view.findViewById(R.id.chatSendAudio).setOnClickListener(v -> {
            releaseAudio(true);
            sendAudio(activity);
            setSendPanelVisible(activity, R.id.sendMessagePanel);
        });

        return audio;
    }

    private void updateFormValues() {
        if (!isAdded()) {
            return;
        }

        final MessageActivity activity = (MessageActivity) requireActivity();
        if (activity.isFinishing() || activity.isDestroyed()) {
            return;
        }

        if (mTopic == null) {
            // Default view when the topic is not available.
            activity.findViewById(R.id.notReadable).setVisibility(View.VISIBLE);
            activity.findViewById(R.id.notReadableNote).setVisibility(View.VISIBLE);
            setSendPanelVisible(activity, R.id.sendMessageDisabled);
            UiUtils.setupToolbar(activity, null, mTopicName, false, null, false);
            return;
        }

        UiUtils.setupToolbar(activity, mTopic.getPub(), mTopicName,
                mTopic.getOnline(), mTopic.getLastSeen(), mTopic.isDeleted());

        Acs acs = mTopic.getAccessMode();
        if (acs == null || !acs.isModeDefined()) {
            return;
        }

        activity.findViewById(R.id.replyPreviewWrapper).setVisibility(View.GONE);
        if (mTopic.isReader()) {
            activity.findViewById(R.id.notReadable).setVisibility(View.GONE);
        } else {
            activity.findViewById(R.id.notReadable).setVisibility(View.VISIBLE);
            activity.findViewById(R.id.notReadableNote).setVisibility(
                    acs.isReader(Acs.Side.GIVEN) ? View.GONE : View.VISIBLE);
        }

        if (!mTopic.isWriter() || mTopic.isBlocked() || mTopic.isDeleted()) {
            setSendPanelVisible(activity, R.id.sendMessageDisabled);
        } else if (mContentToForward != null) {
            showContentToForward(activity, mForwardSender, mContentToForward);
        } else {
            Subscription peer = mTopic.getPeer();
            boolean isJoiner = peer != null && peer.acs != null && peer.acs.isJoiner(Acs.Side.WANT);
            AcsHelper missing = peer != null && peer.acs != null ? peer.acs.getMissing() : new AcsHelper();
            if (isJoiner && (missing.isReader() || missing.isWriter())) {
                setSendPanelVisible(activity, R.id.peersMessagingDisabled);
            } else {
                if (!TextUtils.isEmpty(mMessageToSend)) {
                    EditText input = activity.findViewById(R.id.editMessage);
                    if (input.getText().length() == 0) {
                        input.append(mMessageToSend);
                    }

                    mMessageToSend = null;
                }
                setSendPanelVisible(activity, R.id.sendMessagePanel);
            }
        }

        if (acs.isJoiner(Acs.Side.GIVEN) && acs.getExcessive().toString().contains("RW")) {
            showChatInvitationDialog();
        }
    }

    private void scrollToBottom(boolean smooth) {
        if (smooth) {
            mRecyclerView.smoothScrollToPosition(0);
        } else {
            mRecyclerView.scrollToPosition(0);
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        releaseAudio(false);

        final MessageActivity activity = (MessageActivity) requireActivity();

        AudioManager audioManager = (AudioManager) activity.getSystemService(Activity.AUDIO_SERVICE);
        audioManager.setMode(AudioManager.MODE_NORMAL);
        audioManager.setSpeakerphoneOn(false);

        Bundle args = getArguments();
        if (args != null) {
            args.putString(Const.INTENT_EXTRA_TOPIC, mTopicName);
            // Save the text in the send field.
            String draft = ((EditText) activity.findViewById(R.id.editMessage)).getText().toString().trim();
            args.putString(MESSAGE_TO_SEND, draft);
            args.putString(MESSAGE_TEXT_ACTION, mTextAction.name());
            args.putInt(MESSAGE_QUOTED_SEQ_ID, mQuotedSeqID);
            args.putSerializable(MESSAGE_QUOTED, mQuote);
            args.putSerializable(ForwardToFragment.CONTENT_TO_FORWARD, mContentToForward);
            args.putSerializable(ForwardToFragment.FORWARDING_FROM_USER, mForwardSender);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        // Close cursor and release MediaPlayer.
        if (mMessagesAdapter != null) {
            mMessagesAdapter.releaseAudio();
            mMessagesAdapter.resetContent(null);
        }
    }

    @Override
    public void onPrepareMenu(@NonNull Menu menu) {
        MenuProvider.super.onPrepareMenu(menu);
        if (mTopic != null) {
            if (mTopic.isDeleted()) {
                final Activity activity = requireActivity();
                menu.clear();
                activity.getMenuInflater().inflate(R.menu.menu_topic_deleted, menu);
            } else {
                menu.findItem(R.id.action_unmute).setVisible(mTopic.isMuted());
                menu.findItem(R.id.action_mute).setVisible(!mTopic.isMuted());

                menu.findItem(R.id.action_delete).setVisible(mTopic.isOwner());
                menu.findItem(R.id.action_leave).setVisible(!mTopic.isOwner());

                menu.findItem(R.id.action_archive).setVisible(!mTopic.isArchived());
                menu.findItem(R.id.action_unarchive).setVisible(mTopic.isArchived());

                boolean callsEnabled = mTopic.isP2PType() &&
                        Cache.getTinode().getServerParam("iceServers") != null;
                menu.findItem(R.id.action_video_call).setVisible(callsEnabled);
                menu.findItem(R.id.action_audio_call).setVisible(callsEnabled);
            }
        }
    }

    @Override
    public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.menu_topic, menu);
    }

    @Override
    public boolean onMenuItemSelected(@NonNull MenuItem item) {
        final Activity activity = requireActivity();

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
            if (mTopic.isDeleted()) {
                mTopic.delete(true);
                Intent intent = new Intent(activity, ChatsActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(intent);
                activity.finish();
            } else {
                showDeleteTopicConfirmationDialog(activity, id == R.id.action_delete);
            }
            return true;
        } else if (id == R.id.action_offline) {
            Cache.getTinode().reconnectNow(true, false, false);
            return true;
        }

        return false;
    }

    void setRefreshing(boolean active) {
        if (!isAdded()) {
            return;
        }
        mRefresher.setRefreshing(active);
    }

    private void initAudioRecorder(Activity activity) {
        if (mAudioRecord != null) {
            mAudioRecord.delete();
            mAudioRecord = null;
        }

        mAudioRecorder = new MediaRecorder();
        mAudioRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mAudioRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mAudioRecorder.setMaxDuration(MAX_DURATION); // 10 minutes.
        mAudioRecorder.setAudioEncodingBitRate(24_000);
        mAudioRecorder.setAudioSamplingRate(16_000);
        mAudioRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);

        if (AcousticEchoCanceler.isAvailable()) {
            mEchoCanceler = AcousticEchoCanceler.create(MediaRecorder.AudioSource.MIC);
        }
        if (NoiseSuppressor.isAvailable()) {
            mNoiseSuppressor = NoiseSuppressor.create(MediaRecorder.AudioSource.MIC);
        }
        if (AutomaticGainControl.isAvailable()) {
            mGainControl = AutomaticGainControl.create(MediaRecorder.AudioSource.MIC);
        }

        activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        try {
            mAudioRecord = File.createTempFile("audio", ".m4a", activity.getCacheDir());
            mAudioRecorder.setOutputFile(mAudioRecord.getAbsolutePath());
            mAudioRecorder.prepare();
            mAudioSampler = new AudioSampler();
        } catch (IOException ex) {
            activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            Log.w(TAG, "Failed to initialize audio recording", ex);
            Toast.makeText(activity, R.string.audio_recording_failed, Toast.LENGTH_SHORT).show();
            mAudioRecorder.release();
            mAudioRecorder = null;
            mAudioSampler = null;
        }
    }

    private synchronized void initAudioPlayer(WaveDrawable waveDrawable, View play, View pause) {
        if (mAudioPlayer != null) {
            return;
        }

        final MessageActivity activity = (MessageActivity) requireActivity();

        AudioManager audioManager = (AudioManager) activity.getSystemService(Activity.AUDIO_SERVICE);
        audioManager.setMode(AudioManager.MODE_IN_CALL);
        audioManager.setSpeakerphoneOn(true);

        mAudioPlayer = new MediaPlayer();
        mAudioPlayer.setOnCompletionListener(mp -> {
            waveDrawable.reset();
            pause.setVisibility(View.GONE);
            play.setVisibility(View.VISIBLE);
        });
        mAudioPlayer.setAudioAttributes(
                new AudioAttributes.Builder().setLegacyStreamType(AudioManager.STREAM_VOICE_CALL).build());
        try {
            mAudioPlayer.setDataSource(mAudioRecord.getAbsolutePath());
            mAudioPlayer.prepare();
            if (AutomaticGainControl.isAvailable()) {
                AutomaticGainControl agc = AutomaticGainControl.create(mAudioPlayer.getAudioSessionId());
                if (agc != null) {
                    // Even when isAvailable returns true, create() may still return null.
                    agc.setEnabled(true);
                }
            }
        } catch (SecurityException | IOException | IllegalStateException ex) {
            Log.e(TAG, "Unable to play recording", ex);
            Toast.makeText(requireContext(), R.string.unable_to_play_audio, Toast.LENGTH_SHORT).show();
            mAudioPlayer = null;
        }
    }

    private void releaseAudio(boolean keepRecord) {
        final MessageActivity activity = (MessageActivity) requireActivity();

        activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        if (!keepRecord && mAudioRecord != null) {
            mAudioRecord.delete();
            mAudioRecord = null;
            mAudioRecordDuration = 0;
        } else if (mRecordingStarted != 0) {
            mAudioRecordDuration = (int) (SystemClock.uptimeMillis() - mRecordingStarted);
        }
        mRecordingStarted = 0;

        if (mAudioPlayer != null) {
            mAudioPlayer.stop();
            mAudioPlayer.reset();
            mAudioPlayer.release();
            mAudioPlayer = null;
        }

        if (mAudioRecorder != null) {
            try {
                mAudioRecorder.stop();
            } catch (RuntimeException ignored) {}
            mAudioRecorder.release();
            mAudioRecorder = null;
        }

        if (mEchoCanceler != null) {
            mEchoCanceler.release();
            mEchoCanceler = null;
        }

        if (mNoiseSuppressor != null) {
            mNoiseSuppressor.release();
            mNoiseSuppressor = null;
        }

        if (mGainControl != null) {
            mGainControl.release();
            mGainControl = null;
        }
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
        mChatInvitationShown = true;

        final Activity activity = requireActivity();

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
                response = mTopic.setMeta(new MsgSetMeta.Builder<VxCard,PrivateType>()
                        .with(new MetaSetSub(mode)).build());
                if (mTopic.isP2PType()) {
                    // For P2P topics change 'given' permission of the peer too.
                    // In p2p topics the other user has the same name as the topic.
                    response = response.thenApply(new PromisedReply.SuccessListener<ServerMessage>() {
                        @Override
                        public PromisedReply<ServerMessage> onSuccess(ServerMessage result) {
                            return mTopic.setMeta(new MsgSetMeta.Builder<VxCard,PrivateType>()
                                    .with(new MetaSetSub(mTopic.getName(), mode)).build());
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
                HashMap<String,Object> head = new HashMap<>();
                head.put("mime", Drafty.MIME_TYPE);
                Cache.getTinode().publish(Tinode.TOPIC_SYS, msg, head, null);
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
                        activity.runOnUiThread(() -> updateFormValues());
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
            updateFormValues();
        } else {
            mMessagesAdapter.notifyDataSetChanged();
        }
    }

    private void openFileSelector(@NonNull Activity activity, boolean checkPermissions) {
        if (activity.isFinishing() || activity.isDestroyed()) {
            return;
        }

        if (checkPermissions) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU &&
                    !UiUtils.isPermissionGranted(activity, Manifest.permission.READ_EXTERNAL_STORAGE)) {
                mFileOpenerRequestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE);
                return;
            }
        }

        try {
            mFilePickerLauncher.launch("*/*");
        } catch (ActivityNotFoundException ex) {
            Toast.makeText(activity, R.string.action_failed, Toast.LENGTH_SHORT).show();
            Log.w(TAG, "Unable to start file picker", ex);
        }
    }

    private void openMediaSelector(@NonNull final Activity activity, boolean checkPermissions) {
        if (activity.isFinishing() || activity.isDestroyed()) {
            return;
        }

        if (checkPermissions) {
            LinkedList<String> permissions = new LinkedList<>();
            permissions.add(Manifest.permission.CAMERA);
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            } else {
                permissions.add(Manifest.permission.READ_MEDIA_VIDEO);
                permissions.add(Manifest.permission.READ_MEDIA_IMAGES);
            }
            LinkedList<String> missing = UiUtils.getMissingPermissions(activity, permissions.toArray(new String[]{}));
            if (!missing.isEmpty()) {
                mImagePickerRequestPermissionLauncher.launch(missing.toArray(new String[]{}));
                return;
            }
        }

        mMediaPickerLauncher.launch(null);
    }

    private boolean sendMessage(Drafty content, int seqId, boolean isReplacement) {
        MessageActivity activity = (MessageActivity) requireActivity();
        boolean done = activity.sendMessage(content, seqId, isReplacement);
        if (done) {
            scrollToBottom(false);
        }
        return done;
    }

    private void sendText(@NonNull Activity activity) {
        if (activity.isFinishing() || activity.isDestroyed()) {
            return;
        }
        final EditText inputField = activity.findViewById(R.id.editMessage);
        if (inputField == null) {
            return;
        }

        if (mContentToForward != null) {
            if (sendMessage(mForwardSender.appendLineBreak().append(mContentToForward), -1, false)) {
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
            boolean isReplacement = false;
            if (mTextAction == UiUtils.MsgAction.EDIT) {
                isReplacement = true;
            } else if (mQuote != null) {
                msg = mQuote.append(msg);
            }
            if (sendMessage(msg, mQuotedSeqID, isReplacement)) {
                // Message is successfully queued, clear text from the input field and redraw the list.
                inputField.getText().clear();
                if (mQuotedSeqID > 0) {
                    mTextAction = UiUtils.MsgAction.NONE;
                    mQuotedSeqID = -1;
                    mQuote = null;
                    activity.findViewById(R.id.replyPreviewWrapper).setVisibility(View.GONE);
                }
            }
        }
    }

    private void sendAudio(@NonNull AppCompatActivity activity) {
        if (activity.isFinishing() || activity.isDestroyed()) {
            return;
        }

        Bundle args = getArguments();
        if (args == null) {
            return;
        }

        if (mAudioRecordDuration < MIN_DURATION) {
            return;
        }

        byte[] preview = mAudioSampler.obtain(96);
        args.putByteArray(AttachmentHandler.ARG_PREVIEW, preview);
        args.putParcelable(AttachmentHandler.ARG_LOCAL_URI, Uri.fromFile(mAudioRecord));
        args.putString(AttachmentHandler.ARG_FILE_PATH, mAudioRecord.getAbsolutePath());
        args.putInt(AttachmentHandler.ARG_DURATION, mAudioRecordDuration);
        args.putString(AttachmentHandler.ARG_OPERATION, AttachmentHandler.ARG_OPERATION_AUDIO);
        args.putString(AttachmentHandler.ARG_TOPIC_NAME, mTopicName);
        AttachmentHandler.enqueueMsgAttachmentUploadRequest(activity, AttachmentHandler.ARG_OPERATION_AUDIO, args);
    }

    private void cancelPreview(Activity activity) {
        if (activity.isFinishing() || activity.isDestroyed()) {
            return;
        }

        mQuotedSeqID = -1;
        mQuote = null;
        mContentToForward = null;
        mForwardSender = null;

        activity.findViewById(R.id.replyPreviewWrapper).setVisibility(View.GONE);
        activity.findViewById(R.id.forwardMessagePanel).setVisibility(View.GONE);
        activity.findViewById(R.id.sendMessagePanel).setVisibility(View.VISIBLE);
        if (mTextAction == UiUtils.MsgAction.EDIT) {
            ((EditText) activity.findViewById(R.id.editMessage)).setText("");
            activity.findViewById(R.id.chatEditDoneButton).setVisibility(View.INVISIBLE);
            activity.findViewById(R.id.chatAudioButton).setVisibility(View.VISIBLE);
        }

        mTextAction = UiUtils.MsgAction.NONE;
    }

    void startEditing(Activity activity, String original, Drafty quote, int seq) {
        handleQuotedText(activity, UiUtils.MsgAction.EDIT, original, quote, seq);
    }

    void showReply(Activity activity, Drafty quote, int seq) {
        handleQuotedText(activity, UiUtils.MsgAction.REPLY, null, quote, seq);
    }

    private void handleQuotedText(Activity activity, UiUtils.MsgAction action,
                                  String original, Drafty quote, int seq) {
        mQuotedSeqID = seq;
        mQuote = quote;
        mContentToForward = null;
        mForwardSender = null;

        activity.findViewById(R.id.forwardMessagePanel).setVisibility(View.GONE);
        activity.findViewById(R.id.sendMessagePanel).setVisibility(View.VISIBLE);
        activity.findViewById(R.id.replyPreviewWrapper).setVisibility(View.VISIBLE);
        if (!TextUtils.isEmpty(original)) {
            EditText editText = activity.findViewById(R.id.editMessage);
            // Two steps: clear field, then append to move cursor to the end.
            editText.setText("");
            editText.append(original);
            editText.requestFocus();
            activity.findViewById(R.id.chatAudioButton).setVisibility(View.INVISIBLE);
            activity.findViewById(R.id.chatSendButton).setVisibility(View.INVISIBLE);
            activity.findViewById(R.id.chatEditDoneButton).setVisibility(View.VISIBLE);
        } else {
            activity.findViewById(R.id.chatAudioButton).setVisibility(View.VISIBLE);
            activity.findViewById(R.id.chatSendButton).setVisibility(View.INVISIBLE);
            activity.findViewById(R.id.chatEditDoneButton).setVisibility(View.INVISIBLE);
            if (mTextAction == UiUtils.MsgAction.EDIT) {
                ((EditText)activity.findViewById(R.id.editMessage)).setText("");
            }
        }
        TextView previewHolder = activity.findViewById(R.id.contentPreview);
        previewHolder.setText(quote.format(new SendReplyFormatter(previewHolder)));
        mTextAction = action;
    }

    private void showContentToForward(Activity activity, Drafty sender, Drafty content) {
        mTextAction = UiUtils.MsgAction.FORWARD;
        mQuotedSeqID = -1;
        mQuote = null;

        activity.findViewById(R.id.sendMessagePanel).setVisibility(View.GONE);
        TextView previewHolder = activity.findViewById(R.id.forwardedContentPreview);
        content = new Drafty().append(sender).appendLineBreak().append(content.preview(Const.QUOTED_REPLY_LENGTH));
        previewHolder.setText(content.format(new SendForwardedFormatter(previewHolder)));
        activity.findViewById(R.id.forwardMessagePanel).setVisibility(View.VISIBLE);
    }

    void topicChanged(String topicName, boolean reset) {
        boolean changed = (mTopicName == null || !mTopicName.equals(topicName));
        mTopicName = topicName;
        if (mTopicName != null) {
            //noinspection unchecked
            mTopic = (ComTopic<VxCard>) Cache.getTinode().getTopic(mTopicName);
        } else {
            mTopic = null;
        }

        if (changed || reset) {
            Bundle args = getArguments();
            if (args != null) {
                mMessageToSend = args.getString(MESSAGE_TO_SEND);
                args.remove(MESSAGE_TO_SEND);

                if (changed) {
                    String textAction = args.getString(MESSAGE_TEXT_ACTION);
                    mTextAction = TextUtils.isEmpty(textAction) ? UiUtils.MsgAction.NONE :
                            UiUtils.MsgAction.valueOf(textAction);
                    mQuotedSeqID = args.getInt(MESSAGE_QUOTED_SEQ_ID);
                    mQuote = (Drafty) args.getSerializable(MESSAGE_QUOTED);
                    mContentToForward = (Drafty) args.getSerializable(ForwardToFragment.CONTENT_TO_FORWARD);
                    mForwardSender = (Drafty) args.getSerializable(ForwardToFragment.FORWARDING_FROM_USER);

                    // Clear used arguments.
                    args.remove(MESSAGE_TEXT_ACTION);
                    args.remove(MESSAGE_QUOTED_SEQ_ID);
                    args.remove(MESSAGE_QUOTED);
                    args.remove(ForwardToFragment.CONTENT_TO_FORWARD);
                    args.remove(ForwardToFragment.FORWARDING_FROM_USER);
                }
            }
        }

        updateFormValues();
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

    private class StickerReceiver implements OnReceiveContentListener {
        @Nullable
        @Override
        public ContentInfoCompat onReceiveContent(@NonNull View view, @NonNull ContentInfoCompat payload) {
            Pair<ContentInfoCompat, ContentInfoCompat> split = payload.partition(item -> item.getUri() != null);

            final MessageActivity activity = (MessageActivity) requireActivity();
            if (split.first != null) {
                // Handle posted URIs.
                ClipData data = split.first.getClip();
                if (data.getItemCount() > 0) {
                    Uri stickerUri = data.getItemAt(0).getUri();
                    Bundle args = new Bundle();
                    args.putParcelable(AttachmentHandler.ARG_LOCAL_URI, stickerUri);
                    args.putString(AttachmentHandler.ARG_OPERATION, AttachmentHandler.ARG_OPERATION_IMAGE);
                    args.putString(AttachmentHandler.ARG_TOPIC_NAME, mTopicName);

                    Operation op = AttachmentHandler.enqueueMsgAttachmentUploadRequest(activity,
                            AttachmentHandler.ARG_OPERATION_IMAGE, args);
                    if (op != null) {
                        op.getResult().addListener(() -> {
                                    if (activity.isFinishing() || activity.isDestroyed()) {
                                        return;
                                    }
                                    activity.syncAllMessages(true);
                                    notifyDataSetChanged(false);
                            }, ContextCompat.getMainExecutor(activity));
                    }
                }
            }

            // Return content we did not handle.
            return split.second;
        }
    }

    // Class for generating audio preview from a stream of amplitudes of unknown length.
    private static class AudioSampler {
        private final static int VISUALIZATION_BARS = 128;
        private final float[] mSamples;
        private final float[] mScratchBuff;
        // The index of a bucket being filled.
        private int mBucketIndex;
        // Number of samples per bucket in mScratchBuff.
        private int mAggregate;
        // Number of samples added the the current bucket.
        private int mSamplesPerBucket;

        AudioSampler() {
            mSamples = new float[VISUALIZATION_BARS * 2];
            mScratchBuff = new float[VISUALIZATION_BARS];
            mBucketIndex = 0;
            mSamplesPerBucket = 0;
            mAggregate = 1;
        }

        public void put(int val) {
            // Fill out the main buffer first.
            if (mAggregate == 1) {
                if (mBucketIndex < mSamples.length) {
                    mSamples[mBucketIndex] = val;
                    mBucketIndex++;
                    return;
                }
                compact();
            }

            // Check if the current bucket is full.
            if (mSamplesPerBucket == mAggregate) {
                // Normalize the bucket.
                mScratchBuff[mBucketIndex] = mScratchBuff[mBucketIndex] / (float) mSamplesPerBucket;
                mBucketIndex++;
                mSamplesPerBucket = 0;
            }
            // Check if scratch buffer is full.
            if (mBucketIndex == mScratchBuff.length) {
                compact();
            }
            mScratchBuff[mBucketIndex] += val;
            mSamplesPerBucket++;
        }

        // Get the count of available samples in the main buffer + scratch buffer.
        private int length() {
            if (mAggregate == 1) {
                // Only the main buffer is available.
                return mBucketIndex;
            }
            // Completely filled main buffer + partially filled scratch buffer.
            return mSamples.length + mBucketIndex + 1;
        }

        // Get bucket content at the given index from the main + scratch buffer.
        private float getAt(int index) {
            // Index into the main buffer.
            if (index < mSamples.length) {
                return mSamples[index];
            }
            // Index into scratch buffer.
            index -= mSamples.length;
            if (index < mBucketIndex) {
                return mScratchBuff[index];
            }
            // Last partially filled bucket in the scratch buffer.
            return mScratchBuff[index] / mSamplesPerBucket;
        }

        public byte[] obtain(int dstCount) {
            // We can only return as many as we have.
            float[] dst = new float[dstCount];
            int srcCount = length();
            // Resampling factor. Couple be lower or higher than 1.
            float factor = (float) srcCount / dstCount;
            float max = -1;
            // src = 100, dst = 200, factor = 0.5
            // src = 200, dst = 100, factor = 2.0
            for (int i = 0; i < dstCount; i++) {
                int lo = (int) (i * factor); // low bound;
                int hi = (int) ((i + 1) * factor); // high bound;
                if (hi == lo) {
                    dst[i] = getAt(lo);
                } else {
                    float amp = 0f;
                    for (int j = lo; j < hi; j++) {
                        amp += getAt(j);
                    }
                    dst[i] = Math.max(0, amp / (hi - lo));
                }
                max = Math.max(dst[i], max);
            }

            byte[] result = new byte[dst.length];
            if (max > 0) {
                for (int i = 0; i < dst.length; i++) {
                    result[i] = (byte) (100f * dst[i] / max);
                }
            }

            return result;
        }

        // Downscale the amplitudes 2x.
        private void compact() {
            int len = VISUALIZATION_BARS / 2;
            // Donwsample the main buffer: two consecutive samples make one new sample.
            for (int i = 0; i < len; i ++) {
                mSamples[i] = (mSamples[i * 2] + mSamples[i * 2 + 1]) * 0.5f;
            }
            // Copy scratch buffer to the upper half the the main buffer.
            System.arraycopy(mScratchBuff, 0, mSamples, len, len);
            // Clear the scratch buffer.
            Arrays.fill(mScratchBuff, 0f);
            // Double the number of samples per bucket.
            mAggregate *= 2;
            // Reset scratch counters.
            mBucketIndex = 0;
            mSamplesPerBucket = 0;
        }
    }
}
