package co.tinode.tindroid.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.text.TextUtils;
import android.util.Log;
import android.widget.RemoteViews;

import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.util.Timer;
import java.util.TimerTask;

import co.tinode.tindroid.BuildConfig;
import co.tinode.tindroid.Cache;
import co.tinode.tindroid.IncomingCallActivity;
import co.tinode.tindroid.MessageActivity;
import co.tinode.tindroid.R;
import co.tinode.tindroid.UiUtils;
import co.tinode.tindroid.media.VxCard;
import co.tinode.tinodesdk.ComTopic;
import co.tinode.tinodesdk.Tinode;
import co.tinode.tinodesdk.Topic;
import co.tinode.tinodesdk.User;
import co.tinode.tinodesdk.model.MsgServerInfo;

/**
 * Handles incoming video calls: notification display and routing.
 */
public class CallService {
    private static final String TAG = "CallService";

    public static int NOTIFICATION_ID = 4096;
    public static int AVATAR_SIZE = 128;

    /*
    private void showCallInviteNotification(RemoteViews notifView, PendingIntent notifIntent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager notificationManager = (NotificationManager)
                    getSystemService(Context.NOTIFICATION_SERVICE);
            NotificationChannel notificationChannel =
                    new NotificationChannel("IncomingCall",
                            "IncomingCall", NotificationManager.IMPORTANCE_HIGH);
            notificationChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);

            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .build();
            Uri ringtoneSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
            notificationChannel.setSound(ringtoneSound, audioAttributes);
            notificationChannel.setDescription("Tinode incoming video call notification.");
            notificationChannel.enableLights(true);
            notificationChannel.setLightColor(Color.RED);
            notificationChannel.setVibrationPattern(new long[]{0, 1000, 500, 1000});
            notificationChannel.enableVibration(true);
            notificationManager.createNotificationChannel(notificationChannel);

            String incomingCallText = getResources().getString(R.string.incoming_call);

            NotificationCompat.Builder notification =
                    new NotificationCompat.Builder(getApplicationContext(), "IncomingCall")
                            .setContentTitle(BuildConfig.APPLICATION_ID)
                            .setTicker(incomingCallText)
                            .setContentText(incomingCallText)
                            .setSmallIcon(R.drawable.ic_icon_push)
                            .setDefaults(Notification.DEFAULT_VIBRATE)
                            .setCategory(NotificationCompat.CATEGORY_CALL)
                            .setSound(null)
                            .setOngoing(true)
                            .setContentIntent(notifIntent)
                            .setFullScreenIntent(notifIntent, true)
                            .setPriority(NotificationCompat.PRIORITY_MAX)
                            .setStyle(new NotificationCompat.DecoratedCustomViewStyle())
                            .setCustomContentView(notifView)
                            .setCustomBigContentView(notifView);

            startForeground(NOTIFICATION_ID, notification.build());
        } else {
            // TODO: implement
            Log.d(TAG, "version lt O");
        }
    }

    private int showCallInvite(Intent intent) {
        String topic = intent.getStringExtra("topic");
        String from = intent.getStringExtra("from");
        int seq = intent.getIntExtra("seq", -1);
        if (TextUtils.isEmpty(topic) || TextUtils.isEmpty(from) || seq < 0) {
            Log.w(TAG, "Invalid call parameters: topic='" + topic + "'; from='" + from + "'; seq=" + seq);
            return START_NOT_STICKY;
        }

        Log.d(TAG, "Incoming call received: " + topic + ":" + seq);
        // Prepare notification view and intents.
        RemoteViews customView = new RemoteViews(BuildConfig.APPLICATION_ID, R.layout.call_notification_collapsed);
        //noinspection ConstantConditions
        User<VxCard> sender = mTinode.getUser(from);
        if (sender != null && sender.pub != null) {
            customView.setTextViewText(R.id.name, sender.pub.fn);
            customView.setImageViewBitmap(R.id.avatar,
                    UiUtils.avatarBitmap(this, sender.pub, Topic.TopicType.P2P, from, AVATAR_SIZE));
        } else {
            customView.setTextViewText(R.id.name, BuildConfig.APPLICATION_ID);
        }

        Intent notificationIntent = new Intent(this, IncomingCallActivity.class);
        notificationIntent.putExtra("topic", topic);
        notificationIntent.putExtra("seq", seq);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent acceptIntent = new Intent(this, CallService.class);
        acceptIntent.setAction("accept");
        acceptIntent.putExtra("topic", topic);
        acceptIntent.putExtra("seq", seq);
        PendingIntent acceptPI = PendingIntent.getService(
                this, 0, acceptIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent declineIntent = new Intent(this, CallService.class);
        declineIntent.setAction("decline");
        declineIntent.putExtra("topic", topic);
        declineIntent.putExtra("seq", seq);
        PendingIntent declinePI = PendingIntent.getService(
                this, 0, declineIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        customView.setOnClickPendingIntent(R.id.btnAccept, acceptPI);
        customView.setOnClickPendingIntent(R.id.btnDecline, declinePI);
        // Notification view and intent are ready. Present notification.
        showCallInviteNotification(customView, pendingIntent);
        cleanUp();
        // Dismiss notification after 40 seconds since we've lost "{info accept|hang-up}".
        // TODO: receive call timeout from the server instead of hardcoding 40 seconds here.
        mTimer = new Timer();
        mTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                CallService.this.stopService();
            }
        }, 40000);
        return START_STICKY;
    }

     */
}