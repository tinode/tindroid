package co.tinode.tinodesdk.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.Date;

import co.tinode.tinodesdk.Tinode;

/**
 * Topic description as deserialized from the server packet.
 */
public class Description<DP, DR> implements Serializable {
    public Date created;
    public Date updated;
    public Date touched;

    public Boolean online;

    public Defacs defacs;
    public Acs acs;
    public int seq;
    // Values reported by the current user as read and received
    public int read;
    public int recv;
    public int clear;

    public boolean chan;

    @JsonProperty("public")
    public DP pub;
    @JsonProperty("private")
    public DR priv;
    public TrustedType trusted;
    public LastSeen seen;

    public Description() {
    }

    private boolean mergePub(DP spub) {
        boolean changed;
        if (Tinode.isNull(spub)) {
            pub = null;
            changed = true;
        } else {
            if (pub != null && (pub instanceof Mergeable)) {
                changed = ((Mergeable)pub).merge((Mergeable)spub);
            } else {
                pub = spub;
                changed = true;
            }
        }
        return changed;
    }

    private boolean mergePriv(DR spriv) {
        boolean changed;
        if (Tinode.isNull(spriv)) {
            priv = null;
            changed = true;
        } else {
            if (priv != null && (priv instanceof Mergeable)) {
                changed = ((Mergeable)priv).merge((Mergeable)spriv);
            } else {
                priv = spriv;
                changed = true;
            }
        }
        return changed;
    }

    /**
     * Copy non-null values to this object.
     *
     * @param desc object to copy.
     */
    public boolean merge(Description<DP,DR> desc) {
        boolean changed = false;

        if (created == null && desc.created != null) {
            created = desc.created;
            changed = true;
        }
        if (desc.updated != null && (updated == null || updated.before(desc.updated))) {
            updated = desc.updated;
            changed = true;
        }
        if (desc.touched != null && (touched == null || touched.before(desc.touched))) {
            touched = desc.touched;
            changed = true;
        }

        if (chan != desc.chan) {
            chan = desc.chan;
            changed = true;
        }

        if (desc.defacs != null) {
            if (defacs == null) {
                defacs = desc.defacs;
                changed = true;
            } else {
                changed = defacs.merge(desc.defacs) || changed;
            }
        }

        if (desc.acs != null) {
            if (acs == null) {
                acs = desc.acs;
                changed = true;
            } else {
                changed = acs.merge(desc.acs) || changed;
            }
        }

        if (desc.seq > seq) {
            seq = desc.seq;
            changed = true;
        }
        if (desc.read > read) {
            read = desc.read;
            changed = true;
        }
        if (desc.recv > recv) {
            recv = desc.recv;
            changed = true;
        }
        if (desc.clear > clear) {
            clear = desc.clear;
            changed = true;
        }

        if (desc.pub != null) {
            changed = mergePub(desc.pub) || changed;
        }

        if (desc.trusted != null) {
            if (trusted == null) {
                trusted = new TrustedType();
                changed = true;
            }
            changed = trusted.merge(desc.trusted) || changed;
        }

        if (desc.priv != null) {
            changed = mergePriv(desc.priv) || changed;
        }

        if (desc.online != null && desc.online != online) {
            online = desc.online;
            changed = true;
        }

        if (desc.seen != null) {
            if (seen == null) {
                seen = desc.seen;
                changed = true;
            } else {
                changed = seen.merge(desc.seen) || changed;
            }
        }

        return changed;
    }

    /**
     * Merge subscription into a description
     */
    public <SP,SR> boolean merge(Subscription<SP,SR> sub) {
        boolean changed = false;

        if (sub.updated != null && (updated == null || updated.before(sub.updated))) {
            updated = sub.updated;
            changed = true;
        }

        if (sub.touched != null && (touched == null || touched.before(sub.touched))) {
            touched = sub.touched;
            changed = true;
        }

        if (sub.acs != null) {
            if (acs == null) {
                acs = sub.acs;
                changed = true;
            } else {
                changed = acs.merge(sub.acs) || changed;
            }
        }

        if (sub.seq > seq) {
            seq = sub.seq;
            changed = true;
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

        if (sub.pub != null) {
            // This may throw a ClassCastException.
            // This is intentional behavior to catch cases of wrong assignment.
            //noinspection unchecked
            changed = mergePub((DP) sub.pub) || changed;
        }

        if (sub.trusted != null) {
            if (trusted == null) {
                trusted = new TrustedType();
                changed = true;
            }
            changed = trusted.merge(sub.trusted) || changed;
        }

        if (sub.priv != null) {
            try {
                //noinspection unchecked
                changed = mergePriv((DR)sub.priv) || changed;
            } catch (ClassCastException ignored) {}

        }

        if (sub.online != null && sub.online != online) {
            online = sub.online;
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

    public boolean merge(MetaSetDesc<DP,DR> desc) {
        boolean changed = false;

        if (desc.defacs != null) {
            if (defacs == null) {
                defacs = desc.defacs;
                changed = true;
            } else {
                changed = defacs.merge(desc.defacs);
            }
        }

        if (desc.pub != null) {
            changed = mergePub(desc.pub) || changed;
        }

        if (desc.priv != null) {
            changed = mergePriv(desc.priv) || changed;
        }

        return changed;
    }
}
