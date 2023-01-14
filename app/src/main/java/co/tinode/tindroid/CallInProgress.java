package co.tinode.tindroid;

import android.telecom.Connection;
import android.telecom.DisconnectCause;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import co.tinode.tindroid.services.CallConnection;

/**
 * Struct to hold video call metadata.
 */
public class CallInProgress {
    // Call topic.
    private final String mTopic;
    // Telephony connection.
    private CallConnection mConnection;
    // Call seq id.
    private int mSeq = 0;
    // True if this call is established and connected between this client and the peer.
    private boolean mConnected = false;

    public CallInProgress(@NonNull String topic, @Nullable CallConnection conn) {
        mTopic = topic;
        mConnection = conn;
    }

    public void setCallActive(@NonNull String topic, int seqId) {
        if (mTopic.equals(topic) && (mSeq == 0 || mSeq == seqId)) {
            mSeq = seqId;
            if (mConnection != null) {
                mConnection.setActive();
            }
        } else {
            throw new IllegalArgumentException("Call seq is already assigned");
        }
    }


    public void setCallConnected() {
        mConnected = true;
    }

    public void endCall() {
        if (mConnection != null) {
            if (mConnection.getState() != Connection.STATE_DISCONNECTED) {
                mConnection.setDisconnected(new DisconnectCause(DisconnectCause.LOCAL));
            }
            mConnection.destroy();
            mConnection = null;
        }
    }

    public boolean equals(String topic, int seq) {
        return mTopic.equals(topic) && mSeq == seq;
    }
    public boolean isConnected() { return mConnected; }
}
