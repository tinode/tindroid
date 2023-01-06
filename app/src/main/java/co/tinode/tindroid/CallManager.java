package co.tinode.tindroid;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telecom.VideoProfile;
import android.text.TextUtils;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import co.tinode.tindroid.media.VxCard;
import co.tinode.tindroid.services.CallConnectionService;

import co.tinode.tinodesdk.Tinode;

import static android.content.Context.TELECOM_SERVICE;

public class CallManager {
    private static final String TAG = "CallManager";

    private static CallManager sSharedInstance;

    private final PhoneAccountHandle mPhoneAccountHandle;

    private CallManager(Context context) {
        TelecomManager telecomManager = (TelecomManager) context.getSystemService(TELECOM_SERVICE);

        Tinode tinode = Cache.getTinode();
        String myID = tinode.getMyId();
        VxCard card = (VxCard) tinode.getMeTopic().getPub();
        String accLabel = null;
        Icon icon = null;
        if (card != null) {
            accLabel = !TextUtils.isEmpty(card.fn) ? card.fn : context.getString(R.string.current_user);
            Bitmap avatar = card.getBitmap();
            if (avatar != null) {
                icon = Icon.createWithBitmap(avatar);
            }
        }

        // Register current user's phone account.
        mPhoneAccountHandle = new PhoneAccountHandle(new ComponentName(context, CallConnectionService.class), myID);
        PhoneAccount.Builder builder = PhoneAccount.builder(mPhoneAccountHandle, accLabel)
                .addSupportedUriScheme("tinode")
                .setAddress(Uri.fromParts("tinode", myID, null))
                .setShortDescription(accLabel)
                .setIcon(icon);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setCapabilities(PhoneAccount.CAPABILITY_SELF_MANAGED);
        }
        telecomManager.registerPhoneAccount(builder.build());
    }

    private static CallManager getShared() {
        if (sSharedInstance != null) {
            return sSharedInstance;
        }
        sSharedInstance = new CallManager(TindroidApp.getAppContext());
        return sSharedInstance;
    }

    // FIXME: this has to be called on logout.
    public static void unregisterCallingAccount() {
        CallManager shared = CallManager.getShared();
        TelecomManager telecomManager = (TelecomManager) TindroidApp.getAppContext().getSystemService(TELECOM_SERVICE);
        telecomManager.unregisterPhoneAccount(shared.mPhoneAccountHandle);
    }

    public static void placeOutgoingCall(String callee, boolean audioOnly) {
        CallManager shared = CallManager.getShared();
        Bundle callParams = new Bundle();
        callParams.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, shared.mPhoneAccountHandle);
        callParams.putInt(TelecomManager.EXTRA_START_CALL_WITH_VIDEO_STATE, VideoProfile.STATE_BIDIRECTIONAL);
        callParams.putBoolean(TelecomManager.EXTRA_START_CALL_WITH_SPEAKERPHONE, true);

        Bundle extras = new Bundle();
        extras.putString(Const.INTENT_EXTRA_TOPIC, callee);
        extras.putBoolean(Const.INTENT_EXTRA_CALL_AUDIO_ONLY, audioOnly);
        callParams.putParcelable(TelecomManager.EXTRA_OUTGOING_CALL_EXTRAS, extras);
        try {
            TelecomManager telecomManager = (TelecomManager) TindroidApp.getAppContext().getSystemService(TELECOM_SERVICE);
            telecomManager.placeCall(Uri.fromParts("tinode", callee, null), callParams);
        } catch (SecurityException ex) {
            Log.w(TAG, "Unable to place call", ex);
        }
    }

    public static void acceptIncomingCall(Context context, String caller, int seq) {
        if (context.checkSelfPermission(Manifest.permission.MANAGE_OWN_CALLS) != PackageManager.PERMISSION_GRANTED) {
            Log.i(TAG, "No permission to accept incoming calls");
            return;
        }

        CallManager shared = CallManager.getShared();

        Uri uri = Uri.fromParts("tinode", caller, null);
        Bundle callParams = new Bundle();
        callParams.putParcelable(TelecomManager.EXTRA_INCOMING_CALL_ADDRESS, uri);
        callParams.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, shared.mPhoneAccountHandle);

        callParams.putBoolean(TelecomManager.EXTRA_START_CALL_WITH_SPEAKERPHONE, true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // TODO: handle audio-only calls.
            boolean isVideoCall = true;
            callParams.putInt(TelecomManager.EXTRA_INCOMING_VIDEO_STATE, isVideoCall ?
                    VideoProfile.STATE_BIDIRECTIONAL : VideoProfile.STATE_AUDIO_ONLY);
        }

        Bundle extras = new Bundle();
        extras.putString(Const.INTENT_EXTRA_TOPIC, caller);
        extras.putInt(Const.INTENT_EXTRA_SEQ, seq);
        callParams.putBundle(TelecomManager.EXTRA_INCOMING_CALL_EXTRAS, extras);

        try {
            TelecomManager telecomManager = (TelecomManager) context.getSystemService(TELECOM_SERVICE);
            telecomManager.addNewIncomingCall(shared.mPhoneAccountHandle, callParams);
        } catch (SecurityException ex) {
            if (Build.MANUFACTURER.equalsIgnoreCase("Samsung")) {
                Intent intent = new Intent();
                intent.setComponent(new ComponentName("com.android.server.telecom",
                        "com.android.server.telecom.settings.EnableAccountPreferenceActivity"));
                context.startActivity(intent);
            } else {
                context.startActivity(new Intent(TelecomManager.ACTION_CHANGE_PHONE_ACCOUNTS));
            }
        } catch (Exception ex) {
            Log.i(TAG, "Failed to accept incoming call", ex);
        }
    }
}
