package co.tinode.tinodesdk.model;

/**
 * Class describing default access to topic
 */
public class Defacs {
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
}