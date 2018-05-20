package co.tinode.tinodesdk;

import java.util.Date;

import co.tinode.tinodesdk.model.Description;
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
        if (sub.user != null && sub.user.length() > 0) {
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

    public boolean merge(User<P> user) {
        boolean changed = false;

        if ((user.updated != null) && (updated == null || updated.before(user.updated))) {
            updated = user.updated;

            if (user.pub != null) {
                pub = user.pub;
            }

            changed = true;
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

            if (sub.pub != null) {
                pub = sub.pub;
            }

            changed = true;
        } else if (pub == null && sub.pub != null) {
            pub = sub.pub;
            changed = true;
        }

        return changed;
    }

    public boolean merge(Description<P,?> desc) {
        boolean changed = false;
        try {
            if ((desc.updated != null) && (updated == null || updated.before(desc.updated))) {

                if (desc.pub != null) {
                    pub = desc.pub;
                }
                updated = desc.updated;
                changed = true;
            } else if (pub == null && desc.pub != null) {
                pub = desc.pub;
                changed = true;
            }
        } catch (ClassCastException ignored) {}

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
