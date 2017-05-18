package co.tinode.tinodesdk.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.Map;

/**
 * Access mode.
 */
public class Acs {
    private AcsHelper given;
    private AcsHelper want;
    private AcsHelper mode;

    public Acs() {
        assign(null, null, null);
    }

    public Acs(String g, String w, String m) {
        assign(g, w, m);
    }

    public Acs(Acs am) {
        if (am != null) {
            given = am.given;
            want = am.want;
            mode = am.mode;
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

    public void setGiven(String g) {
        given = new AcsHelper(g);
    }
    public String getGiven() {
        return given.toString();
    }

    public void setWant(String w) {
        want = new AcsHelper(w);
    }
    public String getWant() {
        return want.toString();
    }

    public boolean merge(Acs am) {
        boolean changed = false;
        if (am != null && !equals(am)) {
            given = am.given;
            want = am.want;
            mode = am.mode;
            changed = true;
        }
        return changed;
    }

    public Acs updateMode(String u) {
        mode.update(u);
        return this;
    }
    public Acs updateGiven(String u) {
        given.update(u);
        return this;
    }
    public Acs updateWant(String u) {
        want.update(u);
        return this;
    }

    public boolean equals(Acs am) {
        return am != null && mode.equals(am.mode) && want.equals(am.want) && mode.equals(am.mode);
    }

    public boolean modeEquals(Acs am) {
        return am != null && mode.equals(am.mode);
    }
    public boolean modeEquals(String m) {
        return mode.equals(m);
    }

    public boolean wantEquals(Acs am) {
        return am != null && want.equals(am.want);
    }
    public boolean wantEquals(String w) {
        return want.equals(w);
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
