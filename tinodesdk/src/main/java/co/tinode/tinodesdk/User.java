package co.tinode.tinodesdk;

import java.util.Date;

import co.tinode.tinodesdk.model.Description;
import co.tinode.tinodesdk.model.Subscription;
import co.tinode.tinodesdk.model.VCard;

/**
 * Information about specific user
 */
public class User implements LocalData {

    public Date updated;
    public String uid;
    public VCard pub;

    private Payload mLocal = null;

    public User() {
    }

    public User(String uid) {
        this.uid = uid;
    }

    public User(Subscription sub) {
        if (sub.user != null && sub.user.length() > 0) {
            uid = sub.user;
            updated = sub.updated;
            pub = sub.pub;
        } else {
            throw new IllegalArgumentException();
        }
    }

    public <P> User(String uid, Description<P,?> desc) {
        this.uid = uid;
        updated = desc.updated;
        try {
            pub = (VCard) desc.pub;
        } catch (ClassCastException ignored) {}
    }

    public boolean merge(User user) {
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

    public boolean merge(Subscription sub) {
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

    public <P> boolean merge(Description<P,?> desc) {
        boolean changed = false;
        try {
            if ((desc.updated != null) && (updated == null || updated.before(desc.updated))) {

                if (desc.pub != null) {
                    pub = (VCard) desc.pub;
                }
                updated = desc.updated;
                changed = true;
            } else if (pub == null && desc.pub != null) {
                pub = (VCard) desc.pub;
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
