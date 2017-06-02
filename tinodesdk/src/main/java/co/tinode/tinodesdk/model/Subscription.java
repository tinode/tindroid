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
    public Acs acs;
    public int read;
    public int recv;
    @JsonProperty("private")
    public Pr priv;
    public boolean online;

    public String topic;
    public int seq;
    public int clear;
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

        if (sub.acs != null) {
            if (acs == null) {
                acs = new Acs(sub.acs);
                changed++;
            } else {
                changed += acs.merge(sub.acs) ? 1 : 0;
            }
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

    /**
     * Merge changes from {meta set} packet with the subscription.
     */
    public boolean merge(MetaSetSub<?> sub) {
        int changed = 0;

        if (sub.mode != null && acs == null) {
            acs = new Acs();
        }

        if (sub.user != null && !sub.user.equals("")) {
            if (user == null) {
                user = sub.user;
                changed++;
            }
            if (sub.mode != null) {
                acs.setGiven(sub.mode);
                changed++;
            }
        } else {
            if (sub.mode != null) {
                acs.setWant(sub.mode);
                changed++;
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