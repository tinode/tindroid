package co.tinode.tinodesdk;

import java.util.Date;

import co.tinode.tinodesdk.model.Description;
import co.tinode.tinodesdk.model.Subscription;

/**
 * Information about specific user
 */
public class User<Pu> implements LocalData {

    public Date updated;
    public String uid;
    public Pu pub;

    private Payload mLocal = null;

    public User() {
    }

    public User(String uid) {
        this.uid = uid;
    }

    public User(Subscription<Pu,?> sub) {
        if (sub.user != null && sub.user.length() > 0) {
            uid = sub.user;
            updated = sub.updated;
            pub = sub.pub;
        } else {
            throw new IllegalArgumentException();
        }
    }

    public User(String uid, Description<Pu,?> desc) {
        this.uid = uid;
        updated = desc.updated;
        pub = desc.pub;
    }

    public boolean merge(User<Pu> user) {
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

    public boolean merge(Subscription<Pu, ?> sub) {
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

    public boolean merge(Description<Pu,?> desc) {
        boolean changed = false;

        if ((desc.updated != null) && (updated == null || updated.before(desc.updated))) {
            updated = desc.updated;

            if (desc.pub != null) {
                pub = desc.pub;
            }

            changed = true;
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
