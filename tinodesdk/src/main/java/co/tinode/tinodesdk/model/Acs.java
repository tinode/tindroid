package co.tinode.tinodesdk.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.io.Serializable;
import java.util.Map;

/**
 * Access mode.
 */
public class Acs implements Serializable {
    AcsHelper given;
    AcsHelper want;
    AcsHelper mode;

    public Acs() {
        assign(null, null, null);
    }

    public Acs(String g, String w, String m) {
        assign(g, w, m);
    }

    public Acs(Acs am) {
        if (am != null) {
            given = new AcsHelper(am.given);
            want = new AcsHelper(am.want);
            mode = new AcsHelper(am.mode);
        }
    }

    public Acs(Map<String,String> am) {
        if (am != null) {
            assign(am.get("given"), am.get("want"), am.get("mode"));
        }
    }

    public Acs(AccessChange ac) {
        if (ac != null) {
            given = new AcsHelper("N");
            given.update(ac.given);
            want = new AcsHelper("N");
            want.update(ac.want);
            mode = AcsHelper.and(want, given);
        }
    }

    private void assign(String g, String w, String m) {
        this.given = new AcsHelper(g);
        this.want = new AcsHelper(w);
        this.mode = new AcsHelper(m);
    }

    public void setMode(String m) {
        mode = new AcsHelper(m);
    }
    public String getMode() {
        return mode.toString();
    }
    public AcsHelper getModeHelper() {
        return new AcsHelper(mode);
    }

    public void setGiven(String g) {
        given = new AcsHelper(g);
    }
    public String getGiven() {
        return given.toString();
    }
    public AcsHelper getGivenHelper() {
        return new AcsHelper(given);
    }

    public void setWant(String w) {
        want = new AcsHelper(w);
    }
    public String getWant() {
        return want.toString();
    }
    public AcsHelper getWantHelper() {
        return new AcsHelper(want);
    }

    public boolean merge(Acs am) {
        int change = 0;
        if (am != null && !equals(am)) {
            if (am.given != null) {
                change += given.merge(am.given) ? 1 : 0;
            }
            if (am.want != null) {
                change += want.merge(am.want) ? 1 : 0;
            }
            if (am.mode != null) {
                change += mode.merge(am.mode) ? 1 : 0;
            } else {
                AcsHelper m2 = AcsHelper.and(want, given);
                if (m2 != null) {
                    change += m2.equals(mode) ? 0 : 1;
                    mode = m2;
                }
            }
        }
        return change > 0;
    }

    public boolean merge(Map<String,String> am) {
        int change = 0;
        if (am != null) {
            if (am.get("given") != null) {
                change += given.merge(new AcsHelper(am.get("given"))) ? 1 : 0;
            }
            if (am.get("want") != null) {
                change += want.merge(new AcsHelper(am.get("want"))) ? 1 : 0;
            }
            if (am.get("mode") != null) {
                change += mode.merge(new AcsHelper(am.get("mode"))) ? 1 : 0;
            } else {
                AcsHelper m2 = AcsHelper.and(want, given);
                if (m2 != null) {
                    change += m2.equals(mode) ? 0 : 1;
                    mode = m2;
                }
            }
        }
        return change > 0;
    }

    public boolean update(AccessChange ac) {
        int change = 0;
        if (ac != null) {
            if (ac.given != null) {
                if (given != null) {
                    change += given.update(ac.given) ? 1 : 0;
                } else {
                    given = new AcsHelper(ac.given);
                    change += given.isDefined() ? 1 : 0;
                }
            }
            if (ac.want != null) {
                if (want != null) {
                    change += want.update(ac.want) ? 1 : 0;
                } else {
                    want = new AcsHelper(ac.want);
                    change += want.isDefined() ? 1 : 0;
                }
            }
            AcsHelper m2 = AcsHelper.and(want, given);
            if (m2 != null) {
                change += m2.equals(mode) ? 0 : 1;
                mode = m2;
            }
        }
        return change > 0;
    }

    public boolean equals(Acs am) {
        return am != null && mode.equals(am.mode) && want.equals(am.want) && mode.equals(am.mode);
    }

    public boolean isReader() {
        return mode.isReader();
    }

    public boolean isWriter() {
        return mode.isWriter();
    }

    public boolean isMuted() {
        return mode.isMuted();
    }

    @JsonIgnore
    public Acs setMuted(boolean v) {
        mode.setMuted(v);
        return this;
    }
    public boolean isAdmin() {
        return mode.isAdmin();
    }
    public boolean isDeleter() {
        return mode.isDeleter();
    }
    public boolean isOwner() {
        return mode.isOwner();
    }
    public boolean isJoiner() {
        return mode.isJoiner();
    }
    public boolean isModeDefined() {
        return mode.isDefined();
    }
    public boolean isGivenDefined() {
        return given.isDefined();
    }
    public boolean isWantDefined() {
        return want.isDefined();
    }
    public boolean isInvalid() {
        return mode.isInvalid();
    }

    @Override
    public String toString() {
        return "G:" + (given != null ? given.toString() : "null") +
                ";W:"+ (want != null ? want.toString() : "null");
    }
}
