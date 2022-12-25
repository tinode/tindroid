package co.tinode.tindroid.services;

import android.net.Uri;
import android.os.Build;
import android.telecom.Connection;
import android.telecom.ConnectionRequest;
import android.telecom.ConnectionService;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telecom.VideoProfile;
import android.util.Log;
import android.view.Surface;

import androidx.annotation.Nullable;

import static android.telecom.TelecomManager.PRESENTATION_ALLOWED;

public class CallConnectionService extends ConnectionService {
    private static final String TAG = "CallConnectionService";

    @Override
    public Connection onCreateOutgoingConnection(@Nullable PhoneAccountHandle connectionManagerPhoneAccount,
                                                 @Nullable ConnectionRequest request) {
        Log.i(TAG, "onCreateOutgoingConnection");
        CallConnection conn = new CallConnection(getApplicationContext());
        if (request != null) {
            conn.setAddress(request.getAddress(), PRESENTATION_ALLOWED);
        }
        conn.setInitializing();
        conn.setAudioModeIsVoip(true);
        // conn.videoProvider = MyVideoProvider();
        conn.setActive();
        return conn;
    }

    @Override
    public Connection onCreateIncomingConnection(@Nullable PhoneAccountHandle connectionManagerPhoneAccount,
                                                 @Nullable ConnectionRequest request) {
        Log.i(TAG, "onCreateIncomingConnection");
        CallConnection conn = new CallConnection(getApplicationContext());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            conn.setConnectionProperties(Connection.PROPERTY_SELF_MANAGED);
        }
        conn.setCallerDisplayName("test call", TelecomManager.PRESENTATION_ALLOWED);
        if (request != null) {
            conn.setAddress(request.getAddress(), PRESENTATION_ALLOWED);
        }
        conn.setInitializing();
        conn.setAudioModeIsVoip(true);
        conn.setVideoProvider(new Connection.VideoProvider() {
            @Override
            public void onSetCamera(String cameraId) {

            }

            @Override
            public void onSetPreviewSurface(Surface surface) {

            }

            @Override
            public void onSetDisplaySurface(Surface surface) {

            }

            @Override
            public void onSetDeviceOrientation(int rotation) {

            }

            @Override
            public void onSetZoom(float value) {

            }

            @Override
            public void onSendSessionModifyRequest(VideoProfile fromProfile, VideoProfile toProfile) {

            }

            @Override
            public void onSendSessionModifyResponse(VideoProfile responseProfile) {

            }

            @Override
            public void onRequestCameraCapabilities() {

            }

            @Override
            public void onRequestConnectionDataUsage() {

            }

            @Override
            public void onSetPauseImage(Uri uri) {

            }
        });
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
}
