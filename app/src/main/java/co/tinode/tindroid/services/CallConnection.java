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

import androidx.core.app.NotificationCompat;
import co.tinode.tindroid.Cache;
import co.tinode.tindroid.CallActivity;
import co.tinode.tindroid.CallBroadcastReceiver;
import co.tinode.tindroid.Const;
import co.tinode.tindroid.R;
import co.tinode.tindroid.UiUtils;
import co.tinode.tindroid.media.VxCard;
import co.tinode.tinodesdk.ComTopic;

public class CallConnection extends Connection {
    private static final String TAG = "CallConnection";
    public static final String NOTIFICATION_TAG_INCOMING_CALL = "incoming_call";

    private final Context mContext;

    CallConnection(Context ctx) {
        super();
        mContext = ctx;
    }

    @Override
    public void onShowIncomingCallUi() {
        Log.i(TAG, "onShowIncomingCallUi");

        NotificationManager nm = mContext.getSystemService(NotificationManager.class);

        Notification.Builder builder = new Notification.Builder(mContext);

        builder.setPriority(Notification.PRIORITY_HIGH)
                .setOngoing(true)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setChannelId(Const.CALL_NOTIFICATION_CHAN_ID);
        }

        String topicName = getAddress().getEncodedSchemeSpecificPart();
        Bundle args = getExtras();
        int seq = args.getInt(Const.INTENT_EXTRA_SEQ);

        PendingIntent askUserIntent = askUserIntent(topicName, seq);
        // Set notification content intent to take user to fullscreen UI if user taps on the
        // notification body.
        builder.setContentIntent(askUserIntent);
        // Set full screen intent to trigger display of the fullscreen UI when the notification
        // manager deems it appropriate.
        builder.setFullScreenIntent(askUserIntent, true);

        // Setup notification content.
        ComTopic topic = (ComTopic) Cache.getTinode().getTopic(topicName);
        if (topic != null) {
            VxCard pub = (VxCard) topic.getPub();
            int width = (int) mContext.getResources().getDimension(android.R.dimen.notification_large_icon_width);
            //Bitmap avatar = UiUtils.avatarBitmap(mContext, pub, topic.getTopicType(), topic.getName(), width);
            //builder.setLargeIcon(Icon.createWithBitmap(avatar));
            String userName = pub != null && !TextUtils.isEmpty(pub.fn) ? pub.fn : mContext.getString(R.string.unknown);
            builder.setContentTitle(userName);
        }
        builder.setSmallIcon(R.drawable.ic_icon_push)
                .setContentText(mContext.getString(R.string.tinode_video_call))
                .setUsesChronometer(true)
                .setCategory(Notification.CATEGORY_CALL);
        // This will be ignored on O+ and handled by the channel
        builder.setPriority(Notification.PRIORITY_MAX);

        builder.addAction(new Notification.Action.Builder(Icon.createWithResource(mContext, R.drawable.ic_call_end),
                getActionText(mContext, R.string.decline_call, R.color.colorNegativeAction), declineIntent(topicName, seq))
                .build());

        builder.addAction(new Notification.Action.Builder(Icon.createWithResource(mContext, R.drawable.ic_call_white),
                getActionText(mContext, R.string.answer_call, R.color.colorPositiveAction), answerIntent(topicName, seq))
                .build());

        nm.notify(NOTIFICATION_TAG_INCOMING_CALL, 0, builder.build());
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

    private Spannable getActionText(Context context, @StringRes int stringRes, @ColorRes int colorRes) {
        Spannable spannable = new SpannableString(context.getText(stringRes));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            spannable.setSpan(
                    new ForegroundColorSpan(context.getColor(colorRes)), 0, spannable.length(), 0);
        }
        return spannable;
    }

    private PendingIntent askUserIntent(String topicName, int seq) {
        Intent intent = new Intent(CallActivity.INTENT_ACTION_CALL_INCOMING, null);
        intent.setFlags(Intent.FLAG_ACTIVITY_NO_USER_ACTION
                | Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra(Const.INTENT_EXTRA_TOPIC, topicName)
                .putExtra(Const.INTENT_EXTRA_SEQ, seq);
        intent.setClass(mContext, CallActivity.class);
        return PendingIntent.getActivity(mContext, 101, intent,
                PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private PendingIntent answerIntent(String topicName, int seq) {
        Intent intent = new Intent(CallActivity.INTENT_ACTION_CALL_INCOMING, null);
        intent.setFlags(Intent.FLAG_ACTIVITY_NO_USER_ACTION
                | Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra(Const.INTENT_EXTRA_TOPIC, topicName)
                .putExtra(Const.INTENT_EXTRA_SEQ, seq)
                .putExtra(Const.INTENT_EXTRA_CALL_ACCEPTED, true);
        intent.setClass(mContext, CallActivity.class);
        return PendingIntent.getActivity(mContext, 102, intent,
                PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private PendingIntent declineIntent(String topicName, int seq) {
        final Intent intent = new Intent(mContext, CallBroadcastReceiver.class);
        intent.setAction(CallBroadcastReceiver.ACTION_INCOMING_CALL);
        intent.putExtra(Const.INTENT_EXTRA_TOPIC, topicName);
        intent.putExtra(Const.INTENT_EXTRA_SEQ, seq);
        return PendingIntent.getBroadcast(mContext, 103, intent,
                PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}