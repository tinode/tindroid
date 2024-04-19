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
    public TrustedType trusted;
    public LastSeen seen;

    // Local values
    @JsonIgnore
    private Payload mLocal;

    public Subscription() {
    }

    public Subscription(Subscription<SP,SR> sub) {
        this.merge(sub);
        mLocal = null;
    }

    @JsonIgnore
    public String getUnique() {
        if (topic == null) {
            return user;
        }
        if (user == null) {
            return topic;
        }
        return topic + ":" + user;
    }

    /**
     * Merge two subscriptions.
     */
    public boolean merge(Subscription<SP,SR> sub) {
        boolean changed = false;

        if (user == null && sub.user != null && !sub.user.isEmpty()) {
            user = sub.user;
            changed = true;
        }

        if ((sub.updated != null) && (updated == null || updated.before(sub.updated))) {
            updated = sub.updated;

            if (sub.pub != null) {
                pub = sub.pub;
            }
            if (sub.trusted != null) {
                if (trusted == null) {
                    trusted = new TrustedType();
                }
                trusted.merge(sub.trusted);
            }
            changed = true;
        } else {
            if (pub == null && sub.pub != null) {
                pub = sub.pub;
                changed = true;
            }
            if (trusted == null && sub.trusted != null) {
                trusted = sub.trusted;
                changed = true;
            }
        }

        if ((sub.touched != null) && (touched == null || touched.before(sub.touched))) {
            touched = sub.touched;
        }

        if (sub.deleted != null) {
            deleted = sub.deleted;
        }

        if (sub.acs != null) {
            if (acs == null) {
                acs = new Acs(sub.acs);
                changed = true;
            } else {
                changed = acs.merge(sub.acs) || changed;
            }
        }

        if (sub.read > read) {
            read = sub.read;
            changed = true;
        }
        if (sub.recv > recv) {
            recv = sub.recv;
            changed = true;
        }
        if (sub.clear > clear) {
            clear = sub.clear;
            changed = true;
        }

        if (sub.priv != null) {
            priv = sub.priv;
        }

        if (sub.online != null) {
            online = sub.online;
        }

        if ((topic == null || topic.isEmpty()) && sub.topic != null && !sub.topic.isEmpty()) {
            topic = sub.topic;
            changed = true;
        }
        if (sub.seq > seq) {
            seq = sub.seq;
            changed = true;
        }

        if (sub.seen != null) {
            if (seen == null) {
                seen = sub.seen;
                changed = true;
            } else {
                changed = seen.merge(sub.seen) || changed;
            }
        }

        return changed;
    }

    /**
     * Merge changes from {meta set} packet with the subscription.
     */
    public boolean merge(MetaSetSub sub) {
        boolean changed = false;

        if (sub.mode != null && acs == null) {
            acs = new Acs();
        }

        if (sub.user != null && !sub.user.isEmpty()) {
            if (user == null) {
                user = sub.user;
                changed = true;
            }
            if (sub.mode != null) {
                acs.setGiven(sub.mode);
                changed = true;
            }
        } else {
            if (sub.mode != null) {
                acs.setWant(sub.mode);
                changed = true;
            }
        }

        return changed;
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