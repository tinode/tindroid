package co.tinode.tinodesdk.model;

/**
 * Access mode handler.
 */

public class AccessMode {

    private long mMode;
    // User access to topic

    private static long MODE_SUB = 0x01;    // R
    private static long MODE_PUB = 0x02;    // W
    private static long MODE_PRES = 0x04;   // P
    private static long MODE_SHARE = 0x08;  // S user can invite other people to join (S)
    private static long MODE_DELETE = 0x10; // D user can hard-delete messages (D), only owner can completely delete
    private static long MODE_OWNER = 0x20;  // O user is the owner (O) - full access
    private static long MODE_BANNED = 0x40; // user has no access, requests to share/gain access/{sub} are ignored (X)

    private static long MODE_NONE = 0; // No access, requests to gain access are processed normally (N)

    // Invalid mode to indicate an error
    private static long MODE_INVALID = 0x100000;

    public AccessMode() {
        mMode = MODE_NONE;
    }

    public AccessMode(String mode) {
        mMode = parse(mode);
    }

    private static long parse(String mode) {
        if (mode == null || mode.length() == 0) {
            return MODE_NONE;
        }

        long m0 = MODE_NONE;

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

    @Override
    public String toString() {
        // Need to distinguish between "not set" and "no access"
        if (mMode == 0) {
            return "N";
        }
        if (mMode == MODE_INVALID) {
            return null;
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
}
