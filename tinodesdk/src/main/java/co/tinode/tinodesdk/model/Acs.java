package co.tinode.tinodesdk.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.util.Map;

/**
 * Access mode.
 */
public class Acs implements Serializable {
    public enum Side {
        MODE(0), WANT(1), GIVEN(2);

        private final int val;
        Side(int val) {
            this.val = val;
        }

        public int val() {
            return val;
        }
    }

    AcsHelper given = null;
    AcsHelper want = null;
    AcsHelper mode = null;

    public Acs() {
        assign(null, null, null);
    }

    public Acs(String g, String w) {
        assign(g, w, null);
    }

    public Acs(String g, String w, String m) {
        assign(g, w, m);
    }

    public Acs(Acs am) {
        if (am != null) {
            given = am.given != null ? new AcsHelper(am.given) : null;
            want = am.want != null ? new AcsHelper(am.want) : null;
            mode = am.mode != null ? new AcsHelper(am.mode) : AcsHelper.and(want, given);
        }
    }

    public Acs(Map<String,String> am) {
        if (am != null) {
            assign(am.get("given"), am.get("want"), am.get("mode"));
        }
    }

    public Acs(AccessChange ac) {
        if (ac != null) {
            boolean change = false;
            if (ac.given != null) {
                if (given == null) {
                    given = new AcsHelper();
                }
                change = given.update(ac.given);
            }

            if (ac.want != null) {
                if (want == null) {
                    want = new AcsHelper();
                }
                change = change || want.update(ac.want);
            }

            if (change) {
                mode = AcsHelper.and(want, given);
            }
        }
    }

    private void assign(String g, String w, String m) {
        this.given = g != null ? new AcsHelper(g) : null;
        this.want = w != null ? new AcsHelper(w) : null;
        this.mode = m != null ? new AcsHelper(m) : AcsHelper.and(want, given);
    }

    public void setMode(String m) {
        mode = m != null ? new AcsHelper(m) : null;
    }
    public String getMode() {
        return mode != null ? mode.toString() : null;
    }
    public AcsHelper getModeHelper() {
        return new AcsHelper(mode);
    }

    public void setGiven(String g) {
        given = g != null ? new AcsHelper(g) : null;
    }
    public String getGiven() {
        return given != null ? given.toString() : null;
    }
    public AcsHelper getGivenHelper() {
        return new AcsHelper(given);
    }

    public void setWant(String w) {
        want = w != null ? new AcsHelper(w) : null;
    }
    public String getWant() {
        return want != null ? want.toString() : null;
    }
    public AcsHelper getWantHelper() {
        return new AcsHelper(want);
    }

    public boolean merge(Acs am) {
        int change = 0;
        if (am != null && !equals(am)) {
            if (am.given != null) {
                if (given == null) {
                    given = new AcsHelper();
                }
                change += given.merge(am.given) ? 1 : 0;
            }

            if (am.want != null) {
                if (want == null) {
                    want = new AcsHelper();
                }
                change += want.merge(am.want) ? 1 : 0;
            }

            if (am.mode != null) {
                if (mode == null) {
                    mode = new AcsHelper();
                }
                change += mode.merge(am.mode) ? 1 : 0;
            } else if (change > 0) {
                AcsHelper m2 = AcsHelper.and(want, given);
                if (m2 != null && !m2.equals(mode)) {
                    change ++;
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
            } else if (change > 0) {
                AcsHelper m2 = AcsHelper.and(want, given);
                if (m2 != null && !m2.equals(mode)) {
                    change ++;
                    mode = m2;
                }
            }
        }
        return change > 0;
    }

    public boolean update(AccessChange ac) {
        int change = 0;
        if (ac != null) {
            try {
                if (ac.given != null) {
                    if (given == null) {
                        given = new AcsHelper();
                    }
                    change += given.update(ac.given) ? 1 : 0;
                }
                if (ac.want != null) {
                    if (want == null) {
                        want = new AcsHelper();
                    }
                    change += want.update(ac.want) ? 1 : 0;
                }
            } catch (IllegalArgumentException ignore) {}

            if (change > 0) {
                AcsHelper m2 = AcsHelper.and(want, given);
                if (m2 != null && !m2.equals(mode)) {
                    mode = m2;
                }
            }
        }
        return change > 0;
    }

    /**
     * Compare this Acs with another.
     * @param am Acs instance to compare to.
     * @return true if am represents the same access rights, false otherwise.
     */
    public boolean equals(Acs am) {
        return (am != null) &&
                ((mode == null && am.mode == null) || (mode != null && mode.equals(am.mode))) &&
                ((want == null && am.want == null) || (want != null && want.equals(am.want))) &&
                ((given == null && am.given == null) || (given != null && given.equals(am.given)));
    }

    /**
     * Check if mode is NONE: no flags are set.
     * @return true if no flags are set.
     */
    public boolean isNone() {
        return mode != null && mode.isNone();
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
        return switch (s) {
            case MODE -> mode != null && mode.isReader();
            case WANT -> want != null && want.isReader();
            case GIVEN -> given != null && given.isReader();
        };
    }

    /**
     * Check if Writer (W) flag is set.
     * @return true if flag is set.
     */
    public boolean isWriter() {
        return mode != null && mode.isWriter();
    }

    /**
     * Check if Presence (P) flag is NOT set.
     * @return true if flag is NOT set.
     */
    public boolean isMuted() {
        return mode != null && mode.isMuted();
    }

    @JsonIgnore
    public Acs setMuted(boolean v) {
        if (mode == null) {
            mode = new AcsHelper("N");
        }
        mode.setMuted(v);
        return this;
    }

    /**
     * Check if Approver (A) flag is set.
     * @return true if flag is set.
     */
    public boolean isApprover() {
        return mode != null && mode.isApprover();
    }

    /**
     * Check if either Owner (O) or Approver (A) flag is set.
     * @return true if flag is set.
     */
    public boolean isManager() {
        return mode != null && (mode.isApprover() || mode.isOwner());
    }

    /**
     * Check if Sharer (S) flag is set.
     * @return true if flag is set.
     */
    public boolean isSharer() {
        return mode != null && mode.isSharer();
    }


    /**
     * Check if Deleter (D) flag is set.
     * @return true if flag is set.
     */
    public boolean isDeleter() {
        return mode != null && mode.isDeleter();
    }
    /**
     * Check if Owner (O) flag is set.
     * @return true if flag is set.
     */
    public boolean isOwner() {
        return mode != null && mode.isOwner();
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
        return switch (s) {
            case MODE -> mode != null && mode.isJoiner();
            case WANT -> want != null && want.isJoiner();
            case GIVEN -> given != null && given.isJoiner();
        };
    }

    /**
     * Check if mode is defined.
     * @return true if defined.
     */
    public boolean isModeDefined() {
        return mode != null && mode.isDefined();
    }
    /**
     * Check if given is defined.
     * @return true if defined.
     */
    public boolean isGivenDefined() {
        return given != null && given.isDefined();
    }

    /**
     * Check if want is defined.
     * @return true if defined.
     */
    public boolean isWantDefined() {
        return want != null && want.isDefined();
    }

    /**
     * Check if mode is invalid.
     * @return true if invalid.
     */
    public boolean isInvalid() {
        return mode != null && mode.isInvalid();
    }

    /**
     * Get permissions present in 'want' but missing in 'given'.
     * Inverse of {@link Acs#getExcessive}
     *
     * @return <b>want</b> value.
     */
    @JsonIgnore
    public AcsHelper getMissing() {
        return AcsHelper.diff(want, given);
    }

    /**
     * Get permissions present in 'given' but missing in 'want'.
     * Inverse of {@link Acs#getMissing}
     *
     * @return permissions present in <b>given</b> but missing in <b>want</b>.
     */
    @JsonIgnore
    public AcsHelper getExcessive() {
        return AcsHelper.diff(given, want);
    }

    @NotNull
    @Override
    public String toString() {
        return "{\"given\":" + (given != null ? " \"" + given + "\"" : " null") +
                ", \"want\":" + (want != null ? " \"" + want + "\"" : " null") +
                ", \"mode\":" + (mode != null ? " \"" + mode + "\"}" : " null}");
    }
}
