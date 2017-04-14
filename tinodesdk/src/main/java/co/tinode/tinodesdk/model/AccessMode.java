package co.tinode.tinodesdk.model;

/**
 * Access mode handler.
 */

public class AccessMode {

    private Integer mMode;
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

    public AccessMode() {
        mMode = MODE_NONE;
    }

    public AccessMode(String mode) {
        mMode = parse(mode);
    }

    public AccessMode(Acs acs) {
        this(acs != null ? acs.mode : null);
    }

    public AccessMode(AccessMode mode) {
        if (mode != null) {
            mMode = mode.mMode;
        } else {
            mMode = MODE_NONE;
        }
    }

    private static int parse(String mode) {
        if (mode == null || mode.length() == 0) {
            return MODE_NONE;
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

    /**
     * Change current mode.
     * @param umode change to the current mode, '+' or '-' followed by the letter being set or unset.
     * @return new mode
     */
    public AccessMode update(String umode) {
        if (umode == null || umode.length() == 0) {
            return this;
        }
        char sign = umode.charAt(0);
        if (umode.length() < 2 || (sign != '+' && sign != '-')) {
            throw new IllegalArgumentException();
        }
        int mode = parse(umode.substring(1));
        if (mode == MODE_INVALID) {
            throw new IllegalArgumentException();
        }
        if (mode == MODE_NONE) {
            return this;
        }
        if (sign == '+') {
            if (mode == MODE_BANNED) {
                mMode = MODE_BANNED;
            } else {
                mMode |= mode;
            }
        } else {
            if (mode == MODE_BANNED) {
                if ((mMode & MODE_BANNED) != 0) {
                    mMode = MODE_NONE;
                }
            } else {
                mMode &= ~mode;
            }
        }

        return this;
    }

    @Override
    public String toString() {
        // Need to distinguish between "not set" and "no access"
        if (mMode == null || mMode == MODE_INVALID) {
            return null;
        }

        if (mMode == 0) {
            return "N";
        }

        if ((mMode & MODE_BANNED) != 0) {
            return "X";
        }

        StringBuilder res = new StringBuilder(6);
        char[] modes = new char[]{'R', 'W', 'P', 'S', 'D', 'O'};
        for (int i = 0; i < modes.length; i++) {
            if ((mMode & (1 << i)) != 0) {
                res.append(modes[i]);
            }
        }
        return res.toString();
    }

    public boolean equals(AccessMode am) {
        return am != null && mMode.equals(am.mMode);
    }

    public boolean canRead() {
        return (mMode & MODE_SUB) != 0;
    }

    public boolean canWrite() {
        return (mMode & MODE_PUB) != 0;
    }

    public boolean isMuted() {
        return (mMode & MODE_PRES) == 0;
    }
    public AccessMode setMuted(boolean v) {
        mMode = !v ? mMode | MODE_PRES : (mMode & ~MODE_PRES);
        return this;
    }

    public boolean isAdmin() {
        return (mMode & MODE_SHARE) != 0;
    }

    public boolean canDelete() {
        return (mMode & MODE_DELETE) != 0;
    }

    public boolean isOwner() {
        return (mMode & MODE_OWNER) != 0;
    }

    public boolean isBanned() {
        return (mMode & MODE_BANNED) != 0;
    }

    public boolean isDefined() {
        return mMode != MODE_NONE && mMode != MODE_INVALID;
    }

    public boolean isInvalid() {
        return mMode == MODE_INVALID;
    }
}
