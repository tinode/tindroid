package co.tinode.tindroid;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import co.tinode.tinodesdk.Topic;

/**
 * Receives broadcasts to hang up or decline video/audio call.
 */
public class HangUpBroadcastReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        // Clear incoming call notification.
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        nm.cancel(CallManager.NOTIFICATION_TAG_INCOMING_CALL, 0);

        if (Const.INTENT_ACTION_CALL_CLOSE.equals(intent.getAction())) {
            String topicName = intent.getStringExtra(Const.INTENT_EXTRA_TOPIC);
            int seq = intent.getIntExtra(Const.INTENT_EXTRA_SEQ, -1);
            Topic topic = Cache.getTinode().getTopic(topicName);
            if (topic != null && seq > 0) {
                // Send message to server that the call is declined.
                topic.videoCallHangUp(seq);
            }
            Cache.endCallInProgress();
        }
    }
}
