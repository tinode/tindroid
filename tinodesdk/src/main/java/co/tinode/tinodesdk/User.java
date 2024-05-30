package co.tinode.tinodesdk;

import java.util.Date;

import co.tinode.tinodesdk.model.Description;
import co.tinode.tinodesdk.model.Mergeable;
import co.tinode.tinodesdk.model.Subscription;

/**
 * Information about specific user
 */
public class User<P> implements LocalData {

    public Date updated;
    public String uid;
    public P pub;

    private Payload mLocal = null;

    public User() {
    }

    public User(String uid) {
        this.uid = uid;
    }

    public User(Subscription<P,?> sub) {
        if (sub.user != null && !sub.user.isEmpty()) {
            uid = sub.user;
            updated = sub.updated;
            pub = sub.pub;
        } else {
            throw new IllegalArgumentException();
        }
    }

    public User(String uid, Description<P,?> desc) {
        this.uid = uid;
        updated = desc.updated;
        try {
            pub = desc.pub;
        } catch (ClassCastException ignored) {}
    }

    private boolean mergePub(P pub) {
        boolean changed = false;
        if (pub != null) {
            try {
                if (Tinode.isNull(pub)) {
                    this.pub = null;
                    changed = true;
                } else if (this.pub != null && (this.pub instanceof Mergeable)) {
                    changed = ((Mergeable) this.pub).merge((Mergeable) pub);
                } else {
                    this.pub = pub;
                    changed = true;
                }
            } catch (ClassCastException ignored) { }
        }
        return changed;
    }

    public boolean merge(User<P> user) {
        boolean changed = false;

        if ((user.updated != null) && (updated == null || updated.before(user.updated))) {
            updated = user.updated;
            changed = mergePub(user.pub);
        } else if (pub == null && user.pub != null) {
            pub = user.pub;
            changed = true;
        }

        return changed;
    }

    public boolean merge(Subscription<P,?> sub) {
        boolean changed = false;

        if ((sub.updated != null) && (updated == null || updated.before(sub.updated))) {
            updated = sub.updated;
            changed = mergePub(sub.pub);
        } else if (pub == null && sub.pub != null) {
            pub = sub.pub;
            changed = true;
        }

        return changed;
    }

    public boolean merge(Description<P,?> desc) {
        boolean changed = false;

        if ((desc.updated != null) && (updated == null || updated.before(desc.updated))) {
            updated = desc.updated;
            changed = mergePub(desc.pub);
        } else if (pub == null && desc.pub != null) {
            pub = desc.pub;
            changed = true;
        }

        return changed;
    }

    @Override
    public void setLocal(Payload value) {
        mLocal = value;
    }

    @Override
    public Payload getLocal() {
        return mLocal;
    }
}
