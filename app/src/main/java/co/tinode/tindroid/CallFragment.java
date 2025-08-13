package co.tinode.tindroid;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera1Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.DataChannel;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaSource;
import org.webrtc.MediaStream;
import org.webrtc.MediaStreamTrack;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RendererCommon;
import org.webrtc.RtpReceiver;
import org.webrtc.RtpSender;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.RawRes;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.fragment.app.Fragment;
import co.tinode.tindroid.media.VxCard;
import co.tinode.tinodesdk.ComTopic;
import co.tinode.tinodesdk.PromisedReply;
import co.tinode.tinodesdk.Tinode;
import co.tinode.tinodesdk.Topic;
import co.tinode.tinodesdk.model.Drafty;
import co.tinode.tinodesdk.model.MsgServerInfo;
import co.tinode.tinodesdk.model.ServerMessage;

/**
 * Video call UI: local & remote video views.
 */
public class CallFragment extends Fragment {
    private static final String TAG = "CallFragment";

    // Video mute/unmute events.
    private static final String VIDEO_MUTED_EVENT = "video:muted";
    private static final String VIDEO_UNMUTED_EVENT = "video:unmuted";

    // Camera constants.
    // TODO: hardcoded for now. Consider querying camera for supported values.
    private static final int CAMERA_RESOLUTION_WIDTH = 1024;
    private static final int CAMERA_RESOLUTION_HEIGHT = 720;
    private static final int CAMERA_FPS = 30;

    public enum CallDirection {
        OUTGOING,
        INCOMING,
    }

    private PeerConnectionFactory mPeerConnectionFactory;
    private MediaConstraints mSdpConstraints;
    private VideoCapturer mVideoCapturerAndroid;
    private VideoSource mVideoSource;
    private VideoTrack mLocalVideoTrack;
    private AudioSource mAudioSource;
    private AudioTrack mLocalAudioTrack;
    private PeerConnection mLocalPeer;
    private DataChannel mDataChannel;
    private List<PeerConnection.IceServer> mIceServers;
    private EglBase mRootEglBase;

    // Saved original audio settings.
    private AudioSettings mAudioSettings;

    private CallDirection mCallDirection;
    // If true, the client has received a remote SDP from the peer and has sent a local SDP to the peer.
    private boolean mCallInitialSetupComplete;
    // Stores remote ice candidates until initial call setup is complete.
    private List<IceCandidate> mRemoteIceCandidatesCache;

    // Media state
    private boolean mAudioOff = false;
    private boolean mVideoOff = false;

    // For playing ringing sounds.
    MediaPlayer mMediaPlayer = null;

    // Video (camera views).
    private SurfaceViewRenderer mLocalVideoView;
    private SurfaceViewRenderer mRemoteVideoView;

    // Control buttons: speakerphone, mic, camera.
    private FloatingActionButton mToggleSpeakerphoneBtn;
    private FloatingActionButton mToggleCameraBtn;
    private FloatingActionButton mToggleMicBtn;

    private ConstraintLayout mLayout;
    private TextView mPeerName;
    private ImageView mPeerAvatar;

    private ComTopic<VxCard> mTopic;
    private int mCallSeqID = 0;
    private InfoListener mTinodeListener;
    private boolean mCallStarted = false;
    private boolean mAudioOnly = false;

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

    public CallFragment() {
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_call, container, false);
        mLocalVideoView = v.findViewById(R.id.localView);
        mRemoteVideoView = v.findViewById(R.id.remoteView);

        mToggleSpeakerphoneBtn = v.findViewById(R.id.toggleSpeakerphoneBtn);
        mToggleCameraBtn = v.findViewById(R.id.toggleCameraBtn);
        mToggleMicBtn = v.findViewById(R.id.toggleMicBtn);

        mLayout = v.findViewById(R.id.callMainLayout);

        // Button click handlers: speakerphone on/off, mute/unmute, video/audio-only, hang up.
        mToggleSpeakerphoneBtn.setOnClickListener(v0 ->
                toggleSpeakerphone((FloatingActionButton) v0));
        v.findViewById(R.id.hangupBtn).setOnClickListener(v1 -> handleCallClose());
        mToggleCameraBtn.setOnClickListener(v2 ->
                toggleMedia((FloatingActionButton) v2, true,
                        R.drawable.ic_videocam, R.drawable.ic_videocam_off));
        mToggleMicBtn.setOnClickListener(v3 ->
                toggleMedia((FloatingActionButton) v3, false,
                        R.drawable.ic_mic, R.drawable.ic_mic_off));
        return v;
    }

    @SuppressLint("UnsafeOptInUsageError")
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

        String callStateStr = args.getString(Const.INTENT_EXTRA_CALL_DIRECTION);
        mCallDirection = "incoming".equals(callStateStr) ? CallDirection.INCOMING : CallDirection.OUTGOING;
        if (mCallDirection == CallDirection.INCOMING) {
            mCallSeqID = args.getInt(Const.INTENT_EXTRA_SEQ);
        }

        mAudioOnly = args.getBoolean(Const.INTENT_EXTRA_CALL_AUDIO_ONLY);

        // Save original settings, restore them on exit.
        // Saved original audio settings.
        mAudioSettings = new AudioSettings();
        AudioManager audioManager = TindroidApp.getAudioManager();
        mAudioSettings.audioMode = audioManager.getMode();
        mAudioSettings.microphone = audioManager.isMicrophoneMute();
        mAudioSettings.speakerphone = TindroidApp.isSpeakerphoneOn();

        audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
        boolean speakerphoneOn = !mAudioOnly;
        speakerphoneOn &= TindroidApp.setSpeakerphoneOn(speakerphoneOn);
        mToggleSpeakerphoneBtn.setImageResource(speakerphoneOn ? R.drawable.ic_volume_up : R.drawable.ic_volume_off);

        if (!mTopic.isAttached()) {
            mTopic.setListener(new Topic.Listener<>() {
                @Override
                public void onSubscribe(int code, String text) {
                    handleCallStart();
                }
            });
        }

        mTinodeListener = new InfoListener();
        tinode.addListener(mTinodeListener);

        VxCard pub = mTopic.getPub();
        mPeerAvatar = view.findViewById(R.id.imageAvatar);
        UiUtils.setAvatar(mPeerAvatar, pub, name, false);

        String peerName = pub != null ? pub.fn : null;
        if (TextUtils.isEmpty(peerName)) {
            peerName = getResources().getString(R.string.unknown);
        }
        mPeerName = view.findViewById(R.id.peerName);
        mPeerName.setText(peerName);

        mRemoteIceCandidatesCache = new ArrayList<>();

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

    @SuppressLint("UnsafeOptInUsageError")
    @Override
    public void onDestroyView() {
        stopMediaAndSignal();
        Cache.getTinode().removeListener(mTinodeListener);
        mTopic.setListener(null);

        TindroidApp.setAudioMode(mAudioSettings.audioMode);
        TindroidApp.setMicrophoneMute(mAudioSettings.microphone);
        TindroidApp.setSpeakerphoneOn(mAudioSettings.speakerphone);
        TindroidApp.abandonAudioFocus();

        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        stopSoundEffect();
        super.onDestroy();
    }

    @Override
    public void onPause() {
        stopSoundEffect();
        super.onPause();
    }

    private void enableControls() {
        requireActivity().runOnUiThread(() -> {
            mToggleSpeakerphoneBtn.setEnabled(true);
            mToggleCameraBtn.setEnabled(true);
            mToggleMicBtn.setEnabled(true);
        });
    }

    private static VideoCapturer createCameraCapturer(CameraEnumerator enumerator) {
        final String[] deviceNames = enumerator.getDeviceNames();

        // First, try to find front facing camera
        for (String deviceName : deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);
                if (videoCapturer != null) {
                    return videoCapturer;
                } else {
                    Log.d(TAG, "Failed to create FF camera " + deviceName);
                }
            }
        }

        // Front facing camera not found, try something else
        for (String deviceName : deviceNames) {
            if (!enumerator.isFrontFacing(deviceName)) {
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);

                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }
        }

        return null;
    }

    private void muteVideo() {
        try {
            mVideoCapturerAndroid.stopCapture();
            mLocalVideoView.setVisibility(View.INVISIBLE);
            sendToPeer(VIDEO_MUTED_EVENT);
        } catch (InterruptedException e) {
            Log.d(TAG, e.toString());
        }
    }

    private void unmuteVideo() {
        mVideoCapturerAndroid.startCapture(CAMERA_RESOLUTION_WIDTH, CAMERA_RESOLUTION_HEIGHT, CAMERA_FPS);
        mLocalVideoView.setVisibility(View.VISIBLE);
        sendToPeer(VIDEO_UNMUTED_EVENT);
    }

    // Mute/unmute media.
    @SuppressLint("UnsafeOptInUsageError")
    private void toggleMedia(FloatingActionButton b, boolean video, @DrawableRes int enabledIcon, int disabledIcon) {
        boolean disabled;
        if (video) {
            disabled = !mVideoOff;
            mVideoOff = disabled;
        } else {
            disabled = !mAudioOff;
            mAudioOff = disabled;
        }

        b.setImageResource(disabled ? disabledIcon : enabledIcon);

        if (video) {
            if (disabled) {
                muteVideo();
            } else {
                unmuteVideo();
            }
            return;
        }
        mLocalAudioTrack.setEnabled(!disabled);

        // Need to disable microphone too, otherwise webrtc LocalPeer produces echo.
        TindroidApp.setMicrophoneMute(disabled);

        if (mLocalPeer == null) {
            return;
        }

        for (RtpSender transceiver : mLocalPeer.getSenders()) {
            MediaStreamTrack track = transceiver.track();
            if (track instanceof AudioTrack) {
                track.setEnabled(!disabled);
            }
        }
    }

    @SuppressLint("UnsafeOptInUsageError")
    private void toggleSpeakerphone(FloatingActionButton b) {
        boolean isEnabled = TindroidApp.isSpeakerphoneOn();
        if (TindroidApp.setSpeakerphoneOn(!isEnabled)) {
            b.setImageResource(!isEnabled ? R.drawable.ic_volume_up : R.drawable.ic_volume_off);
        }
    }

    // Initializes media (camera and audio) and notifies the peer (sends "invite" for outgoing,
    // and "accept" for incoming call).
    private void startMediaAndSignal() {
        final Activity activity = requireActivity();
        if (activity.isFinishing() || activity.isDestroyed()) {
            // We are done. Just quit.
            return;
        }

        if (!initIceServers()) {
            Toast.makeText(activity, R.string.video_calls_unavailable, Toast.LENGTH_LONG).show();
            handleCallClose();
        }

        initVideos();

        // Initialize PeerConnectionFactory globals.
        PeerConnectionFactory.InitializationOptions initializationOptions =
                PeerConnectionFactory.InitializationOptions.builder(activity)
                        .createInitializationOptions();
        PeerConnectionFactory.initialize(initializationOptions);

        // Create a new PeerConnectionFactory instance - using Hardware encoder and decoder.
        PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();
        PeerConnectionFactory.Builder pcfBuilder = PeerConnectionFactory.builder()
                .setOptions(options);
        DefaultVideoEncoderFactory defaultVideoEncoderFactory = new DefaultVideoEncoderFactory(
                mRootEglBase.getEglBaseContext(), true, true);
        DefaultVideoDecoderFactory defaultVideoDecoderFactory = new DefaultVideoDecoderFactory(
                mRootEglBase.getEglBaseContext());
        pcfBuilder.setVideoEncoderFactory(defaultVideoEncoderFactory)
                .setVideoDecoderFactory(defaultVideoDecoderFactory);

        mPeerConnectionFactory = pcfBuilder.createPeerConnectionFactory();

        // Create MediaConstraints - Will be useful for specifying video and audio constraints.
        MediaConstraints audioConstraints = new MediaConstraints();

        // Create an AudioSource instance
        mAudioSource = mPeerConnectionFactory.createAudioSource(audioConstraints);
        mLocalAudioTrack = mPeerConnectionFactory.createAudioTrack("101", mAudioSource);
        if (mAudioOff) {
            mLocalAudioTrack.setEnabled(false);
        }

        // Create a VideoCapturer instance.
        mVideoCapturerAndroid = createCameraCapturer(new Camera1Enumerator(false));

        // Create a VideoSource instance
        if (mVideoCapturerAndroid != null) {
            SurfaceTextureHelper surfaceTextureHelper =
                    SurfaceTextureHelper.create("CaptureThread", mRootEglBase.getEglBaseContext());
            mVideoSource = mPeerConnectionFactory.createVideoSource(mVideoCapturerAndroid.isScreencast());
            mVideoCapturerAndroid.initialize(surfaceTextureHelper, activity, mVideoSource.getCapturerObserver());
        }

        mLocalVideoTrack = mPeerConnectionFactory.createVideoTrack("100", mVideoSource);

        mVideoOff = mAudioOnly;
        if (mVideoCapturerAndroid != null && !mVideoOff) {
            // Only start video in video calls (in audio-only calls video may be turned on later).
            mVideoCapturerAndroid.startCapture(CAMERA_RESOLUTION_WIDTH, CAMERA_RESOLUTION_HEIGHT, CAMERA_FPS);
        }

        // VideoRenderer is ready => add the renderer to the VideoTrack.
        mLocalVideoTrack.addSink(mLocalVideoView);
        mLocalVideoView.setMirror(true);
        mRemoteVideoView.setMirror(false);

        handleCallStart();
    }

    // Stops media and concludes the call (sends "hang-up" to the peer).
    private void stopMediaAndSignal() {
        // Clean up.
        if (mLocalPeer != null) {
            mLocalPeer.close();
            mLocalPeer = null;
        }
        if (mRemoteVideoView != null) {
            mRemoteVideoView.release();
            mRemoteVideoView = null;
        }
        if (mLocalVideoTrack != null) {
            mLocalVideoTrack.removeSink(mLocalVideoView);
            mLocalVideoTrack = null;
        }
        if (mVideoCapturerAndroid != null) {
            try {
                mVideoCapturerAndroid.stopCapture();
            } catch (InterruptedException e) {
                Log.e(TAG, "Failed to stop camera", e);
            }
            mVideoCapturerAndroid = null;
        }
        if (mLocalVideoView != null) {
            mLocalVideoView.release();
            mLocalVideoView = null;
        }

        if (mAudioSource != null) {
            mAudioSource.dispose();
            mAudioSource = null;
        }
        if (mVideoSource != null) {
            mVideoSource.dispose();
            mVideoSource = null;
        }
        if (mRootEglBase != null) {
            mRootEglBase.release();
            mRootEglBase = null;
        }

        handleCallClose();
    }

    private void initVideos() {
        mRootEglBase = EglBase.create();

        mRemoteVideoView.init(mRootEglBase.getEglBaseContext(), null);
        mRemoteVideoView.setEnableHardwareScaler(true);
        mRemoteVideoView.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_BALANCED);
        mRemoteVideoView.setZOrderMediaOverlay(false);
        mRemoteVideoView.setVisibility(mAudioOnly ? View.INVISIBLE : View.VISIBLE);

        mLocalVideoView.init(mRootEglBase.getEglBaseContext(), null);
        mLocalVideoView.setEnableHardwareScaler(true);
        mLocalVideoView.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT);
        mLocalVideoView.setZOrderMediaOverlay(true);
        mLocalVideoView.setVisibility(mAudioOnly ? View.INVISIBLE : View.VISIBLE);

        if (mAudioOnly) {
            mToggleCameraBtn.setImageResource(R.drawable.ic_videocam_off);
        }
    }

    private boolean initIceServers() {
        mIceServers = new ArrayList<>();
        try {
            //noinspection unchecked
            List<Map<String, Object>> iceServersConfig =
                    (List<Map<String, Object>>) Cache.getTinode().getServerParam("iceServers");
            if (iceServersConfig == null) {
                return false;
            }

            for (Map<String, Object> server : iceServersConfig) {
                //noinspection unchecked
                List<String> urls = (List<String>) server.get("urls");
                if (urls == null || urls.isEmpty()) {
                    Log.w(TAG, "Invalid ICE server config: no URLs");
                    continue;
                }
                PeerConnection.IceServer.Builder builder = PeerConnection.IceServer.builder(urls);
                String username = (String) server.get("username");
                if (username != null) {
                    builder.setUsername(username);
                }
                String credential = (String) server.get("credential");
                if (credential != null) {
                    builder.setPassword(credential);
                }
                mIceServers.add(builder.createIceServer());
            }
        } catch (ClassCastException | NullPointerException ex) {
            Log.w(TAG, "Unexpected format of server-provided ICE config", ex);
            return false;
        }
        return !mIceServers.isEmpty();
    }

    private void addRemoteIceCandidateToCache(IceCandidate candidate) {
        mRemoteIceCandidatesCache.add(candidate);
    }

    private void drainRemoteIceCandidatesCache() {
        Log.d(TAG, "Draining remote ICE candidate cache: " + mRemoteIceCandidatesCache.size() + " elements.");
        for (IceCandidate candidate: mRemoteIceCandidatesCache) {
            mLocalPeer.addIceCandidate(candidate);
        }
        mRemoteIceCandidatesCache.clear();
    }

    // Peers have exchanged their local and remote SDPs.
    private void initialSetupComplete() {
        mCallInitialSetupComplete = true;
        drainRemoteIceCandidatesCache();
        rearrangePeerViews(requireActivity(), false);
        enableControls();
    }

    // Sends a hang-up notification to the peer and closes the fragment.
    private void handleCallClose() {
        stopSoundEffect();

        // Close fragment.
        if (mCallSeqID > 0) {
            mTopic.videoCallHangUp(mCallSeqID);
        }

        mCallSeqID = -1;
        final CallActivity activity = (CallActivity) getActivity();
        if (activity != null && !activity.isFinishing() && !activity.isDestroyed()) {
            activity.finishCall();
        }
        Cache.endCallInProgress();
    }

    // Call initiation.
    private void handleCallStart() {
        if (!mTopic.isAttached() || mCallStarted) {
            // Already started or not attached. wait to attach.
            return;
        }
        Activity activity = requireActivity();
        mCallStarted = true;
        switch (mCallDirection) {
            case OUTGOING:
                // Send out a call invitation to the peer.
                Map<String, Object> head = new HashMap<>();
                head.put("webrtc", "started");
                // Is audio-only?
                head.put(Tinode.CALL_AUDIO_ONLY, mAudioOnly);
                mTopic.publish(Drafty.videoCall(), head).thenApply(
                        new PromisedReply.SuccessListener<>() {
                            @Override
                            public PromisedReply<ServerMessage> onSuccess(ServerMessage result) {
                                if (result.ctrl != null && result.ctrl.code < 300) {
                                    int seq = result.ctrl.getIntParam("seq", -1);
                                    if (seq > 0) {
                                        // All good.
                                        mCallSeqID = seq;
                                        Cache.setCallActive(mTopic.getName(), seq);
                                        return null;
                                    }
                                }
                                handleCallClose();
                                return null;
                            }
                        }, new FailureHandler(getActivity()));
                rearrangePeerViews(activity, false);
                break;
            case INCOMING:
                // The callee (we) has accepted the call. Notify the caller.
                rearrangePeerViews(activity, false);
                mTopic.videoCallAccept(mCallSeqID);
                Cache.setCallConnected();
                break;
            default:
                break;
        }
    }

    // Sends a SDP offer to the peer.
    private void handleSendOffer(SessionDescription sd) {
        mTopic.videoCallOffer(mCallSeqID, new SDPAux(sd.type.canonicalForm(), sd.description));
    }

    // Sends a SDP answer to the peer.
    private void handleSendAnswer(SessionDescription sd) {
        mTopic.videoCallAnswer(mCallSeqID, new SDPAux(sd.type.canonicalForm(), sd.description));
    }

    private void sendToPeer(String msg) {
        if (mDataChannel != null) {
            mDataChannel.send(new DataChannel.Buffer(
                    ByteBuffer.wrap(msg.getBytes(StandardCharsets.UTF_8)), false));
        } else {
            Log.w(TAG, "Data channel is null. Peer will not receive the message: '" + msg + "'");
        }
    }

    // Data channel observer for receiving video mute/unmute events.
    private class DCObserver implements DataChannel.Observer {
        private final DataChannel mChannel;
        public DCObserver(DataChannel chan) {
            super();
            mChannel = chan;
        }
        @Override
        public void onBufferedAmountChange(long l) {
        }

        @Override
        public void onStateChange() {
            Log.d(TAG, "onStateChange: remote data channel state: " + mChannel.state().toString());
            switch (mChannel.state()) {
                case OPEN:
                    sendToPeer(!mVideoOff && mVideoSource.state() == MediaSource.State.LIVE ?
                            VIDEO_UNMUTED_EVENT : VIDEO_MUTED_EVENT);
                    break;
                case CLOSED:
                    break;
            }
        }

        @Override
        public void onMessage(DataChannel.Buffer buffer) {
            ByteBuffer data = buffer.data;
            byte[] bytes = new byte[data.remaining()];
            data.get(bytes);
            final String event = new String(bytes);
            Log.d(TAG, "onMessage: got message" + event);
            switch (event) {
                case VIDEO_MUTED_EVENT:
                    rearrangePeerViews(requireActivity(), false);
                    break;
                case VIDEO_UNMUTED_EVENT:
                    rearrangePeerViews(requireActivity(), true);
                    break;
                default:
                    break;
            }
        }
    }

    // Creates and initializes a peer connection.
    private void createPeerConnection(boolean withDataChannel) {
        PeerConnection.RTCConfiguration rtcConfig =
                new PeerConnection.RTCConfiguration(mIceServers);
        // TCP candidates are only useful when connecting to a server that supports
        // ICE-TCP.
        rtcConfig.tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED;
        rtcConfig.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE;
        rtcConfig.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE;
        rtcConfig.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY;
        // Use ECDSA encryption.
        rtcConfig.keyType = PeerConnection.KeyType.ECDSA;

        // DO NOT change to UNIFIED_PLAN. It crashes.
        rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.PLAN_B;
        mLocalPeer = mPeerConnectionFactory.createPeerConnection(rtcConfig, new PeerConnection.Observer() {
            @Override
            public void onIceCandidate(IceCandidate iceCandidate) {
                // Send ICE candidate to the peer.
                handleIceCandidateEvent(iceCandidate);
            }

            @Override
            public void onAddStream(MediaStream mediaStream) {
                // Received remote stream.
                Activity activity = requireActivity();
                if (activity.isFinishing() || activity.isDestroyed()) {
                    return;
                }

                // Add remote media stream to the renderer.
                if (!mediaStream.videoTracks.isEmpty()) {
                    final VideoTrack videoTrack = mediaStream.videoTracks.get(0);
                    activity.runOnUiThread(() -> {
                        try {
                            mRemoteVideoView.setVisibility(View.VISIBLE);
                            videoTrack.addSink(mRemoteVideoView);
                        } catch (Exception e) {
                            handleCallClose();
                        }
                    });
                }
            }

            @Override
            public void onSignalingChange(PeerConnection.SignalingState signalingState) {
                Log.d(TAG, "onSignalingChange() called with: signalingState = [" + signalingState + "]");
                if (signalingState == PeerConnection.SignalingState.CLOSED) {
                    handleCallClose();
                }
            }

            @Override
            public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {
                Log.d(TAG, "onIceConnectionChange() called with: iceConnectionState = [" + iceConnectionState + "]");
                switch (iceConnectionState) {
                    case CLOSED:
                    case FAILED:
                        handleCallClose();
                        break;
                }
            }

            @Override
            public void onIceConnectionReceivingChange(boolean b) {
                Log.d(TAG, "onIceConnectionReceivingChange() called with: b = [" + b + "]");
            }

            @Override
            public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {
                Log.d(TAG, "onIceGatheringChange() called with: iceGatheringState = [" + iceGatheringState + "]");
            }

            @Override
            public void onIceCandidatesRemoved(IceCandidate[] iceCandidates) {
                Log.d(TAG, "onIceCandidatesRemoved() called with: iceCandidates = [" +
                        Arrays.toString(iceCandidates) + "]");
            }

            @Override
            public void onRemoveStream(MediaStream mediaStream) {
                Log.d(TAG, "onRemoveStream() called with: mediaStream = [" + mediaStream + "]");
            }

            @Override
            public void onDataChannel(DataChannel channel) {
                Log.d(TAG, "onDataChannel(): state: " + channel.state());
                channel.registerObserver(new DCObserver(channel));
                mDataChannel = channel;
            }

            @Override
            public void onRenegotiationNeeded() {
                Log.d(TAG, "onRenegotiationNeeded() called");

                if (CallFragment.this.mCallDirection == CallDirection.INCOMING &&
                        !CallFragment.this.mCallInitialSetupComplete) {
                    // Do not send an offer yet as
                    // - We are still in initial setup phase.
                    // - The caller is supposed to send us an offer.
                    return;
                }
                if (mLocalPeer.getSenders().isEmpty()) {
                    // This is a recvonly connection for now. Wait until it turns sendrecv.
                    Log.d(TAG, "PeerConnection is recvonly. Waiting for sendrecv.");
                    return;
                }
                mSdpConstraints = new MediaConstraints();
                mSdpConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
                if (!mAudioOnly) {
                    mSdpConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"));
                }
                mLocalPeer.createOffer(new CustomSdpObserver("localCreateOffer") {
                    @Override
                    public void onCreateSuccess(SessionDescription sessionDescription) {
                        super.onCreateSuccess(sessionDescription);
                        Log.d("onCreateSuccess", "setting local desc - setLocalDescription");
                        mLocalPeer.setLocalDescription(new CustomSdpObserver("localSetLocalDesc"),
                                sessionDescription);
                        handleSendOffer(sessionDescription);
                    }
                }, mSdpConstraints);
            }

            @Override
            public void onAddTrack(RtpReceiver rtpReceiver, MediaStream[] mediaStreams) {
                Log.d(TAG, "onAddTrack() called with: rtpReceiver = [" + rtpReceiver +
                        "], mediaStreams = [" + Arrays.toString(mediaStreams) + "]");
            }
        });

        if (withDataChannel) {
            DataChannel.Init i = new DataChannel.Init();
            i.ordered = true;
            mDataChannel = mLocalPeer.createDataChannel("events", i);
            mDataChannel.registerObserver(new DCObserver(mDataChannel));
        }
        // Create a local media stream and attach it to the peer connection.
        MediaStream stream = mPeerConnectionFactory.createLocalMediaStream("102");
        stream.addTrack(mLocalAudioTrack);
        stream.addTrack(mLocalVideoTrack);
        mLocalPeer.addStream(stream);
    }

    private void handleVideoCallAccepted() {
        Log.d(TAG, "handling video call accepted");
        Activity activity = requireActivity();
        if (activity.isDestroyed() || activity.isFinishing()) {
            return;
        }

        stopSoundEffect();
        rearrangePeerViews(activity, false);

        createPeerConnection(true);
        Cache.setCallConnected();
    }

    // Handles remote SDP offer received from the peer,
    // creates a local peer connection and sends an answer to the peer.
    private void handleVideoOfferMsg(@NonNull MsgServerInfo info) {
        // Incoming call.
        if (info.payload == null) {
            Log.e(TAG, "Received RTC offer with an empty payload. Quitting");
            handleCallClose();
            return;
        }

        // Data channel should be created by the peer. Not creating one.
        createPeerConnection(false);
        //noinspection unchecked
        Map<String, Object> m = (Map<String, Object>) info.payload;
        String type = (String) m.getOrDefault("type", "");
        String sdp = (String) m.getOrDefault("sdp", "");

        //noinspection ConstantConditions
        mLocalPeer.setRemoteDescription(new CustomSdpObserver("localSetRemote"),
                new SessionDescription(SessionDescription.Type.fromCanonicalForm(type.toLowerCase()), sdp));

        mLocalPeer.createAnswer(new CustomSdpObserver("localCreateAns") {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                super.onCreateSuccess(sessionDescription);
                mLocalPeer.setLocalDescription(new CustomSdpObserver("localSetLocal"), sessionDescription);

                handleSendAnswer(sessionDescription);

                CallFragment.this.initialSetupComplete();
            }
        }, new MediaConstraints());
    }

    // Passes remote SDP received from the peer to the peer connection.
    private void handleVideoAnswerMsg(@NonNull MsgServerInfo info) {
        if (info.payload == null) {
            Log.e(TAG, "Received RTC answer with an empty payload. Quitting. ");
            handleCallClose();
            return;
        }
        //noinspection unchecked
        Map<String, Object> m = (Map<String, Object>) info.payload;
        String type = (String) m.getOrDefault("type", "");
        String sdp = (String) m.getOrDefault("sdp", "");

        //noinspection ConstantConditions
        mLocalPeer.setRemoteDescription(new CustomSdpObserver("localSetRemote"),
                new SessionDescription(SessionDescription.Type.fromCanonicalForm(type.toLowerCase()), sdp));
        initialSetupComplete();
    }

    // Adds remote ICE candidate data received from the peer to the peer connection.
    private void handleNewICECandidateMsg(@NonNull MsgServerInfo info) {
        if (info.payload == null) {
            // Skip.
            Log.e(TAG, "Received ICE candidate message an empty payload. Skipping.");
            return;
        }
        //noinspection unchecked
        Map<String, Object> m = (Map<String, Object>) info.payload;
        String sdpMid = (String) m.getOrDefault("sdpMid", "");
        //noinspection ConstantConditions
        int sdpMLineIndex = (int) m.getOrDefault("sdpMLineIndex", 0);
        String sdp = (String) m.getOrDefault("candidate", "");
        if (sdp == null || sdp.isEmpty()) {
            // Skip.
            Log.e(TAG, "Invalid ICE candidate with an empty candidate SDP" + info);
            return;
        }

        IceCandidate candidate = new IceCandidate(sdpMid, sdpMLineIndex, sdp);
        if (mCallInitialSetupComplete) {
            mLocalPeer.addIceCandidate(candidate);
        } else {
            addRemoteIceCandidateToCache(candidate);
        }
    }

    // Sends a local ICE candidate to the other party.
    private void handleIceCandidateEvent(IceCandidate candidate) {
        mTopic.videoCallICECandidate(mCallSeqID,
                new IceCandidateAux("candidate", candidate.sdpMLineIndex, candidate.sdpMid, candidate.sdp));
    }

    // Cleans up call after receiving a remote hang-up notification.
    private void handleRemoteHangup(MsgServerInfo info) {
        handleCallClose();
    }

    private void playSoundEffect(@RawRes int effectId) {
        if (mMediaPlayer == null) {
            mMediaPlayer = MediaPlayer.create(getContext(), effectId);
            mMediaPlayer.setLooping(true);
            mMediaPlayer.start();
        }
    }

    private synchronized void stopSoundEffect() {
        if (mMediaPlayer != null) {
            mMediaPlayer.stop();
            mMediaPlayer.release();
            mMediaPlayer = null;
        }
    }

    private void rearrangePeerViews(final Activity activity, boolean remoteVideoLive) {
        activity.runOnUiThread(() -> {
            if (activity.isFinishing() || activity.isDestroyed()) {
                return;
            }
            if (remoteVideoLive) {
                ConstraintSet cs = new ConstraintSet();
                cs.clone(mLayout);
                cs.removeFromVerticalChain(R.id.peerName);
                cs.connect(R.id.peerName, ConstraintSet.BOTTOM, R.id.callControlsPanel, ConstraintSet.TOP, 0);
                cs.setHorizontalBias(R.id.peerName, 0.05f);

                cs.applyTo(mLayout);
                mPeerName.setElevation(8);

                mPeerAvatar.setVisibility(View.INVISIBLE);
                mRemoteVideoView.setVisibility(View.VISIBLE);
            } else {
                ConstraintSet cs = new ConstraintSet();
                cs.clone(mLayout);
                cs.removeFromVerticalChain(R.id.peerName);
                cs.connect(R.id.peerName, ConstraintSet.BOTTOM, R.id.imageAvatar, ConstraintSet.TOP, 0);
                cs.setHorizontalBias(R.id.peerName, 0.5f);
                cs.applyTo(mLayout);
                mPeerAvatar.setVisibility(View.VISIBLE);
                if (mRemoteVideoView != null) {
                    mRemoteVideoView.setVisibility(View.INVISIBLE);
                }
            }
        });
    }

    // Auxiliary class to facilitate serialization of SDP data.
    // Don't convert to record is it's not serialized properly.
    static class SDPAux implements Serializable {
        public final String type;
        public final String sdp;

        SDPAux(String type, String sdp) {
            this.type = type;
            this.sdp = sdp;
        }
    }

    // Auxiliary class to facilitate serialization of the ICE candidate data.
    // Don't convert to record is it's not serialized properly.
    static class IceCandidateAux implements Serializable {
        public final String type;
        public final int sdpMLineIndex;
        public final String sdpMid;
        public final String candidate;

        IceCandidateAux(String type, int sdpMLineIndex, String sdpMid, String candidate) {
            this.type = type;
            this.sdpMLineIndex = sdpMLineIndex;
            this.sdpMid = sdpMid;
            this.candidate = candidate;
        }
    }

    // Listens for incoming call-related info messages.
    private class InfoListener implements Tinode.EventListener {
        @Override
        public void onInfoMessage(MsgServerInfo info) {
            if (MsgServerInfo.parseWhat(info.what) != MsgServerInfo.What.CALL) {
                // We are only interested in "call" info messages.
                return;
            }
            MsgServerInfo.Event event = MsgServerInfo.parseEvent(info.event);
            switch (event) {
                case ACCEPT:
                    handleVideoCallAccepted();
                    break;
                case ANSWER:
                    handleVideoAnswerMsg(info);
                    break;
                case ICE_CANDIDATE:
                    handleNewICECandidateMsg(info);
                    break;
                case HANG_UP:
                    handleRemoteHangup(info);
                    break;
                case OFFER:
                    handleVideoOfferMsg(info);
                    break;
                case RINGING:
                    playSoundEffect(R.raw.call_out);
                    break;
                default:
                    break;
            }
        }
    }

    private static class CustomSdpObserver implements SdpObserver {
        private String tag;

        CustomSdpObserver(String logTag) {
            tag = getClass().getCanonicalName();
            this.tag = this.tag + " " + logTag;
        }

        @Override
        public void onCreateSuccess(SessionDescription sessionDescription) {
            Log.d(tag, "onCreateSuccess() called with: sessionDescription = [" + sessionDescription + "]");
        }

        @Override
        public void onSetSuccess() {
            Log.d(tag, "onSetSuccess() called");
        }

        @Override
        public void onCreateFailure(String s) {
            Log.d(tag, "onCreateFailure() called with: s = [" + s + "]");
        }

        @Override
        public void onSetFailure(String s) {
            Log.d(tag, "onSetFailure() called with: s = [" + s + "]");
        }
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

    private static class AudioSettings {
        boolean speakerphone;
        boolean microphone;
        int audioMode;
    }
}
