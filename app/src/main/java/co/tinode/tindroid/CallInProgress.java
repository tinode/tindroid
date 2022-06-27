package co.tinode.tindroid;

/**
 * Struct to hold video call metadata.
 */
public class CallInProgress {
    // Call topic.
    private String mTopic;
    // Call seq id.
    private int mSeq;

    public CallInProgress(String topic, int  seq) {
        mTopic = topic;
        mSeq = seq;
    }

    public boolean equals(String topic, int seq) {
        return mTopic.equals(topic) && mSeq == seq;
    }
}
