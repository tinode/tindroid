package co.tinode.tindroid;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.media.AudioManager;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

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

import co.tinode.tindroid.media.VxCard;
import co.tinode.tinodesdk.ComTopic;
import co.tinode.tinodesdk.PromisedReply;
import co.tinode.tinodesdk.Tinode;
import co.tinode.tinodesdk.model.Drafty;
import co.tinode.tinodesdk.model.MsgServerInfo;
import co.tinode.tinodesdk.model.ServerMessage;

/**
 * Video call UI: local & remote video views.
 */
public class CallFragment extends Fragment {
    private static final String TAG = "CallFragment";

    private class CustomSdpObserver implements SdpObserver {
        private String tag;

        CustomSdpObserver(String logTag) {
            tag = this.getClass().getCanonicalName();
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

    PeerConnectionFactory mPeerConnectionFactory;
    MediaConstraints mAudioConstraints;
    MediaConstraints mVideoConstraints;
    MediaConstraints mSdpConstraints;
    VideoCapturer mVideoCapturerAndroid;
    VideoSource mVideoSource;
    VideoTrack mLocalVideoTrack;
    AudioSource mAudioSource;
    AudioTrack mLocalAudioTrack;
    SurfaceTextureHelper mSurfaceTextureHelper;
    PeerConnection mLocalPeer;
    List<PeerConnection.IceServer> mIceServers;
    EglBase mRootEglBase;

    // Video (camera views).
    SurfaceViewRenderer mLocalVideoView;
    SurfaceViewRenderer mRemoteVideoView;

    public enum CallDirection {
        OUTGOING,
        INCOMING,
    }

    CallDirection mCallDirection;
    int mCallSeqID;

    // Check if we have camera and mic permissions.
    private ActivityResultLauncher<String[]>  mMediaPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                for (Map.Entry<String,Boolean> e : result.entrySet()) {
                    if (!e.getValue()) {
                        Log.d(TAG, "The user has disallowed " + e.toString());
                        handleCallClose();
                        return;
                    }
                }
                // All permissions granted.
                this.startMediaAndSignal();
            });

    // Listens for incoming call-related info messages.
    private class InfoListener extends UiUtils.EventListener {
        private static final String TAG = "CallFragment.InfoListener";

        private CallFragment parent;

        InfoListener(Activity owner, Boolean connected) {
            super(owner, connected);
        }

        public void setParent(CallFragment parent) {
            this.parent = parent;
        }

        @Override
        public void onInfoMessage(MsgServerInfo info) {
            if (!"call".equals(info.what)) {
                // We are only interested in "call" info messages.
                return;
            }
            switch (info.event) {
                case "accept":
                    this.parent.handleVideoCallAccepted();
                    break;
                case "offer":
                    this.parent.handleVideoOfferMsg(info);
                    break;
                case "answer":
                    this.parent.handleVideoAnswerMsg(info);
                    break;
                case "ice-candidate":
                    this.parent.handleNewICECandidateMsg(info);
                    break;
                case "hang-up":
                    this.parent.handleRemoteHangup(info);
                    break;
                default:
                    break;
            }
        }
    }

    private ComTopic<VxCard> mTopic;
    private InfoListener mTinodeListener;

    public CallFragment() {}

    @Override
    public void onResume() {
        Tinode tinode = Cache.getTinode();
        mTinodeListener = new InfoListener(this.getActivity(), tinode.isConnected());
        mTinodeListener.setParent(this);
        tinode.addListener(mTinodeListener);

        super.onResume();
    }

    @Override
    public void onPause() {
        this.stopMediaAndSignal();
        Cache.getTinode().removeListener(mTinodeListener);
        super.onPause();
    }

    // Mute/unmute media (video if toggleCamera, audio otherwise).
    private void toggleMedia(FloatingActionButton b, boolean toggleCamera, @DrawableRes int enabledIcon, int disabledIcon) {
        if (mLocalPeer == null) {
            return;
        }
        for (RtpSender transceiver : mLocalPeer.getSenders()) {
            MediaStreamTrack track = transceiver.track();
            if ((toggleCamera && track instanceof VideoTrack) ||
                    (!toggleCamera && track instanceof AudioTrack)) {
                boolean enabled = !track.enabled();
                track.setEnabled(enabled);
                b.setImageResource(enabled ? enabledIcon : disabledIcon);
            }
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstance) {
        final Bundle args = getArguments();
        if (args == null) {
            // Reject the call.
            handleCallClose();
            return;
        }
        String name = args.getString("topic");
        mTopic = (ComTopic<VxCard>)Cache.getTinode().getTopic(name);
        String callStateStr = args.getString("call_direction");
        mCallSeqID = args.getInt("call_seq");
        mCallDirection = "incoming".equals(callStateStr) ? CallDirection.INCOMING : CallDirection.OUTGOING;
        // Check permissions.
        final MessageActivity activity = (MessageActivity) getActivity();
        LinkedList<String> missing = UiUtils.getMissingPermissions(activity,
                new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO});
        if (!missing.isEmpty()) {
            Log.d(TAG,"Requesting missing permissions:" + missing);
            mMediaPermissionLauncher.launch(missing.toArray(new String[]{}));
            return;
        }
        // Got all necessary permissions.
        this.startMediaAndSignal();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_call, container, false);
        mLocalVideoView = v.findViewById(R.id.localView);
        mRemoteVideoView = v.findViewById(R.id.remoteView);

        AudioManager audioManager = (AudioManager)getActivity()
                .getSystemService(Context.AUDIO_SERVICE);
        audioManager.setMode(AudioManager.MODE_IN_CALL);
        audioManager.setSpeakerphoneOn(true);

        // Button click handlers: mute/unmute video/audio, hang up.
        v.findViewById(R.id.hangupBtn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleCallClose();
            }
        });
        v.findViewById(R.id.toggleCameraBtn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleMedia((FloatingActionButton)v, true,
                        R.drawable.ic_outline_videocam_24,
                        R.drawable.ic_outline_videocam_off_24);
            }
        });
        v.findViewById(R.id.toggleMicBtn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleMedia((FloatingActionButton)v, false,
                        R.drawable.ic_outline_mic_24,
                        R.drawable.ic_outline_mic_off_24);
            }
        });
        return v;
    }

    @Override
    public void onDestroyView() {
        this.stopMediaAndSignal();
        super.onDestroyView();
    }

    // Initializes media (camera and audio) and notifies the peer (sends "invite" for outgoing,
    // and "accept" for incoming call).
    private void startMediaAndSignal() {
        // Keep screen on.
        final Activity activity = getActivity();
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            // We are done anyway. Just quite.
            return;
        }
        activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        initVideos();
        initIceServers();

        // Initialize PeerConnectionFactory globals.
        PeerConnectionFactory.InitializationOptions initializationOptions =
                PeerConnectionFactory.InitializationOptions.builder(this.getActivity())
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

        // Create MediaConstraints - Will be useful for specifying video and audio constraints.
        mAudioConstraints = new MediaConstraints();
        mVideoConstraints = new MediaConstraints();

        // Create a VideoSource instance
        if (mVideoCapturerAndroid != null) {
            mSurfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", mRootEglBase.getEglBaseContext());
            mVideoSource = mPeerConnectionFactory.createVideoSource(mVideoCapturerAndroid.isScreencast());
            mVideoCapturerAndroid.initialize(mSurfaceTextureHelper, this.getActivity(), mVideoSource.getCapturerObserver());
        }
        mLocalVideoTrack = mPeerConnectionFactory.createVideoTrack("100", mVideoSource);

        // Create an AudioSource instance
        mAudioSource = mPeerConnectionFactory.createAudioSource(mAudioConstraints);
        mLocalAudioTrack = mPeerConnectionFactory.createAudioTrack("101", mAudioSource);

        if (mVideoCapturerAndroid != null) {
            mVideoCapturerAndroid.startCapture(1024, 720, 30);
        }

        // VideoRenderer is ready => add the renderer to the VideoTrack.
        mLocalVideoTrack.addSink(mLocalVideoView);

        mLocalVideoView.setMirror(true);
        mRemoteVideoView.setMirror(true);

        this.handleCallInvite();
    }

    // Stops media and concludes the call (sends "hang-up" to the peer).
    private void stopMediaAndSignal() {
        // Clean up.
        if (this.mLocalPeer != null) {
            this.mLocalPeer.close();
            this.mLocalPeer = null;
        }
        if (this.mRemoteVideoView != null) {
            this.mRemoteVideoView.release();
            this.mRemoteVideoView = null;
        }
        if (mLocalVideoTrack != null) {
            mLocalVideoTrack.removeSink(mLocalVideoView);
            mLocalVideoTrack = null;
        }
        if (mVideoCapturerAndroid != null) {
            try {
                mVideoCapturerAndroid.stopCapture();
            } catch (InterruptedException e) {
                Log.e(TAG, "Failed to stop camera " + e);
            }
            mVideoCapturerAndroid = null;
        }
        if (this.mLocalVideoView != null) {
            this.mLocalVideoView.release();
            this.mLocalVideoView = null;
        }

        if (this.mAudioSource != null) {
            this.mAudioSource.dispose();
            this.mAudioSource = null;
        }
        if (this.mVideoSource != null) {
            this.mVideoSource.dispose();
            this.mVideoSource = null;
        }
        if (this.mRootEglBase != null) {
            this.mRootEglBase.release();
            this.mRootEglBase = null;
        }

        handleCallClose();
    }

    private void initVideos() {
        mRootEglBase = EglBase.create();
        mLocalVideoView.init(mRootEglBase.getEglBaseContext(), null);
        mRemoteVideoView.init(mRootEglBase.getEglBaseContext(), null);
        mLocalVideoView.setZOrderMediaOverlay(true);
        mRemoteVideoView.setZOrderMediaOverlay(true);
    }

    private void initIceServers() {
        // TODO: ICE/WebRTC config should be obtained from the server.
        mIceServers = new ArrayList<>();
        mIceServers.add(PeerConnection.IceServer.builder("stun:bn-turn1.xirsys.com").createIceServer());

        PeerConnection.IceServer.Builder bld = PeerConnection.IceServer.builder(
                new ArrayList<>(Arrays.asList(
                        "turn:bn-turn1.xirsys.com:80?transport=udp",
                        "turn:bn-turn1.xirsys.com:3478?transport=udp",
                        "turn:bn-turn1.xirsys.com:80?transport=tcp",
                        "turn:bn-turn1.xirsys.com:3478?transport=tcp",
                        "turns:bn-turn1.xirsys.com:443?transport=tcp",
                        "turns:bn-turn1.xirsys.com:5349?transport=tcp"
                )));
        bld.setUsername("0kYXFmQL9xojOrUy4VFemlTnNPVFZpp7jfPjpB3AjxahuRe4QWrCs6Ll1vDc7TTjAAAAAGAG2whXZWJUdXRzUGx1cw==");
        bld.setPassword("285ff060-5a58-11eb-b269-0242ac140004");
        mIceServers.add(bld.createIceServer());
    }

    // Sends a hang-up notification to the peer and closes the fragment.
    private void handleCallClose() {
        // Close fragment.
        if (mCallSeqID > 0) {
            this.mTopic.videoCall("hang-up", mCallSeqID, null);
        }
        mCallSeqID = -1;
        final Activity activity = getActivity();
        if (activity != null && !activity.isFinishing() && !activity.isDestroyed()) {
            getActivity().getSupportFragmentManager().popBackStack();
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

    // Call initiation.
    private void handleCallInvite() {
        switch (mCallDirection) {
            case OUTGOING:
                // Send out a call invitation to the peer.
                Map<String, Object> head = new HashMap<>();
                head.put("mime", Tinode.VIDEO_CALL_MIME);
                head.put("webrtc", "started");
                mTopic.publish(Drafty.videoCall(), head).thenApply(new PromisedReply.SuccessListener<ServerMessage>() {
                    @Override
                    public PromisedReply<ServerMessage> onSuccess(ServerMessage result) {
                    if (result.ctrl != null && result.ctrl.code < 300) {
                        int seq = result.ctrl.getIntParam("seq", -1);
                        if (seq > 0) {
                            // All good.
                            mCallSeqID = seq;
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
                this.mTopic.videoCall("accept", mCallSeqID, null);
                break;
            default:
                break;
        }
    }

    // Auxiliary class to facilitate serialization of SDP data.
    class SDPAux {
        public final String type;
        public final String sdp;
        SDPAux(String type, String sdp) {
            this.type = type;
            this.sdp = sdp;
        }
    }

    // Sends a SDP offer to the peer.
    private void handleSendOffer(SessionDescription sd) {
        this.mTopic.videoCall("offer", mCallSeqID, new SDPAux(sd.type.canonicalForm(), sd.description));
    }

    // Sends a SDP answer to the peer.
    private void handleSendAnswer(SessionDescription sd) {
        this.mTopic.videoCall("answer", mCallSeqID, new SDPAux(sd.type.canonicalForm(), sd.description));
    }

    private VideoCapturer createCameraCapturer(CameraEnumerator enumerator) {
        final String[] deviceNames = enumerator.getDeviceNames();

        // First, try to find front facing camera
        Log.d(TAG, "Looking for front facing cameras.");
        for (String deviceName : deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                Log.d(TAG, "Creating front facing camera capturer.");
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);

                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }
        }

        // Front facing camera not found, try something else
        Log.d(TAG, "Looking for other cameras.");
        for (String deviceName : deviceNames) {
            if (!enumerator.isFrontFacing(deviceName)) {
                Log.d(TAG, "Creating other camera capturer.");
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);

                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }
        }

        return null;
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
        mLocalPeer = mPeerConnectionFactory.createPeerConnection(rtcConfig, new PeerConnection.Observer()/*CustomPeerConnectionObserver("localPeerCreation")*/ {
            @Override
            public void onIceCandidate(IceCandidate iceCandidate) {
                //super.onIceCandidate(iceCandidate);
                //onIceCandidateReceived(iceCandidate);
                // Send ICE candidate to the peer.

                Log.d(TAG, iceCandidate.toString());
                handleIceCandidateEvent(iceCandidate);
            }

            @Override
            public void onAddStream(MediaStream mediaStream) {
                Log.d(TAG, "Received Remote stream.");
                //super.onAddStream(mediaStream);

                // Add remote media stream to the renderer.
                final VideoTrack videoTrack = mediaStream.videoTracks.get(0);
                getActivity().runOnUiThread(() -> {
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
                Log.d(TAG, "onIceCandidatesRemoved() called with: iceCandidates = [" + iceCandidates + "]");
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
                        mLocalPeer.setLocalDescription(new CustomSdpObserver("localSetLocalDesc"), sessionDescription);
                        handleSendOffer(sessionDescription);
                    }
                }, mSdpConstraints);
            }

            @Override
            public void onAddTrack(RtpReceiver rtpReceiver, MediaStream[] mediaStreams) {
                Log.d(TAG, "onAddTrack() called with: rtpReceiver = [" + rtpReceiver + "], mediaStreams = [" + mediaStreams + "]");
            }
        });

        // Create a local media stream and attach it to the peer connection.
        MediaStream stream = mPeerConnectionFactory.createLocalMediaStream("102");
        stream.addTrack(mLocalAudioTrack);
        stream.addTrack(mLocalVideoTrack);
        mLocalPeer.addStream(stream);
    }

    private void handleVideoCallAccepted() {
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
        Map<String, Object> m = (Map<String, Object>)info.payload;
        String type = (String) m.getOrDefault("type", "");
        String sdp = (String)m.getOrDefault("sdp", "");

        mLocalPeer.setRemoteDescription(new CustomSdpObserver("localSetRemote"),
                new SessionDescription(
                        SessionDescription.Type.fromCanonicalForm(
                                type.toLowerCase()),
                        sdp));

        mLocalPeer.createAnswer(new CustomSdpObserver("localCreateAns") {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                super.onCreateSuccess(sessionDescription);
                mLocalPeer.setLocalDescription(new CustomSdpObserver("localSetLocal"), sessionDescription);

                handleSendAnswer(sessionDescription);
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
        Map<String, Object> m = (Map<String, Object>)info.payload;
        String type = (String) m.getOrDefault("type", "");
        String sdp = (String)m.getOrDefault("sdp", "");

        mLocalPeer.setRemoteDescription(new CustomSdpObserver("localSetRemote"),
                new SessionDescription(
                        SessionDescription.Type.fromCanonicalForm(
                                type.toLowerCase()),
                        sdp));
    }

    // Adds remote ICE candidate data received from the peer to the peer connection.
    private void handleNewICECandidateMsg(@NonNull MsgServerInfo info) {
        if (info.payload == null) {
            // Skip.
            Log.e(TAG, "Received ICE candidate message an empty payload. Skipping.");
            return;
        }
        Map<String, Object> m = (Map<String, Object>)info.payload;
        String sdpMid = (String)m.getOrDefault("sdpMid", "");
        int sdpMLineIndex = (int)m.getOrDefault("sdpMLineIndex", 0);
        String sdp = (String)m.getOrDefault("sdp", "");

        mLocalPeer.addIceCandidate(new IceCandidate(sdpMid,
                sdpMLineIndex, sdp));
    }

    // Auxiliary class to facilitate serialization of the ICE candidate data.
    private class IceCandidateAux {
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

    // Sends a local ICE candidate to the other party.
    private void handleIceCandidateEvent(IceCandidate candidate) {
        mTopic.videoCall(
                "ice-candidate", mCallSeqID,
                new IceCandidateAux("candidate", candidate.sdpMLineIndex, candidate.sdpMid, candidate.sdp));
    }

    // Cleans up call after receiving a remote hang-up notification.
    private void handleRemoteHangup(MsgServerInfo info) {
        this.handleCallClose();
    }
}