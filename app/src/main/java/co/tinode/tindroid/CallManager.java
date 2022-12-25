package co.tinode.tindroid;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telecom.VideoProfile;
import android.text.TextUtils;
import android.util.Log;

import co.tinode.tindroid.services.CallConnectionService;

import co.tinode.tinodesdk.Tinode;
import co.tinode.tinodesdk.model.TheCard;

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

        TheCard card = (TheCard) tinode.getMeTopic().getPub();
        String accLabel = (card != null && !TextUtils.isEmpty(card.fn)) ?
                card.fn : context.getString(R.string.current_user);

        // Register current user's phone account.
        mPhoneAccountHandle = new PhoneAccountHandle(new ComponentName(context, CallConnectionService.class), mMyID);
        PhoneAccount.Builder builder = PhoneAccount.builder(mPhoneAccountHandle, accLabel);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setCapabilities(PhoneAccount.CAPABILITY_SELF_MANAGED).build();
        }
        mTelecomManager.registerPhoneAccount(builder.build());
    }

    public void placeOutgoingCall(String id) {
        TelecomManager manager = (TelecomManager) mContext.getSystemService(TELECOM_SERVICE);
        PhoneAccountHandle phoneAccountHandle = new PhoneAccountHandle(new ComponentName(mContext.getPackageName(),
                CallConnectionService.class.getName()), mMyID);

        Bundle callParams = new Bundle();
        callParams.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, phoneAccountHandle);
        callParams.putInt(TelecomManager.EXTRA_START_CALL_WITH_VIDEO_STATE, VideoProfile.STATE_BIDIRECTIONAL);
        Bundle extras = new Bundle();
        extras.putBoolean(TelecomManager.EXTRA_START_CALL_WITH_SPEAKERPHONE, true);
        callParams.putParcelable(TelecomManager.EXTRA_OUTGOING_CALL_EXTRAS, extras);
        try {
            manager.placeCall(Uri.fromParts("tn", id, null), callParams);
        } catch (SecurityException ex) {
            Log.w(TAG, "Unable to place call", ex);
        }
    }

    public void acceptIncomingCall() {
        if (mContext.checkSelfPermission(Manifest.permission.MANAGE_OWN_CALLS) != PackageManager.PERMISSION_GRANTED) {
            Log.i(TAG, "No permission to accept incoming calls");
            return;
        }

        boolean isCallPermitted = Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
                mTelecomManager.isIncomingCallPermitted(mPhoneAccountHandle);
        Log.i(TAG, "TelecomManager.isIncomingCallPermitted = " + isCallPermitted);

        Uri uri = Uri.fromParts(PhoneAccount.SCHEME_TEL, "from_mary_jane", null);
        Bundle extras = new Bundle();
        extras.putParcelable(TelecomManager.EXTRA_INCOMING_CALL_ADDRESS, uri);
        extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, mPhoneAccountHandle);
        extras.putBoolean(TelecomManager.EXTRA_START_CALL_WITH_SPEAKERPHONE, true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // TODO: handle audio-only calls.
            boolean isVideoCall = true;
            extras.putInt(TelecomManager.EXTRA_INCOMING_VIDEO_STATE, isVideoCall ?
                    VideoProfile.STATE_BIDIRECTIONAL : VideoProfile.STATE_AUDIO_ONLY);
        }
        try {
            mTelecomManager.addNewIncomingCall(mPhoneAccountHandle, extras);
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
