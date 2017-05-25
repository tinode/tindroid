package co.tinode.tinodesdk.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Helper class for access mode parser/generator.
 */
public class AcsHelper {
    private static final String TAG = "AcsHelper";

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

    private Integer a;

    public AcsHelper(String str) {
        a = decode(str);
    }

    public AcsHelper(AcsHelper ah) {
        a = ah != null ? ah.a : null;
    }

    @Override
    public String toString() {
        return a == null ? "" : encode(a);
    }

    public boolean update(String umode) {
        Integer old = a;
        a = update(a, umode);
        return !a.equals(old);
    }

    @Override
    public boolean equals(Object o) {

        if (o == null) {
            return false;
        }

        if (o == this) {
            return true;
        }

        if (!(o instanceof AcsHelper)) {
            return false;
        }

        AcsHelper ah = (AcsHelper) o;

        return (a == null && ah.a == null) || (a != null && a.equals(ah.a));
    }

    public boolean equals(String s) {
        Integer ah = decode(s);
        return (a == null && ah == null) || (a != null && a.equals(ah));
    }

    public boolean canRead() {
        return (a != null) && ((a & MODE_SUB) != 0);
    }
    public boolean canWrite() {
        return (a != null) && ((a & MODE_PUB) != 0);
    }
    public boolean isMuted() {
        return (a != null) && ((a & MODE_PRES) == 0);
    }
    @JsonIgnore
    public void setMuted(boolean v) {
        if (a == null) {
            a = MODE_NONE;
        }
        a = !v ? a | MODE_PRES : (a & ~MODE_PRES);
    }
    public boolean isAdmin() {
        return (a != null) && ((a & MODE_SHARE) != 0);
    }
    public boolean canDelete() {
        return (a != null) && ((a & MODE_DELETE) != 0);
    }

    public boolean isOwner() {
        return (a != null) && ((a & MODE_OWNER) != 0);
    }

    public boolean isBanned() {
        return (a != null) && ((a & MODE_BANNED) != 0);
    }
    public boolean isDefined() {
        return a != null && a != MODE_NONE && a != MODE_INVALID;
    }
    public boolean isInvalid() {
        return a != null && a == MODE_INVALID;
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
     * @param val value to change.
     * @param umode change to the value, '+' or '-' followed by the letter(s) being set or unset,
     *              or an explicit new value.
     * @return updated value.
     */
    private static Integer update(Integer val, String umode) {
        if (umode == null || umode.length() == 0) {
            return val;
        }
        char sign = umode.charAt(0);
        int m0;
        if (sign == '+' || sign == '-') {
            if (umode.length() < 2) {
                throw new IllegalArgumentException();
            }
            m0 = decode(umode.substring(1));
        } else {
            m0 = decode(umode);
        }

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
        } else if (sign == '-') {
            if (m0 == MODE_BANNED) {
                if ((val & MODE_BANNED) != 0) {
                    val = MODE_NONE;
                }
            } else {
                val &= ~m0;
            }
        } else {
            val = m0;
        }

        return val;
    }

    public boolean merge(AcsHelper ah) {
        if (ah != null && ah.a != null && ah.a != MODE_INVALID) {
            if (!ah.a.equals(a)) {
                a = ah.a;
                return true;
            }
        }
        return false;
    }
}
