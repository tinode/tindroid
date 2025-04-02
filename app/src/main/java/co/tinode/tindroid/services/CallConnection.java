package co.tinode.tindroid.services;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.telecom.CallAudioState;
import android.telecom.Connection;
import android.util.Log;

import androidx.annotation.Nullable;

import co.tinode.tindroid.CallManager;
import co.tinode.tindroid.Const;

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
        Log.d(TAG, "onCallAudioStateChanged " + state);
    }

    @Override
    public void onAnswer() {
        Bundle args = getExtras();
        final String topicName = getAddress().getEncodedSchemeSpecificPart();
        Intent answer = CallManager.answerCallIntent(mContext, topicName, args.getInt(Const.INTENT_EXTRA_SEQ),
                args.getBoolean(Const.INTENT_EXTRA_CALL_AUDIO_ONLY));
        mContext.startActivity(answer);
    }

    @Override
    public void onDisconnect() {
        // FIXME: this is never called by Android.
        Log.d(TAG, "onDisconnect");
        destroy();
    }

    @Override
    public void onHold() {
        Log.d(TAG, "onHold");
    }

    @Override
    public void onUnhold() {
        Log.d(TAG, "onUnhold");
    }

    @Override
    public void onReject() {
        Log.d(TAG, "onReject");
    }
}