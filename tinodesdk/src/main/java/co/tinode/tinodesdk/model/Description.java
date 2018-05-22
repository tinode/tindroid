package co.tinode.tinodesdk.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.Date;

/**
 * Topic description as deserialized from the server packet.
 */
public class Description<DP,DR> implements Serializable {
    public Date created;
    public Date updated;
    public Date touched;

    public Defacs defacs;
    public Acs acs;
    public int seq;
    // Values reported by the current user as read and received
    public int read;
    public int recv;
    public int clear;
    @JsonProperty("public")
    public DP pub;
    @JsonProperty("private")
    public DR priv;

    public Description() {
    }

    /**
     * Copy non-null values to this object.
     *
     * @param desc object to copy.
     */
    public boolean merge(Description<DP,DR> desc) {
        int changed = 0;

        if (created == null && desc.created != null) {
            created = desc.created;
            changed ++;
        }
        if (desc.updated != null && (updated == null || updated.before(desc.updated))) {
            updated = desc.updated;
            changed ++;
        }
        if (desc.touched != null && (touched == null || touched.before(desc.touched))) {
            touched = desc.touched;
            changed ++;
        }

        if (desc.defacs != null) {
            if (defacs == null) {
                defacs = desc.defacs;
                changed ++;
            } else {
                changed += defacs.merge(desc.defacs) ? 1 : 0;
            }
        }

        if (desc.acs != null) {
            if (acs == null) {
                acs = desc.acs;
                changed++;
            } else {
                changed += acs.merge(desc.acs) ? 1 : 0;
            }
        }

        if (desc.seq > seq) {
            seq = desc.seq;
            changed ++;
        }
        if (desc.read > read) {
            read = desc.read;
            changed ++;
        }
        if (desc.recv > recv) {
            recv = desc.recv;
            changed ++;
        }
        if (desc.clear > clear) {
            clear = desc.clear;
            changed ++;
        }

        if (desc.pub != null) {
            pub = desc.pub;
        }

        if (desc.priv != null) {
            priv = desc.priv;
        }

        return changed > 0;
    }

    /**
     * Merge subscription into a description
     */
    public <SP,SR> boolean merge(Subscription<SP,SR> sub) {
        int changed = 0;

        if (sub.updated != null && (updated == null || updated.before(sub.updated))) {
            updated = sub.updated;
            changed++;
        }

        if (sub.seq > seq) {
            seq = sub.seq;
            changed++;
        }

        if (sub.read > read) {
            read = sub.read;
            changed++;
        }

        if (sub.recv > recv) {
            recv = sub.recv;
            changed++;
        }

        if (sub.clear > clear) {
            clear = sub.clear;
            changed++;
        }

        if (sub.pub != null) {
            // This may throw a ClassCastException.
            // This is intentional behavior to catch cases of wrong assignment.
            pub = (DP) sub.pub;
            changed++;
        }

        if (sub.priv != null) {
            try {
                priv = (DR) sub.priv;
                changed++;
            } catch (ClassCastException ignored) {}

        }

        return changed > 0;
    }

    public boolean merge(MetaSetDesc<DP,DR> desc) {
        int changed = 0;

        if (desc.defacs != null) {
            if (defacs == null) {
                defacs = desc.defacs;
                changed ++;
            } else {
                changed += defacs.merge(desc.defacs) ? 1 : 0;
            }
        }

        if (desc.pub != null) {
            pub = desc.pub;
        }

        if (desc.priv != null) {
            priv = desc.priv;
        }

        return changed > 0;
    }

}
