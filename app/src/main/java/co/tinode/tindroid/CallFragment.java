package co.tinode.tindroid;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
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
import co.tinode.tinodesdk.model.PrivateType;
import co.tinode.tinodesdk.model.ServerMessage;

/**
 * Video call UI: local & remote video views.
 */
public class CallFragment extends Fragment {
    private static final String TAG = "CallFragment";

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
    private List<PeerConnection.IceServer> mIceServers;
    private EglBase mRootEglBase;

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

    private ConstraintLayout mLayout;
    private TextView mPeerName;
    private ImageView mPeerAvatar;

    private ComTopic<VxCard> mTopic;
    private int mCallSeqID = 0;
    private InfoListener mTinodeListener;
    private boolean mCallStarted = false;

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

        mLayout = v.findViewById(R.id.callMainLayout);

        AudioManager audioManager = (AudioManager) inflater.getContext().getSystemService(Context.AUDIO_SERVICE);
        audioManager.setMode(AudioManager.MODE_IN_CALL);
        audioManager.setSpeakerphoneOn(true);

        // Button click handlers: speakerphone on/off, mute/unmute, video/audio-only, hang up.
        v.findViewById(R.id.toggleSpeakerphoneBtn).setOnClickListener(v0 ->
                toggleSpeakerphone((FloatingActionButton) v0));
        v.findViewById(R.id.hangupBtn).setOnClickListener(v1 -> handleCallClose());
        v.findViewById(R.id.toggleCameraBtn).setOnClickListener(v2 ->
                toggleMedia((FloatingActionButton) v2, true,
                        R.drawable.ic_videocam, R.drawable.ic_videocam_off));
        v.findViewById(R.id.toggleMicBtn).setOnClickListener(v3 ->
                toggleMedia((FloatingActionButton) v3, false,
                        R.drawable.ic_mic, R.drawable.ic_mic_off));
        return v;
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstance) {
        final Activity activity = getActivity();
        final Bundle args = getArguments();
        if (args == null || activity == null) {
            Log.w(TAG, "Call fragment created with no arguments");
            // Reject the call.
            handleCallClose();
            return;
        }

        Tinode tinode = Cache.getTinode();
        String name = args.getString("topic");
        // noinspection unchecked
        mTopic = (ComTopic<VxCard>) tinode.getTopic(name);
        String callStateStr = args.getString("call_direction");
        mCallDirection = "incoming".equals(callStateStr) ? CallDirection.INCOMING : CallDirection.OUTGOING;
        if (mCallDirection == CallDirection.INCOMING) {
            mCallSeqID = args.getInt("call_seq");
        }

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

        VxCard pub = mTopic.getPub();
        mPeerAvatar = view.findViewById(R.id.imageAvatar);
        UiUtils.setAvatar(mPeerAvatar, pub, name, false);

        String peerName = pub != null ? pub.fn : null;
        if (TextUtils.isEmpty(peerName)) {
            peerName = getResources().getString(R.string.unknown);
        }
        mPeerName = view.findViewById(R.id.peerName);
        mPeerName.setText(peerName);

        // Check permissions.
        LinkedList<String> missing = UiUtils.getMissingPermissions(activity,
                new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO});
        if (!missing.isEmpty()) {
            mMediaPermissionLauncher.launch(missing.toArray(new String[]{}));
            return;
        }
        mRemoteIceCandidatesCache = new ArrayList<>();

        // Got all necessary permissions.
        startMediaAndSignal();
    }

    @Override
    public void onDestroyView() {
        stopMediaAndSignal();
        Cache.getTinode().removeListener(mTinodeListener);
        mTopic.setListener(null);

        Context ctx = getContext();
        if (ctx != null) {
            AudioManager audioManager = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
            if (audioManager != null) {
                audioManager.setMicrophoneMute(false);
                audioManager.setSpeakerphoneOn(false);
            }
        }

        super.onDestroyView();
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onPause() {
        stopSoundEffect();
        super.onPause();
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

    // Mute/unmute media.
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
            mLocalVideoTrack.setEnabled(!disabled);
        } else {
            mLocalAudioTrack.setEnabled(!disabled);

            // Need to disable microphone too, otherwise webrtc LocalPeer produces echo.
            AudioManager audioManager = (AudioManager) b.getContext().getSystemService(Context.AUDIO_SERVICE);
            if (audioManager != null) {
                audioManager.setMicrophoneMute(disabled);
            }
        }

        if (mLocalPeer == null) {
            return;
        }

        for (RtpSender transceiver : mLocalPeer.getSenders()) {
            MediaStreamTrack track = transceiver.track();
            if ((video && track instanceof VideoTrack) || (!video && track instanceof AudioTrack)) {
                track.setEnabled(!disabled);
            }
        }

        if (disabled) {
            Log.i(TAG, "Audio enabled=" + mLocalAudioTrack.enabled());
        }
    }

    private void toggleSpeakerphone(FloatingActionButton b) {
        AudioManager audioManager = (AudioManager) b.getContext().getSystemService(Context.AUDIO_SERVICE);
        if (audioManager != null) {
            boolean enabled = audioManager.isSpeakerphoneOn();
            audioManager.setSpeakerphoneOn(!enabled);
            b.setImageResource(enabled ? R.drawable.ic_volume_off : R.drawable.ic_volume_up);
        }
    }

    // Initializes media (camera and audio) and notifies the peer (sends "invite" for outgoing,
    // and "accept" for incoming call).
    private void startMediaAndSignal() {
        final Activity activity = getActivity();
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
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
        DefaultVideoEncoderFactory defaultVideoEncoderFactory = new DefaultVideoEncoderFactory(
                mRootEglBase.getEglBaseContext(), true, true);
        DefaultVideoDecoderFactory defaultVideoDecoderFactory = new DefaultVideoDecoderFactory(mRootEglBase.getEglBaseContext());
        mPeerConnectionFactory = PeerConnectionFactory.builder()
                .setOptions(options)
                .setVideoEncoderFactory(defaultVideoEncoderFactory)
                .setVideoDecoderFactory(defaultVideoDecoderFactory)
                .createPeerConnectionFactory();

        // Create a VideoCapturer instance.
        mVideoCapturerAndroid = createCameraCapturer(new Camera1Enumerator(false));
        // Create a VideoSource instance
        if (mVideoCapturerAndroid != null) {
            SurfaceTextureHelper surfaceTextureHelper =
                    SurfaceTextureHelper.create("CaptureThread", mRootEglBase.getEglBaseContext());
            mVideoSource = mPeerConnectionFactory.createVideoSource(mVideoCapturerAndroid.isScreencast());
            mVideoCapturerAndroid.initialize(surfaceTextureHelper, activity, mVideoSource.getCapturerObserver());
        }

        // Create MediaConstraints - Will be useful for specifying video and audio constraints.
        MediaConstraints audioConstraints = new MediaConstraints();
        MediaConstraints videoConstraints = new MediaConstraints();

        mLocalVideoTrack = mPeerConnectionFactory.createVideoTrack("100", mVideoSource);

        // Create an AudioSource instance
        mAudioSource = mPeerConnectionFactory.createAudioSource(audioConstraints);
        mLocalAudioTrack = mPeerConnectionFactory.createAudioTrack("101", mAudioSource);
        if (mAudioOff) {
            mLocalAudioTrack.setEnabled(false);
        }

        if (mVideoCapturerAndroid != null) {
            mVideoCapturerAndroid.startCapture(1024, 720, 30);
        }

        // VideoRenderer is ready => add the renderer to the VideoTrack.
        mLocalVideoTrack.addSink(mLocalVideoView);
        if (mVideoOff) {
            mLocalVideoTrack.setEnabled(false);
        }
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

        mLocalVideoView.init(mRootEglBase.getEglBaseContext(), null);
        mLocalVideoView.setEnableHardwareScaler(true);
        mLocalVideoView.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT);
        mLocalVideoView.setZOrderMediaOverlay(true);
        mLocalVideoView.setVisibility(View.VISIBLE);
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
    }

    // Call initiation.
    private void handleCallStart() {
        if (!mTopic.isAttached() || mCallStarted) {
            // Already started or not attached. wait to attach.
            return;
        }
        mCallStarted = true;
        switch (mCallDirection) {
            case OUTGOING:
                // Send out a call invitation to the peer.
                Map<String, Object> head = new HashMap<>();
                head.put("webrtc", "started");
                mTopic.publish(Drafty.videoCall(), head).thenApply(
                        new PromisedReply.SuccessListener<ServerMessage>() {
                            @Override
                            public PromisedReply<ServerMessage> onSuccess(ServerMessage result) {
                                if (result.ctrl != null && result.ctrl.code < 300) {
                                    int seq = result.ctrl.getIntParam("seq", -1);
                                    if (seq > 0) {
                                        // All good.
                                        mCallSeqID = seq;
                                        Cache.registerCallInProgress(mTopic.getName(), seq);
                                        return null;
                                    }
                                }
                                handleCallClose();
                                return null;
                            }
                        }, new FailureHandler(getActivity()));
                break;
            case INCOMING:
                // The callee (we) has accepted the call. Notify the caller.
                Activity activity = getActivity();
                if (activity != null) {
                    rearrangePeerViews(activity);
                    mTopic.videoCallAccept(mCallSeqID);
                } else {
                    handleCallClose();
                }
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

    // Creates and initializes a peer connection.
    private void createPeerConnection() {
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

        rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.PLAN_B;
        mLocalPeer = mPeerConnectionFactory.createPeerConnection(rtcConfig, new PeerConnection.Observer() {
            @Override
            public void onIceCandidate(IceCandidate iceCandidate) {
                // Send ICE candidate to the peer.
                Log.d(TAG, iceCandidate.toString());
                handleIceCandidateEvent(iceCandidate);
            }

            @Override
            public void onAddStream(MediaStream mediaStream) {
                // Received remote stream.
                Activity activity = getActivity();
                if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
                    return;
                }

                // Add remote media stream to the renderer.
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
            public void onDataChannel(DataChannel dataChannel) {
                Log.d(TAG, "onDataChannel() called with: dataChannel = [" + dataChannel + "]");
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
                mSdpConstraints = new MediaConstraints();
                mSdpConstraints.mandatory.add(
                        new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
                mSdpConstraints.mandatory.add(
                        new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"));
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

        // Create a local media stream and attach it to the peer connection.
        MediaStream stream = mPeerConnectionFactory.createLocalMediaStream("102");
        stream.addTrack(mLocalAudioTrack);
        stream.addTrack(mLocalVideoTrack);
        mLocalPeer.addStream(stream);
    }

    private void handleVideoCallAccepted() {
        Activity activity = getActivity();
        if (activity == null || activity.isDestroyed() || activity.isFinishing()) {
            return;
        }

        stopSoundEffect();
        rearrangePeerViews(activity);

        createPeerConnection();
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

        createPeerConnection();
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
        String sdp = (String) m.getOrDefault("sdp", "");

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
        mMediaPlayer = MediaPlayer.create(getContext(), effectId);
        mMediaPlayer.setLooping(true);
        mMediaPlayer.start();
    }

    private void stopSoundEffect() {
        if (mMediaPlayer != null) {
            mMediaPlayer.stop();
            mMediaPlayer.release();
            mMediaPlayer = null;
        }
    }

    private void rearrangePeerViews(final Activity activity) {
        activity.runOnUiThread(() -> {
            ConstraintSet cs = new ConstraintSet();
            cs.clone(mLayout);
            cs.removeFromVerticalChain(R.id.peerName);
            cs.connect(R.id.peerName, ConstraintSet.BOTTOM, R.id.callControlsPanel, ConstraintSet.TOP,0);
            cs.setHorizontalBias(R.id.peerName, 0.05f);
            cs.applyTo(mLayout);
            mPeerName.setElevation(8);

            mPeerAvatar.setVisibility(View.INVISIBLE);
        });
    }
    // Auxiliary class to facilitate serialization of SDP data.
    static class SDPAux {
        public final String type;
        public final String sdp;

        SDPAux(String type, String sdp) {
            this.type = type;
            this.sdp = sdp;
        }
    }

    // Auxiliary class to facilitate serialization of the ICE candidate data.
    private static class IceCandidateAux {
        public String type;
        public int sdpMLineIndex;
        public String sdpMid;
        public String candidate;

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
}
