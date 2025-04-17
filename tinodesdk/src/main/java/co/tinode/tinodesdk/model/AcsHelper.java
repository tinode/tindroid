package co.tinode.tinodesdk.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.util.StringTokenizer;

/**
 * Helper class for access mode parser/generator.
 */
public class AcsHelper implements Serializable {
    // User access to topic
    private static final int MODE_JOIN = 0x01;      // J - join topic
    private static final int MODE_READ = 0x02;      // R - read broadcasts
    private static final int MODE_WRITE = 0x04;     // W - publish
    private static final int MODE_PRES = 0x08;      // P - receive presence notifications
    private static final int MODE_APPROVE = 0x10;   // A - approve requests
    private static final int MODE_SHARE = 0x20;     // S - user can invite other people to join (S)
    private static final int MODE_DELETE = 0x40;    // D - user can hard-delete messages (D), only owner can completely delete
    private static final int MODE_OWNER = 0x80;     // O - user is the owner (O) - full access

    private static final int MODE_NONE = 0; // No access, requests to gain access are processed normally (N)

    // Invalid mode to indicate an error
    private static final int MODE_INVALID = 0x100000;

    private int a;

    public AcsHelper() {
        a = MODE_NONE;
    }

    public AcsHelper(String str) {
        a = decode(str);
    }

    public AcsHelper(AcsHelper ah) {
        a = ah != null ? ah.a : MODE_INVALID;
    }

    public AcsHelper(Integer a) {
        this.a = a != null ? a : MODE_INVALID;
    }

    @NotNull
    @Override
    public String toString() {
        return encode(a);
    }

    public boolean update(String umode) {
        int old = a;
        a = update(a, umode);
        return a != old;
    }

    @Override
    public boolean equals(Object o) {

        if (o == null) {
            return false;
        }

        if (o == this) {
            return true;
        }

        if (!(o instanceof AcsHelper ah)) {
            return false;
        }

        return a == ah.a;
    }

    public boolean equals(String s) {
        return a == decode(s);
    }

    public boolean isNone() {
        return a == MODE_NONE;
    }
    public boolean isReader() {
        return (a & MODE_READ) != 0;
    }
    public boolean isWriter() {
        return (a & MODE_WRITE) != 0;
    }
    public boolean isMuted() {
        return (a & MODE_PRES) == 0;
    }

    @JsonIgnore
    public void setMuted(boolean v) {
        if (a == MODE_INVALID) {
            a = MODE_NONE;
        }
        a = !v ? (a | MODE_PRES) : (a & ~MODE_PRES);
    }
    public boolean isApprover() {
        return (a & MODE_APPROVE) != 0;
    }

    public boolean isSharer() {
        return (a & MODE_SHARE) != 0;
    }

    public boolean isDeleter() {
        return (a & MODE_DELETE) != 0;
    }

    public boolean isOwner() {
        return (a & MODE_OWNER) != 0;
    }

    public boolean isJoiner() {
        return (a & MODE_JOIN) != 0;
    }

    public boolean isDefined() {
        return a != MODE_INVALID;
    }
    public boolean isInvalid() {
        return a == MODE_INVALID;
    }

    private static int decode(String mode) {
        if (mode == null || mode.isEmpty()) {
            return MODE_INVALID;
        }

        int m0 = MODE_NONE;

        for (char c : mode.toCharArray()) {
            switch (c) {
                case 'J':
                case 'j':
                    m0 |= MODE_JOIN;
                    continue;
                case 'R':
                case 'r':
                    m0 |= MODE_READ;
                    continue;
                case 'W':
                case 'w':
                    m0 |= MODE_WRITE;
                    continue;
                case 'A':
                case 'a':
                    m0 |= MODE_APPROVE;
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
            return "";
        }

        if (val == MODE_NONE) {
            return "N";
        }

        StringBuilder res = new StringBuilder(6);
        char[] modes = new char[]{'J', 'R', 'W', 'P', 'A', 'S', 'D', 'O'};
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
     *              or an explicit new value: "+JS-WR" or just "JSA"
     * @return updated value.
     */
    private static int update(int val, String umode) {
        if (umode == null || umode.isEmpty()) {
            return val;
        }

        int m0;
        char action = umode.charAt(0);
        if (action == '+' || action == '-') {
            int val0 = val;
            StringTokenizer parts = new StringTokenizer(umode, "-+", true);
            while (parts.hasMoreTokens()) {
                action = parts.nextToken().charAt(0);
                if (parts.hasMoreTokens()) {
                    m0 = decode(parts.nextToken());
                } else {
                    break;
                }

                if (m0 == MODE_INVALID) {
                    throw new IllegalArgumentException();
                }
                if (m0 == MODE_NONE) {
                    continue;
                }

                if (action == '+') {
                    val0 |= m0;
                } else if (action == '-') {
                    val0 &= ~m0;
                }
            }
            val = val0;

        } else {
            val = decode(umode);
            if (val == MODE_INVALID) {
                throw new IllegalArgumentException();
            }
        }

        return val;
    }

    public boolean merge(AcsHelper ah) {
        if (ah != null && ah.a != MODE_INVALID) {
            if (ah.a != a) {
                a = ah.a;
                return true;
            }
        }
        return false;
    }

    /**
     * Bitwise AND between two modes, usually given & want: a1 & a2.
     * @param a1 first mode
     * @param a2 second mode
     * @return {AcsHelper} (a1 & a2)
     */
    public static AcsHelper and(AcsHelper a1, AcsHelper a2) {
        if (a1 != null && !a1.isInvalid() && a2 != null && !a2.isInvalid()) {
            return new AcsHelper(a1.a & a2.a);
        }
        return null;
    }

    /**
     * Get bits present in a1 but missing in a2: a1 & ~a2.
     * @param a1 first mode
     * @param a2 second mode
     * @return {AcsHelper} (a1 & ~a2)
     */
    public static AcsHelper diff(AcsHelper a1, AcsHelper a2) {
        if (a1 != null && !a1.isInvalid() && a2 != null && !a2.isInvalid()) {
            return new AcsHelper(a1.a & ~a2.a);
        }
        return null;
    }
}
