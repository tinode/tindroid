package co.tinode.tinodesdk.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.Date;

import co.tinode.tinodesdk.LocalData;

/**
 * Subscription to topic.
 */
public class Subscription<SP,SR> implements LocalData, Serializable {
    public String user;
    public Date updated;
    public Date deleted;
    public Date touched;

    public Acs acs;
    public int read;
    public int recv;
    @JsonProperty("private")
    public SR priv;
    public Boolean online;

    public String topic;
    public int seq;
    public int clear;
    @JsonProperty("public")
    public SP pub;
    public LastSeen seen;

    // Local values
    @JsonIgnore
    private Payload mLocal;

    public Subscription() {
    }

    public boolean merge(Subscription<SP,SR> sub) {
        int changed = 0;

        if (user == null && sub.user != null && !sub.user.equals("")) {
            user = sub.user;
            changed ++;
        }

        if ((sub.updated != null) && (updated == null || updated.before(sub.updated))) {
            updated = sub.updated;

            if (sub.pub != null) {
                pub = sub.pub;
            }

            changed ++;
        } else if (pub == null && sub.pub != null) {
            pub = sub.pub;
        }

        if ((sub.touched != null) && (touched == null || touched.before(sub.touched))) {
            touched = sub.touched;
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
    public boolean merge(MetaSetSub sub) {
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

    public void updateAccessMode(AccessChange ac) {
        if (acs == null) {
            acs = new Acs();
        }
        acs.update(ac);
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