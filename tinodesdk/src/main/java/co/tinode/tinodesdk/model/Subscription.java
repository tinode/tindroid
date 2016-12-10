package co.tinode.tinodesdk.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Date;

import co.tinode.tinodesdk.LocalData;
import co.tinode.tinodesdk.Topic;

/**
 * Subscription to topic.
 */
public class Subscription<Pu,Pr> implements LocalData {
    public String user;
    public Date updated;
    public Date deleted;
    public String mode;
    public int read;
    public int recv;
    @JsonProperty("private")
    public Pr priv;
    public boolean online;

    public String topic;
    public int seq;
    public int clear;
    public String with;
    @JsonProperty("public")
    public Pu pub;
    public LastSeen seen;

    // Local values
    @JsonIgnore
    private Payload mLocal;

    public Subscription() {
    }

    public boolean merge(Subscription<Pu,Pr> sub) {
        int changed = 0;

        if (user == null && sub.user != null && !sub.user.equals("")) {
            user = sub.user;
            changed ++;
        }

        if ((sub.updated != null) && (updated == null || updated.before(sub.updated))) {
            updated = sub.updated;
            changed ++;
        }

        if ((sub.deleted != null) && (deleted == null || deleted.after(sub.deleted))) {
            deleted = sub.deleted;
            changed ++;
        }

        if (sub.mode != null) {
            mode = sub.mode;
            changed ++;
        }
        if (sub.read > read) {
            read = sub.read;
            changed ++;
        }
        if (sub.recv > recv) {
            recv = sub.recv;
            changed ++;
        }
        if (sub.clear > clear) {
            clear = sub.clear;
            changed ++;
        }

        if (sub.priv != null) {
            priv = sub.priv;
        }
        online = sub.online;

        if ((topic == null || topic.equals("")) && sub.topic != null && !sub.topic.equals("")) {
            topic = sub.topic;
            changed ++;
        }
        if (sub.seq > seq) {
            seq = sub.seq;
            changed ++;
        }
        if (sub.with != null && !sub.with.equals("") && (with == null || !with.equals(sub.with))) {
            with = sub.with;
            changed ++;
        }
        if (sub.pub != null) {
            pub = sub.pub;
        }

        if (sub.seen != null) {
            if (seen == null) {
                seen = sub.seen;
                changed ++;
            } else {
                changed += seen.merge(sub.seen) ? 1 : 0;
            }
        }

        return changed > 0;
    }

    @Override
    @JsonIgnore
    public void setLocal(Payload value) {
        mLocal = value;
    }

    @Override
    @JsonIgnore
    public Payload getLocal() {
        return mLocal;
    }
}