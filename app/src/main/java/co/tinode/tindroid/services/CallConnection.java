package co.tinode.tindroid.services;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.telecom.CallAudioState;
import android.telecom.Connection;
import android.util.Log;

import androidx.annotation.Nullable;
import co.tinode.tindroid.CallActivity;
import co.tinode.tindroid.R;

public class CallConnection extends Connection {
    private static final String TAG = "CallConnection";
    private final Context mContext;

    CallConnection(Context ctx) {
        super();
        mContext = ctx;
    }

    @Override
    public void onShowIncomingCallUi() {
        Log.i(TAG, "onShowIncomingCallUi");

        Intent intent = new Intent(Intent.ACTION_MAIN, null);
        intent.setFlags(Intent.FLAG_ACTIVITY_NO_USER_ACTION | Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setClass(mContext, CallActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(mContext, 1, intent,
                PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = new Notification.Builder(mContext);
        builder.setOngoing(true);
        builder.setPriority(Notification.PRIORITY_HIGH);

        // Set notification content intent to take user to fullscreen UI if user taps on the
        // notification body.
        builder.setContentIntent(pendingIntent);
        // Set full screen intent to trigger display of the fullscreen UI when the notification
        // manager deems it appropriate.
        builder.setFullScreenIntent(pendingIntent, true);

        // Setup notification content.
        builder.setSmallIcon(R.mipmap.ic_launcher);
        builder.setContentTitle("Notification title");
        builder.setContentText("Explanation.");

        // Use builder.addAction(..) to add buttons to answer or reject the call.

        NotificationManager notificationManager = mContext.getSystemService(NotificationManager.class);

        notificationManager.notify("Call Notification", 12345, builder.build());
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
        Log.i(TAG, "onDisconnect");
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