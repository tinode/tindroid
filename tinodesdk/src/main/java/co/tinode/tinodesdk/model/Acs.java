package co.tinode.tinodesdk.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.io.Serializable;
import java.util.Map;

/**
 * Access mode.
 */
public class Acs implements Serializable {
    public enum Side {
        MODE(0), WANT(1), GIVEN(2);

        private int val;
        Side(int val) {
            this.val = val;
        }

        public int val() {return val;}
    }

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
        return am != null && mode.equals(am.mode) && want.equals(am.want) && given.equals(am.given);
    }

    /**
     * Check if mode Reader (R) flag is set.
     * @return true if flag is set.
     */
    public boolean isReader() {
        return mode != null && mode.isReader();
    }
    /**
     * Check if Reader (R) flag is set for the given side.
     * @return true if flag is set.
     */
    public boolean isReader(Side s) {
        switch (s) {
            case MODE:
                return mode != null && mode.isReader();
            case WANT:
                return want != null && want.isReader();
            case GIVEN:
                return given != null && given.isReader();
        }
        return false;
    }

    /**
     * Check if Writer (W) flag is set.
     * @return true if flag is set.
     */
    public boolean isWriter() {
        return mode.isWriter();
    }

    /**
     * Check if Presence (P) flag is NOT set.
     * @return true if flag is NOT set.
     */
    public boolean isMuted() {
        return mode.isMuted();
    }

    @JsonIgnore
    public Acs setMuted(boolean v) {
        mode.setMuted(v);
        return this;
    }

    /**
     * Check if Approver (A) flag is set.
     * @return true if flag is set.
     */
    public boolean isAdmin() {
        return mode.isAdmin();
    }

    /**
     * Check if either Owner (O) or Approver (A) flag is set.
     * @return true if flag is set.
     */
    public boolean isManager() {
        return mode.isAdmin() || mode.isOwner();
    }

    /**
     * Check if Sharer (S) flag is set.
     * @return true if flag is set.
     */
    public boolean isSharer() {
        return mode.isSharer();
    }


    /**
     * Check if Deleter (D) flag is set.
     * @return true if flag is set.
     */
    public boolean isDeleter() {
        return mode.isDeleter();
    }
    /**
     * Check if Owner (O) flag is set.
     * @return true if flag is set.
     */
    public boolean isOwner() {
        return mode.isOwner();
    }
    /**
     * Check if Joiner (J) flag is set.
     * @return true if flag is set.
     */
    public boolean isJoiner() {
        return mode != null && mode.isJoiner();
    }
    /**
     * Check if Joiner (J) flag is set for the specified side.
     * @param s site to query (mode, want, given).
     * @return true if flag is set.
     */
    public boolean isJoiner(Side s) {
        switch (s) {
            case MODE:
                return mode != null && mode.isJoiner();
            case WANT:
                return want != null && want.isJoiner();
            case GIVEN:
                return given != null && given.isJoiner();
        }
        return false;
    }

    /**
     * Check if mode is defined.
     * @return true if defined.
     */
    public boolean isModeDefined() {
        return mode.isDefined();
    }
    /**
     * Check if given is defined.
     * @return true if defined.
     */
    public boolean isGivenDefined() {
        return given.isDefined();
    }

    /**
     * Check if want is defined.
     * @return true if defined.
     */
    public boolean isWantDefined() {
        return want.isDefined();
    }

    /**
     * Check if mode is invalid.
     * @return true if invalid.
     */
    public boolean isInvalid() {
        return mode.isInvalid();
    }

    /**
     * Get permissions present in 'want' but missing in 'given'.
     * Inverse of {@link Acs#getExcessive}
     *
     * @return <b>want</b> value.
     */
    public AcsHelper getMissing() {
        return AcsHelper.diff(want, given);
    }

    /**
     * Get permissions present in 'given' but missing in 'want'.
     * Inverse of {@link Acs#getMissing}
     *
     * @return permissions present in <b>given</b> but missing in <b>want</b>.
     */
    public AcsHelper getExcessive() {
        return AcsHelper.diff(given, want);
    }

    @Override
    public String toString() {
        return "G:" + (given != null ? given.toString() : "null") +
                ";W:"+ (want != null ? want.toString() : "null");
    }
}
