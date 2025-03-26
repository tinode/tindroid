package co.tinode.tindroid;

import android.telecom.CallAudioState;
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
    private int mSeq;
    // True if this call is established and connected between this client and the peer.
    private boolean mConnected = false;
    // True if the call is outgoing.
    private final boolean mIsOutgoing;

    public CallInProgress(@NonNull String topic, int seq, @Nullable CallConnection conn) {
        mTopic = topic;
        mSeq = seq;
        // Incoming calls will have a seq id.
        mIsOutgoing = seq == 0;
        mConnection = conn;
    }

    public boolean isOutgoingCall() {
        return mIsOutgoing;
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
        if (mConnection != null && mConnection.getState() == Connection.STATE_INITIALIZING) {
            mConnection.setInitialized();
        }
    }

    public synchronized void endCall() {
        mConnected = false;
        if (mConnection != null) {
            if (mConnection.getState() != Connection.STATE_DISCONNECTED) {
                mConnection.setDisconnected(new DisconnectCause(DisconnectCause.LOCAL));
            }
            mConnection.destroy();
            mConnection = null;
        }
    }

    public boolean isConnectionUseful() {
        return mConnection != null && mConnection.getState() != Connection.STATE_DISCONNECTED;
    }

    public boolean setAudioRoute(int route) {
        if (mConnection != null) {
            mConnection.setAudioRoute(route);
            return true;
        }
        return false;
    }

    public int getAudioRoute() {
        CallAudioState state = mConnection != null ? mConnection.getCallAudioState() : null;
        return state != null ? state.getRoute() : CallAudioState.ROUTE_EARPIECE;
    }

    public boolean equals(String topic, int seq) {
        return mTopic.equals(topic) && mSeq == seq;
    }

    public boolean isConnected() {
        return mConnected;
    }

    @Override
    @NonNull
    public String toString() {
        return mTopic + ":" + mSeq + "@" + mConnection;
    }
}
