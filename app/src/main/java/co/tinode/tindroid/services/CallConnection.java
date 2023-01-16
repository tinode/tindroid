package co.tinode.tindroid.services;

import android.content.Context;
import android.telecom.CallAudioState;
import android.telecom.Connection;
import android.util.Log;

import androidx.annotation.Nullable;

import co.tinode.tindroid.CallManager;

public class CallConnection extends Connection {
    private static final String TAG = "CallConnection";

    private final Context mContext;

    CallConnection(Context ctx) {
        super();
        mContext = ctx;
    }

    @Override
    public void onShowIncomingCallUi() {
        final String topicName = getAddress().getEncodedSchemeSpecificPart();
        CallManager.showIncomingCallUi(mContext, topicName, getExtras());
    }

    @Override
    public void onCallAudioStateChanged(@Nullable CallAudioState state) {
        Log.i(TAG, "onCallAudioStateChanged " + state);
    }

    @Override
    public void onAnswer() {
        Log.i(TAG, "onAnswer");
    }

    @Override
    public void onDisconnect() {
        // FIXME: this is never called by Android.
        Log.i(TAG, "onDisconnect");
        destroy();
    }

    @Override
    public void onHold() {
        Log.i(TAG, "onHold");
    }

    @Override
    public void onUnhold() {
        Log.i(TAG, "onUnhold");
    }

    @Override
    public void onReject() {
        Log.i(TAG, "onReject");
    }
}