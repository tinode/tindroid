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

    TelecomManager mTelecomManager;
    PhoneAccountHandle mPhoneAccountHandle;
    Context mContext;
    String mMyID;

    public CallManager(Context context) {
        mTelecomManager = (TelecomManager) context.getSystemService(TELECOM_SERVICE);
        mContext = context;

        Tinode tinode = Cache.getTinode();
        mMyID = tinode.getMyId();

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
        mPhoneAccountHandle = new PhoneAccountHandle(new ComponentName(mContext, CallConnectionService.class), mMyID);
        PhoneAccount.Builder builder = PhoneAccount.builder(mPhoneAccountHandle, accLabel)
                .addSupportedUriScheme("tinode")
                .setAddress(Uri.fromParts("tinode", mMyID, null))
                .setShortDescription(accLabel)
                .setIcon(icon);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setCapabilities(PhoneAccount.CAPABILITY_SELF_MANAGED);
        }
        mTelecomManager.registerPhoneAccount(builder.build());
    }

    public void unregisterCallingAccount() {
        // FIXME: this has to be called on logout.
        mTelecomManager.unregisterPhoneAccount(mPhoneAccountHandle);
    }

    public void placeOutgoingCall(String callee) {
        Bundle callParams = new Bundle();
        callParams.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, mPhoneAccountHandle);
        callParams.putInt(TelecomManager.EXTRA_START_CALL_WITH_VIDEO_STATE, VideoProfile.STATE_BIDIRECTIONAL);
        callParams.putBoolean(TelecomManager.EXTRA_START_CALL_WITH_SPEAKERPHONE, true);
        Bundle extras = new Bundle();
        extras.putString(Const.INTENT_EXTRA_TOPIC, callee);
        callParams.putParcelable(TelecomManager.EXTRA_OUTGOING_CALL_EXTRAS, extras);
        try {
            mTelecomManager.placeCall(Uri.fromParts("tinode", callee, null), callParams);
        } catch (SecurityException ex) {
            Log.w(TAG, "Unable to place call", ex);
        }
    }

    public void acceptIncomingCall(String caller, int seq) {
        if (mContext.checkSelfPermission(Manifest.permission.MANAGE_OWN_CALLS) != PackageManager.PERMISSION_GRANTED) {
            Log.i(TAG, "No permission to accept incoming calls");
            return;
        }

        Uri uri = Uri.fromParts("tinode", caller, null);
        Bundle callParams = new Bundle();
        callParams.putParcelable(TelecomManager.EXTRA_INCOMING_CALL_ADDRESS, uri);
        callParams.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, mPhoneAccountHandle);

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

        try {
            mTelecomManager.addNewIncomingCall(mPhoneAccountHandle, callParams);
        } catch (SecurityException ex) {
            if (Build.MANUFACTURER.equalsIgnoreCase("Samsung")) {
                Intent intent = new Intent();
                intent.setComponent(new ComponentName("com.android.server.telecom",
                        "com.android.server.telecom.settings.EnableAccountPreferenceActivity"));
                mContext.startActivity(intent);
            } else {
                mContext.startActivity(new Intent(TelecomManager.ACTION_CHANGE_PHONE_ACCOUNTS));
            }
        } catch (Exception ex) {
            Log.i(TAG, "Failed to accept incoming call", ex);
        }
    }
}
