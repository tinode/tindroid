package co.tinode.tinodesdk.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Date;

import co.tinode.tinodesdk.Topic;

/**
 * Subscription to topic.
 */
public class Subscription<Pu,Pr> {
    public String user;
    public Date updated;
    public Date deleted;
    public String mode;
    public int read;
    public int recv;
    public int clear;
    @JsonProperty("private")
    public Pr priv;
    public boolean online;

    public String topic;
    public int seq;
    public String with;
    @JsonProperty("public")
    public Pu pub;
    public LastSeen seen;

    // Locally-assigned value;
    @JsonIgnore
    protected int mTopicIndex;

    public Subscription() {
    }

    public void merge(Subscription<Pu,Pr> sub) {
        if (user == null && sub.user != null && !sub.user.equals("")) {
            user = sub.user;
        }
        if ((sub.updated != null) && (updated == null || updated.before(sub.updated))) {
            updated = sub.updated;
        }
        if ((sub.deleted != null) && (deleted == null || deleted.after(sub.deleted))) {
            deleted = sub.deleted;
        }

        if (sub.mode != null) {
            mode = sub.mode;
        }
        if (sub.read > read) {
            read = sub.read;
        }
        if (sub.recv > recv) {
            recv = sub.recv;
        }
        if (sub.clear > clear) {
            clear = sub.clear;
        }
        if (sub.priv != null) {
            priv = sub.priv;
        }
        online = sub.online;

        if ((topic == null || topic.equals("")) && sub.topic != null && !sub.topic.equals("")) {
            topic = sub.topic;
        }
        if (sub.seq > seq) {
            seq = sub.seq;
        }
        if (sub.with != null && !sub.with.equals("")) {
            with = sub.with;
        }
        if (sub.pub != null) {
            pub = sub.pub;
        }
        if (seen == null) {
            seen = sub.seen;
        } else {
            seen.merge(sub.seen);
        }
    }

    /**
     * @return "Sender index" - small unique integer value to identify this topic among other topics.
     */
    public int getTopicIndex() {
        return mTopicIndex;
    }

    public void setTopicIndex(int index) {
        mTopicIndex = index;
    }

    public String getUniqueId() {
        if (topic == null) {
            return user;
        } else {
            if (Topic.getTopicTypeByName(topic) == Topic.TopicType.P2P) {
                return with;
            } else {
                return topic;
            }
        }
    }
}