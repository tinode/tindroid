package co.tinode.tinodesdk.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.Map;

/**
 * Access mode.
 */
public class Acs {
    private Integer given;
    private Integer want;
    private Integer mode;

    // User access to topic
    private static int MODE_SUB = 0x01;    // R
    private static int MODE_PUB = 0x02;    // W
    private static int MODE_PRES = 0x04;   // P
    private static int MODE_SHARE = 0x08;  // S user can invite other people to join (S)
    private static int MODE_DELETE = 0x10; // D user can hard-delete messages (D), only owner can completely delete
    private static int MODE_OWNER = 0x20;  // O user is the owner (O) - full access
    private static int MODE_BANNED = 0x40; // user has no access, requests to share/gain access/{sub} are ignored (X)

    private static int MODE_NONE = 0; // No access, requests to gain access are processed normally (N)

    // Invalid mode to indicate an error
    private static int MODE_INVALID = 0x100000;

    public Acs() {
    }

    private void assign(String g, String w, String m) {
        this.given = decode(g);
        this.want = decode(w);
        this.mode = decode(m);
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

    public void setMode(String m) {
        mode = decode(m);
    }
    public String getMode() {
        return encode(mode);
    }

    public void setGiven(String g) {
        given = decode(g);
    }
    public String getGiven() {
        return encode(given);
    }

    public void setWant(String w) {
        want = decode(w);
    }
    public String getWant() {
        return encode(want);
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

    private static Integer decode(String mode) {
        if (mode == null || mode.length() == 0) {
            return null;
        }

        int m0 = MODE_NONE;

        for (char c : mode.toCharArray()) {
            switch (c) {
                case 'R':
                case 'r':
                    m0 |= MODE_SUB;
                    continue;
                case 'W':
                case 'w':
                    m0 |= MODE_PUB;
                    continue;
                case 'S':
                case 's':
                    m0 |= MODE_SHARE;
                    continue;
                case 'D':
                case 'd':
                    m0 |= MODE_DELETE;
                    continue;
                case 'P':
                case 'p':
                    m0 |= MODE_PRES;
                    continue;
                case 'O':
                case 'o':
                    m0 |= MODE_OWNER;
                    continue;
                case 'X':
                case 'x':
                    return MODE_BANNED;
                case 'N':
                case 'n':
                    return MODE_NONE;
                default:
                    return MODE_INVALID;
            }
        }

        return m0;
    }

    private static String encode(Integer val) {
        // Need to distinguish between "not set" and "no access"
        if (val == null || val == MODE_INVALID) {
            return null;
        }

        if (val == 0) {
            return "N";
        }

        if (val == MODE_BANNED) {
            return "X";
        }

        StringBuilder res = new StringBuilder(6);
        char[] modes = new char[]{'R', 'W', 'P', 'S', 'D', 'O'};
        for (int i = 0; i < modes.length; i++) {
            if ((val & (1 << i)) != 0) {
                res.append(modes[i]);
            }
        }
        return res.toString();
    }

    /**
     * Apply changes, defined as a string, to the given internal representation.
     *
     * @param val value to change
     * @param umode change to the value, '+' or '-' followed by the letter(s) being set or unset.
     * @return updated value
     */
    private static int update(Integer val, String umode) {
        if (umode == null || umode.length() == 0) {
            return val;
        }
        char sign = umode.charAt(0);
        if (umode.length() < 2 || (sign != '+' && sign != '-')) {
            throw new IllegalArgumentException();
        }
        int m0 = decode(umode.substring(1));
        if (m0 == MODE_INVALID) {
            throw new IllegalArgumentException();
        }
        if (m0 == MODE_NONE) {
            return val;
        }

        if (val == null) {
            val = MODE_NONE;
        }

        if (sign == '+') {
            if (m0 == MODE_BANNED) {
                val = MODE_BANNED;
            } else {
                val |= m0;
            }
        } else {
            if (m0 == MODE_BANNED) {
                if ((val & MODE_BANNED) != 0) {
                    val = MODE_NONE;
                }
            } else {
                val &= ~m0;
            }
        }

        return val;
    }

    public Acs updateMode(String u) {
        mode = update(mode, u);
        return this;
    }
    public Acs updateGiven(String u) {
        given = update(given, u);
        return this;
    }
    public Acs updateWant(String u) {
        want = update(want, u);
        return this;
    }

    public boolean equals(Acs am) {
        return
                (mode == null && given == null && want == null && am == null) ||
                (am != null &&
                    ((am.mode == null && mode == null) || mode != null && mode.equals(am.mode)) &&
                    ((am.want == null && want == null) || want != null && want.equals(am.want)) &&
                    ((am.mode == null && mode == null) || mode != null && mode.equals(am.mode)));
    }

    public boolean modeEquals(Acs am) {
        return (mode == null && (am == null || am.mode == null)) ||
                (mode != null && am != null && mode.equals(am.mode));
    }
    public boolean modeEquals(String m) {
        Integer m0 = decode(m);
        return (m0 == null && mode == null) || (m0 != null && m0.equals(mode));
    }

    public boolean wantEquals(Acs am) {
        return (want == null && (am == null || am.want == null)) ||
                (want != null && am != null && want.equals(am.want));
    }
    public boolean wantEquals(String w) {
        Integer w0 = decode(w);
        return (w0 == null && want == null) || (w0 != null && w0.equals(mode));
    }

    public boolean canRead() {
        return (mode != null) && ((mode & MODE_SUB) != 0);
    }

    public boolean canWrite() {
        return (mode != null) && ((mode & MODE_PUB) != 0);
    }

    public boolean isMuted() {
        return (mode != null) && ((mode & MODE_PRES) == 0);
    }
    @JsonIgnore
    public Acs setMuted(boolean v) {
        if (mode == null) {
            mode = MODE_NONE;
        }
        mode = !v ? mode | MODE_PRES : (mode & ~MODE_PRES);
        return this;
    }

    public boolean isAdmin() {
        return (mode != null) && ((mode & MODE_SHARE) != 0);
    }

    public boolean canDelete() {
        return (mode != null) && ((mode & MODE_DELETE) != 0);
    }

    public boolean isOwner() {
        return (mode != null) && ((mode & MODE_OWNER) != 0);
    }

    public boolean isBanned() {
        return (mode != null) && ((mode & MODE_BANNED) != 0);
    }

    private static boolean isDefined(Integer val) {
        return val != null && val != MODE_NONE && val != MODE_INVALID;
    }
    public boolean isModeDefined() {
        return isDefined(mode);
    }
    public boolean isGivenDefined() {
        return isDefined(given);
    }
    public boolean isWantDefined() {
        return isDefined(want);
    }

    public boolean isInvalid() {
        return mode != null && mode == MODE_INVALID;
    }

}
