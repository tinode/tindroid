package co.tinode.tindroid;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import co.tinode.tindroid.media.VxCard;
import co.tinode.tinodesdk.ComTopic;
import co.tinode.tinodesdk.PromisedReply;
import co.tinode.tinodesdk.Tinode;
import co.tinode.tinodesdk.Topic;
import co.tinode.tinodesdk.model.Drafty;
import co.tinode.tinodesdk.model.MsgServerInfo;
import co.tinode.tinodesdk.model.PrivateType;
import co.tinode.tinodesdk.model.ServerMessage;
import io.livekit.android.room.Room;
import io.livekit.android.room.participant.Participant;

/**
 * Video conference UI.
 */
public class VCFragment extends Fragment {
    private static final String TAG = "VCFragment";

    private ComTopic<VxCard> mTopic;
    private int mCallSeqID = 0;
    private String mVCEndpoint;
    private VCFragment.InfoListener mTinodeListener;
    private boolean mCallStarted = false;
    // Control buttons: speakerphone, mic, camera.
    private FloatingActionButton mFlipCameraBtn;
    private FloatingActionButton mToggleCameraBtn;
    private FloatingActionButton mToggleMicBtn;

    private VCRoomHandler mRoomHandler;

    private RecyclerView mParticipantsView;

    private VCParticipantsAdapter mAdapter;

    private class InfoListener implements Tinode.EventListener {
        @Override
        public void onInfoMessage(MsgServerInfo info) {
            if (MsgServerInfo.parseWhat(info.what) != MsgServerInfo.What.CALL) {
                // We are only interested in "call" info messages.
                return;
            }
            MsgServerInfo.Event event = MsgServerInfo.parseEvent(info.event);
            switch (event) {
                case VC_TOKEN:
                    handleVCToken(info.payload);
                    break;
                case HANG_UP:
                    handleRemoteHangup(info);
                    break;
                default:
                    break;
            }
        }
    }

    // Check if we have camera and mic permissions.
    private final ActivityResultLauncher<String[]> mMediaPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                for (Map.Entry<String, Boolean> e : result.entrySet()) {
                    if (!e.getValue()) {
                        Log.d(TAG, "The user has disallowed " + e);
                        handleCallClose();
                        return;
                    }
                }
                // All permissions granted.
                startMediaAndSignal();
            });

    public VCFragment() {}

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_vc, container, false);

        mParticipantsView = v.findViewById(R.id.vcParticipants);

        Context ctx = getContext();
        mAdapter = new VCParticipantsAdapter(ctx);

        GridLayoutManager layoutManager = new GridLayoutManager(ctx,2);
        mParticipantsView.setLayoutManager(layoutManager);
        mParticipantsView.setAdapter(mAdapter);

        mFlipCameraBtn = v.findViewById(R.id.flipCameraBtn);
        mToggleCameraBtn = v.findViewById(R.id.toggleCameraBtn);
        mToggleMicBtn = v.findViewById(R.id.toggleMicBtn);

        // Button click handlers: speakerphone on/off, mute/unmute, video/audio-only, hang up.
        mFlipCameraBtn.setOnClickListener(v0 ->
                flipCamera((FloatingActionButton) v0));
        mToggleCameraBtn.setOnClickListener(v2 ->
                toggleMedia((FloatingActionButton) v2, true,
                        R.drawable.ic_videocam, R.drawable.ic_videocam_off));
        mToggleMicBtn.setOnClickListener(v3 ->
                toggleMedia((FloatingActionButton) v3, false,
                        R.drawable.ic_mic, R.drawable.ic_mic_off));

        v.findViewById(R.id.hangupBtn).setOnClickListener(v1 -> handleCallClose());
        return v;
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstance) {
        final Activity activity = requireActivity();
        final Bundle args = getArguments();
        if (args == null) {
            Log.w(TAG, "Call fragment created with no arguments");
            // Reject the call.
            handleCallClose();
            return;
        }

        Tinode tinode = Cache.getTinode();
        String name = args.getString(Const.INTENT_EXTRA_TOPIC);
        // noinspection unchecked
        mTopic = (ComTopic<VxCard>) tinode.getTopic(name);
        mVCEndpoint = (String)tinode.getServerParam("vcEndpoint");
        mCallSeqID = args.getInt(Const.INTENT_EXTRA_SEQ);
        if (!mTopic.isAttached()) {
            mTopic.setListener(new Topic.Listener<VxCard, PrivateType, VxCard, PrivateType>() {
                @Override
                public void onSubscribe(int code, String text) {
                    handleCallStart();
                }
            });
        }

        mTinodeListener = new InfoListener();
        tinode.addListener(mTinodeListener);

        // Set logging verbosity.
        //LiveKit.Companion.setLoggingLevel(LoggingLevel.VERBOSE);

        // Check permissions.
        LinkedList<String> missing = UiUtils.getMissingPermissions(activity,
                new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO});
        if (!missing.isEmpty()) {
            mMediaPermissionLauncher.launch(missing.toArray(new String[]{}));
            return;
        }

        // Got all necessary permissions.
        startMediaAndSignal();
    }

    @Override
    public void onDestroyView() {
        Cache.getTinode().removeListener(mTinodeListener);
        mTopic.setListener(null);

        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onPause() {
        stopMediaAndSignal();
        super.onPause();
    }

    private void enableControls() {
        requireActivity().runOnUiThread(() -> {
            mFlipCameraBtn.setEnabled(true);
            mToggleCameraBtn.setEnabled(true);
            mToggleMicBtn.setEnabled(true);
        });
    }

    private void toggleMedia(FloatingActionButton b, boolean video, @DrawableRes int enabledIcon, int disabledIcon) {
        boolean enabled = video ? mRoomHandler.isCameraEnabled() : mRoomHandler.isMicEnabled();
        if (!video) {
            mRoomHandler.setMicEnabled(!enabled);
        } else {
            mRoomHandler.setCameraEnabled(!enabled);
        }
        b.setImageResource(enabled ? disabledIcon : enabledIcon);
    }

    private void flipCamera(FloatingActionButton b) {
        mRoomHandler.flipCamera();
    }

    private void startMediaAndSignal() {
        final Activity activity = requireActivity();
        if (activity.isFinishing() || activity.isDestroyed()) {
            // We are done. Just quit.
            return;
        }

        handleCallStart();
    }

    // Call initiation.
    private void handleCallStart() {
        //
        if (!mTopic.isAttached() || mCallStarted) {
            // Already started or not attached. wait to attach.
            return;
        }
        Activity activity = requireActivity();
        mCallStarted = true;
        if (mCallSeqID <= 0) {
            // Starting a new VC cal.
            // Send out a call invitation to the peer.
            Map<String, Object> head = new HashMap<>();
            head.put("webrtc", "started");
            // It's a video conference call.
            head.put("vc", true);
            mTopic.publish(Drafty.videoCall(), head).thenApply(
                    new PromisedReply.SuccessListener<ServerMessage>() {
                        @Override
                        public PromisedReply<ServerMessage> onSuccess(ServerMessage result) {
                            if (result.ctrl != null && result.ctrl.code < 300) {
                                int seq = result.ctrl.getIntParam("seq", -1);
                                String token = result.ctrl.getStringParam("token", "");
                                if (seq > 0 & !token.isEmpty()) {
                                    // All good.
                                    mCallSeqID = seq;
                                    joinRoom(token);
                                    return null;
                                }
                            }
                            handleCallClose();
                            return null;
                        }
                    }, new VCFragment.FailureHandler(getActivity()));
        } else {
            // Joining an existing call.
            mTopic.videoCallJoinVC(mCallSeqID);
        }
    }

    // Sends a hang-up notification to the peer and closes the fragment.
    private void handleCallClose() {
        // Close fragment.
        if (mCallSeqID > 0) {
            mTopic.videoCallHangUp(mCallSeqID);
        }
        if (mRoomHandler != null) {
            mRoomHandler.close();
        }

        mCallSeqID = -1;
        final VCActivity activity = (VCActivity) getActivity();
        if (activity != null && !activity.isFinishing() && !activity.isDestroyed()) {
            activity.finishCall();
        }
    }

    private void joinRoom(String token) {
        // Android's "localhost" is "10.0.2.2".
        // TODO: remove.
        String url = mVCEndpoint.replace("localhost", "10.0.2.2");

        mRoomHandler = new VCRoomHandler(url, token, getActivity().getApplication(), mAdapter,
                new VCRoomHandler.VCListener() {
                    @Override
                    public void onParticipants(@NonNull List<Participant> participants) {
                        Log.i(TAG, "participants -> " + participants.toString());

                        Activity activity = getActivity();
                        if (activity == null) {
                            return;
                        }
                        Context ctx = getContext();
                        switch (participants.size()) {
                            case 1: {
                                GridLayoutManager layoutManager = new GridLayoutManager(ctx, 1, GridLayoutManager.HORIZONTAL, false);//GridLayoutManager(ctx,2, );
                                activity.runOnUiThread(() -> {
                                    mParticipantsView.setLayoutManager(layoutManager);
                                });
                                break;
                            }
                            case 2: {
                                GridLayoutManager layoutManager = new GridLayoutManager(ctx, 2, GridLayoutManager.HORIZONTAL, false);//GridLayoutManager(ctx,2, );
                                activity.runOnUiThread(() -> {
                                    mParticipantsView.setLayoutManager(layoutManager);
                                });
                                break;
                            }
                            default: {
                                GridLayoutManager layoutManager = new GridLayoutManager(ctx, 2, GridLayoutManager.VERTICAL, false);//GridLayoutManager(ctx,2, );
                                activity.runOnUiThread(() -> {
                                    mParticipantsView.setLayoutManager(layoutManager);
                                });
                                break;
                            }
                        }
                        mAdapter.update(participants);
                        activity.runOnUiThread(() -> {
                            mAdapter.notifyDataSetChanged();
                        });
                    }
                });
        enableControls();
    }

    private void handleVCToken(Object payload) {
        Map<String, Object> m = (Map<String, Object>) payload;
        String token = (String) m.getOrDefault("token", "");
        if (token.isEmpty()) {
            handleCallClose();
            return;
        }
        joinRoom(token);
    }
    // Cleans up call after receiving a remote hang-up notification.
    private void handleRemoteHangup(MsgServerInfo info) {
        handleCallClose();
    }

    private void stopMediaAndSignal() {
        handleCallClose();
    }

    private class FailureHandler extends UiUtils.ToastFailureListener {
        FailureHandler(Activity activity) {
            super(activity);
        }

        @Override
        public PromisedReply<ServerMessage> onFailure(final Exception err) {
            handleCallClose();
            return super.onFailure(err);
        }
    }
}
