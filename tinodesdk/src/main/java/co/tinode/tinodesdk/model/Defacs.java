package co.tinode.tinodesdk.model;

import java.io.Serializable;

/**
 * Class describing default access to topic
 */
public class Defacs implements Serializable {
    public AcsHelper auth;
    public AcsHelper anon;

    public Defacs() {
    }

    public Defacs(String auth, String anon) {
        setAuth(auth);
        setAnon(anon);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null) {
            return false;
        }

        if (this == o) {
            return true;
        }

        if (!(o instanceof Defacs)) {
            return false;
        }

        Defacs rhs = (Defacs) o;

        return (auth  == null ? rhs.auth == null : auth.equals(rhs.auth)) &&
                (anon  == null ? rhs.anon == null : anon.equals(rhs.anon));
    }

    public String getAuth() {
        return auth != null ? auth.toString() : null;
    }
    public void setAuth(String a) {
        auth = new AcsHelper(a);
    }

    public String getAnon() {
        return anon != null ? anon.toString() : null;
    }
    public void setAnon(String a) {
        anon = new AcsHelper(a);
    }

    public boolean merge(Defacs defacs) {
        int changed = 0;
        if (defacs.auth != null) {
            if (auth == null) {
                auth = defacs.auth;
                changed ++;
            } else {
                changed += auth.merge(defacs.auth) ? 1 : 0;
            }
        }
        if (defacs.anon != null) {
            if (anon == null) {
                anon = defacs.anon;
                changed ++;
            } else {
                changed += anon.merge(defacs.anon) ? 1 : 0;
            }
        }

        return changed > 0;
    }
}