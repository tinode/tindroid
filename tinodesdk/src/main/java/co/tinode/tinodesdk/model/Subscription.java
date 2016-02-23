package co.tinode.tinodesdk.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Date;

/**
 * Created by gene on 13/02/16.
 */
public class Subscription<Pu,Pr> {
    public String user;
    public Date updated;
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

        if (topic == null && sub.topic != null && !sub.topic.equals("")) {
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

    public int getTopicIndex() {
        return mTopicIndex;
    }

    public void setTopicIndex(int index) {
        mTopicIndex = index;
    }
}