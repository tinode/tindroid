package co.tinode.tindroid.services;

import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.telecom.Connection;
import android.telecom.ConnectionRequest;
import android.telecom.ConnectionService;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telecom.VideoProfile;
import android.util.Log;
import android.view.Surface;

import androidx.annotation.Nullable;
import co.tinode.tindroid.Cache;
import co.tinode.tindroid.CallManager;
import co.tinode.tindroid.Const;

public class CallConnectionService extends ConnectionService {
    private static final String TAG = "CallConnectionService";

    @Override
    public Connection onCreateOutgoingConnection(@Nullable PhoneAccountHandle connectionManagerPhoneAccount,
                                                 @Nullable ConnectionRequest request) {
        Log.i(TAG, "onCreateOutgoingConnection");

        CallConnection conn = new CallConnection(getApplicationContext());
        conn.setInitializing();
        boolean audioOnly = false;
        if (request != null) {
            conn.setAddress(request.getAddress(), TelecomManager.PRESENTATION_ALLOWED);
            conn.setVideoState(request.getVideoState());
            Bundle extras = request.getExtras();
            audioOnly = extras.getBoolean(Const.INTENT_EXTRA_CALL_AUDIO_ONLY);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            conn.setConnectionProperties(Connection.PROPERTY_SELF_MANAGED);
        }
        conn.setConnectionCapabilities(Connection.CAPABILITY_MUTE |
                Connection.CAPABILITY_CAN_SEND_RESPONSE_VIA_CONNECTION);
        conn.setAudioModeIsVoip(true);
        if (!audioOnly) {
            conn.setVideoProvider(new TinodeVideoProvider());
        }
        conn.setRinging();

        String topicName = conn.getAddress().getSchemeSpecificPart();

        CallManager.showOutgoingCallUi(this, topicName, audioOnly, conn);

        return conn;
    }

    @Override
    public Connection onCreateIncomingConnection(@Nullable PhoneAccountHandle connectionManagerPhoneAccount,
                                                 @Nullable ConnectionRequest request) {
        if (request == null) {
            Log.w(TAG, "Dropped incoming call with null ConnectionRequest");
            return null;
        }

        CallConnection conn = new CallConnection(getApplicationContext());
        conn.setInitializing();
        final Uri callerUri = request.getAddress();
        conn.setAddress(callerUri, TelecomManager.PRESENTATION_ALLOWED);

        Bundle callParams = request.getExtras();
        Bundle extras = callParams.getBundle(TelecomManager.EXTRA_INCOMING_CALL_EXTRAS);
        if (extras == null) {
            Log.w(TAG, "Dropped incoming due to null extras");
            return null;
        }

        boolean audioOnly = extras.getBoolean(Const.INTENT_EXTRA_CALL_AUDIO_ONLY);
        int seq = extras.getInt(Const.INTENT_EXTRA_SEQ);
        conn.setExtras(extras);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            conn.setConnectionProperties(Connection.PROPERTY_SELF_MANAGED);
        }

        Cache.prepareNewCall(callerUri.getSchemeSpecificPart(), seq, conn);

        conn.setConnectionCapabilities(Connection.CAPABILITY_MUTE);
        conn.setAudioModeIsVoip(true);
        if (!audioOnly) {
            conn.setVideoProvider(new TinodeVideoProvider());
        }
        conn.setActive();

        return conn;
    }

    @Override
    public void onCreateIncomingConnectionFailed(@Nullable PhoneAccountHandle connectionManagerPhoneAccount,
                                                 @Nullable ConnectionRequest request) {
        super.onCreateIncomingConnectionFailed(connectionManagerPhoneAccount, request);
        Log.i(TAG, "Create incoming call failed");
    }

    @Override
    public void onCreateOutgoingConnectionFailed(@Nullable PhoneAccountHandle connectionManagerPhoneAccount,
                                                 @Nullable ConnectionRequest request) {
        super.onCreateOutgoingConnectionFailed(connectionManagerPhoneAccount, request);
        Log.i(TAG, "Create outgoing call failed");
    }

    public static class TinodeVideoProvider extends Connection.VideoProvider {
        @Override
        public void onSetCamera(String cameraId) {
            Log.i(TAG, "onSetCamera");
        }

        @Override
        public void onSetPreviewSurface(Surface surface) {
            Log.i(TAG, "onSetPreviewSurface");
        }

        @Override
        public void onSetDisplaySurface(Surface surface) {
            Log.i(TAG, "onSetDisplaySurface");
        }

        @Override
        public void onSetDeviceOrientation(int rotation) {
            Log.i(TAG, "onSetDeviceOrientation");
        }

        @Override
        public void onSetZoom(float value) {
            Log.i(TAG, "onSetZoom");
        }

        @Override
        public void onSendSessionModifyRequest(VideoProfile fromProfile, VideoProfile toProfile) {
            Log.i(TAG, "onSendSessionModifyRequest");
        }

        @Override
        public void onSendSessionModifyResponse(VideoProfile responseProfile) {
            Log.i(TAG, "onSendSessionModifyResponse");
        }

        @Override
        public void onRequestCameraCapabilities() {
            Log.i(TAG, "onRequestCameraCapabilities");
        }

        @Override
        public void onRequestConnectionDataUsage() {
            Log.i(TAG, "onRequestConnectionDataUsage");
        }

        @Override
        public void onSetPauseImage(Uri uri) {
            Log.i(TAG, "onSetPauseImage");
        }
    }
}
