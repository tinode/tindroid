package co.tinode.tinodesdk.model;

/**
 * Default access modes.
 */
public class Acs {
    public String want;
    public String given;
    public String mode;

    @Override
    public boolean equals(Object o) {
        if (o == null) {
            return false;
        }

        if (this == o) {
            return true;
        }

        if (!(o instanceof Acs)) {
            return false;
        }

        Acs rhs = (Acs) o;

        return (want  == null ? rhs.want == null : want.equals(rhs.want)) &&
                (given  == null ? rhs.given == null : given.equals(rhs.given)) &&
                (mode  == null ? rhs.mode == null : mode.equals(rhs.mode));
    }

    public boolean merge(Acs acs) {
        boolean changed = false;
        if (acs != null && !equals(acs)) {
            want = acs.want;
            given = acs.given;
            mode = acs.mode;
            changed = true;
        }
        return changed;
    }
}
