package co.tinode.tindroid.services;

import android.content.Intent;
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
import co.tinode.tindroid.CallActivity;
import co.tinode.tindroid.Const;

public class CallConnectionService extends ConnectionService {
    private static final String TAG = "CallConnectionService";

    @Override
    public Connection onCreateOutgoingConnection(@Nullable PhoneAccountHandle connectionManagerPhoneAccount,
                                                 @Nullable ConnectionRequest request) {
        CallConnection conn = new CallConnection(getApplicationContext());
        conn.setInitializing();
        if (request != null) {
            conn.setAddress(request.getAddress(), TelecomManager.PRESENTATION_ALLOWED);
            conn.setVideoState(request.getVideoState());
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            conn.setConnectionProperties(Connection.PROPERTY_SELF_MANAGED);
        }
        conn.setConnectionCapabilities(Connection.CAPABILITY_MUTE);
        conn.setAudioModeIsVoip(true);
        conn.setVideoProvider(new TinodeVideoProvider());
        conn.setRinging();

        String topicName = conn.getAddress().getSchemeSpecificPart();
        Cache.prepareNewCall(topicName, conn);

        Intent intent = new Intent(this, CallActivity.class);
        intent.setAction(CallActivity.INTENT_ACTION_CALL_START);
        intent.putExtra(Const.INTENT_EXTRA_TOPIC, topicName);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);

        return conn;
    }

    @Override
    public Connection onCreateIncomingConnection(@Nullable PhoneAccountHandle connectionManagerPhoneAccount,
                                                 @Nullable ConnectionRequest request) {
        CallConnection conn = new CallConnection(getApplicationContext());
        conn.setInitializing();
        if (request != null) {
            conn.setAddress(request.getAddress(), TelecomManager.PRESENTATION_ALLOWED);
            Bundle extras = request.getExtras();
            conn.setExtras(extras.getBundle(TelecomManager.EXTRA_INCOMING_CALL_EXTRAS));
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            conn.setConnectionProperties(Connection.PROPERTY_SELF_MANAGED);
        }

        String topicName = conn.getAddress().getSchemeSpecificPart();
        Cache.prepareNewCall(topicName, conn);

        conn.setConnectionCapabilities(Connection.CAPABILITY_MUTE);
        conn.setAudioModeIsVoip(true);
        conn.setVideoProvider(new TinodeVideoProvider());

        conn.setActive();

        return conn;
    }

    @Override
    public void onCreateIncomingConnectionFailed(@Nullable PhoneAccountHandle connectionManagerPhoneAccount,
                                                 @Nullable ConnectionRequest request) {
        super.onCreateIncomingConnectionFailed(connectionManagerPhoneAccount, request);
        Log.i(TAG, "Create outgoing call failed");
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
