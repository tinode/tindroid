package co.tinode.tindroid;

import android.telecom.Connection;
import android.telecom.DisconnectCause;

import androidx.annotation.NonNull;
import co.tinode.tindroid.services.CallConnection;

/**
 * Struct to hold video call metadata.
 */
public class CallInProgress {
    // Call topic.
    private final String mTopic;
    // Telephony connection.
    private final CallConnection mConnection;
    // Call seq id.
    private int mSeq = 0;

    public CallInProgress(@NonNull String topic, @NonNull CallConnection conn) {
        mTopic = topic;
        mConnection = conn;
    }

    public void setCallActive(@NonNull String topic, int seqId) {
        if (mTopic.equals(topic) || mSeq > 0) {
            mSeq = seqId;
            mConnection.setActive();
        } else {
            throw new IllegalArgumentException("Call seq is already assigned");
        }
    }

    public void endCall() {
        if (mConnection.getState() != Connection.STATE_DISCONNECTED) {
            mConnection.setDisconnected(new DisconnectCause(DisconnectCause.LOCAL));
            mConnection.destroy();
        }
    }

    public boolean equals(String topic, int seq) {
        return mTopic.equals(topic) && mSeq == seq;
    }
}
