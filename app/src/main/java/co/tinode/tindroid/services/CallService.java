package co.tinode.tindroid.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.AudioAttributes;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import android.widget.RemoteViews;

import androidx.core.app.NotificationCompat;

import co.tinode.tindroid.BuildConfig;
import co.tinode.tindroid.Cache;
import co.tinode.tindroid.IncomingCallActivity;
import co.tinode.tindroid.MessageActivity;
import co.tinode.tindroid.R;
import co.tinode.tindroid.UiUtils;
import co.tinode.tindroid.media.VxCard;
import co.tinode.tinodesdk.ComTopic;
import co.tinode.tinodesdk.Tinode;
import co.tinode.tinodesdk.User;
import co.tinode.tinodesdk.model.MsgServerInfo;

/**
 * Handles incoming video calls: notification display and routing.
 */
public class CallService extends Service {
    private static final String TAG = "CallService";
    private Tinode mTinode;
    private EventListener mListener;
    public static int NOTIFICATION_ID = 4096;

    private class EventListener extends Tinode.EventListener {
        @Override
        public void onInfoMessage(MsgServerInfo info) {
            Log.d(TAG, "Remote hangup: " + info.toString());
            if (info.what.equals("call") && info.event.equals("hang-up")) {
                Log.d(TAG, "Remote hangup");
                stopForeground(true);
            }
        }
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "Destroying service");
        mTinode.removeListener(mListener);
        super.onDestroy();
    }

    @Override
    public void onCreate() {
        Log.d(TAG, "Creating service");
        mTinode = Cache.getTinode();
        mListener = new EventListener();
        mTinode.addListener(mListener);
        super.onCreate();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private int declineCall(Intent intent) {
        String topicName = intent.getStringExtra("topic");
        int seq = intent.getIntExtra("seq", -1);
        Log.d(TAG, "Call declined: " + topicName + ":" + seq);
        ComTopic<VxCard> topic = (ComTopic<VxCard>)mTinode.getTopic(topicName);
        if (topic != null) {
            topic.videoCall("hang-up", seq, null);
        }
        stopForeground(true);
        return START_NOT_STICKY;
    }

    private int acceptCall(Intent intent) {
        String topicName = intent.getStringExtra("topic");
        int seq = intent.getIntExtra("seq", -1);
        Log.d(TAG, "Call accepted: " + topicName + ":" + seq);
        Intent acceptIntent = new Intent(this, MessageActivity.class);
        acceptIntent.setAction("incoming_call");
        acceptIntent.putExtra("topic", topicName);
        acceptIntent.putExtra("seq", seq);
        acceptIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(acceptIntent);
        // Remove notification.
        stopForeground(true);
        return START_NOT_STICKY;
    }

    private void showCallInviteNotification(RemoteViews notifView, PendingIntent notifIntent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager notificationManager = (NotificationManager)
                    getSystemService(Context.NOTIFICATION_SERVICE);
            NotificationChannel notificationChannel =
                    new NotificationChannel("IncomingCall",
                            "IncomingCall", NotificationManager.IMPORTANCE_HIGH);
            notificationChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            notificationManager.createNotificationChannel(notificationChannel);

            Uri ringtoneSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
            //Ringtone r = RingtoneManager.getRingtone(getApplicationContext(), ringtoneSound);
            //r.play();
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .build();
            notificationChannel.setSound(ringtoneSound, audioAttributes);
            notificationChannel.setDescription("abnc3443");
            notificationChannel.enableLights(true);
            notificationChannel.setLightColor(Color.RED);
            notificationChannel.setVibrationPattern(new long[]{0, 1000, 500, 1000});
            notificationChannel.enableVibration(true);

            NotificationCompat.Builder notification =
                    new NotificationCompat.Builder(getApplicationContext(), "IncomingCall")
                            .setContentTitle(BuildConfig.APPLICATION_ID)
                            .setTicker("Call")
                            .setContentText("IncomingCall")
                            .setSmallIcon(R.drawable.ic_icon_push)
                            .setDefaults(Notification.DEFAULT_LIGHTS | Notification.DEFAULT_SOUND)
                            .setCategory(NotificationCompat.CATEGORY_CALL)
                            //.setVibrate(null)
                            .setSound(ringtoneSound)
                            .setOngoing(true)
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
        if (topic == null || from == null || topic.isEmpty() || seq < 0 || from.isEmpty()) {
            Log.w(TAG, "Invalid call intent: " + intent.toString());
            return START_NOT_STICKY;
        }

        Log.d(TAG, "Incoming call received: " + topic + ":" + seq);
        // Prepare notification view and intents.
        RemoteViews customView = new RemoteViews(BuildConfig.APPLICATION_ID,
                R.layout.call_notification);
        User<VxCard> sender = mTinode.getUser(from);
        if (sender != null && sender.pub != null) {
            customView.setTextViewText(R.id.name, sender.pub.fn);
            Drawable local = UiUtils.avatarDrawable(this, sender.pub.getBitmap(), sender.pub.fn, from);
            customView.setImageViewBitmap(R.id.photo, ((BitmapDrawable)local).getBitmap());
        } else {
            customView.setTextViewText(R.id.name, BuildConfig.APPLICATION_ID);
        }

        Intent notificationIntent = new Intent(this, IncomingCallActivity.class);
        notificationIntent.putExtra("topic", topic);
        notificationIntent.putExtra("seq", seq);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT);

        Intent acceptIntent = new Intent(this, CallService.class);
        acceptIntent.setAction("accept");
        acceptIntent.putExtra("topic", topic);
        acceptIntent.putExtra("seq", seq);
        PendingIntent acceptPI = PendingIntent.getService(
                this, 0, acceptIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        Intent declineIntent = new Intent(this, CallService.class);
        declineIntent.setAction("decline");
        declineIntent.putExtra("topic", topic);
        declineIntent.putExtra("seq", seq);
        PendingIntent declinePI = PendingIntent.getService(
                this, 0, declineIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        customView.setOnClickPendingIntent(R.id.btnAccept, acceptPI);
        customView.setOnClickPendingIntent(R.id.btnDecline, declinePI);
        // Notification view and intent are ready. Present notification.
        showCallInviteNotification(customView, pendingIntent);
        return START_STICKY;
    }

    private int dismissCall(Intent intent) {
        stopForeground(true);
        return START_NOT_STICKY;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent.getAction();
        switch (action) {
            case "decline":
                return declineCall(intent);
            case "accept":
                return acceptCall(intent);
            case "invite":
                return showCallInvite(intent);
            case "dismiss":
                return dismissCall(intent);
            default:
                return START_NOT_STICKY;
        }
    }
}