package co.tinode.tindroid.services;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Icon;
import android.media.RingtoneManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.telecom.CallAudioState;
import android.telecom.Connection;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.util.Log;

import androidx.annotation.ColorRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import co.tinode.tindroid.Cache;
import co.tinode.tindroid.CallActivity;
import co.tinode.tindroid.CallBroadcastReceiver;
import co.tinode.tindroid.CallManager;
import co.tinode.tindroid.Const;
import co.tinode.tindroid.R;
import co.tinode.tindroid.UiUtils;
import co.tinode.tindroid.media.VxCard;
import co.tinode.tinodesdk.ComTopic;
import co.tinode.tinodesdk.Topic;

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