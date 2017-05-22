package co.tinode.tinodesdk.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.Map;

/**
 * Access mode.
 */
public class Acs {
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
        boolean changed = false;
        if (am != null && !equals(am)) {
            given = new AcsHelper(am.given);
            want = new AcsHelper(am.want);
            mode = new AcsHelper(am.mode);
            changed = true;
        }
        return changed;
    }

    public boolean equals(Acs am) {
        return am != null && mode.equals(am.mode) && want.equals(am.want) && mode.equals(am.mode);
    }

    public boolean canRead() {
        return mode.canRead();
    }

    public boolean canWrite() {
        return mode.canWrite();
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
    public boolean canDelete() {
        return mode.canDelete();
    }
    public boolean isOwner() {
        return mode.isOwner();
    }
    public boolean isBanned() {
        return mode.isBanned();
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

}
