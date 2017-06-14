package co.tinode.tinodesdk.model;

import java.io.Serializable;
import java.util.Date;

/**
 * Class to hold last seen date-time and User Agent.
 */
public class LastSeen implements Serializable {
    public Date when;
    public String ua;

    public LastSeen() {
    }

    public LastSeen(Date when) {
        this.when = when;
    }

    public LastSeen(Date when, String ua) {
        this.when = when;
        this.ua = ua;
    }

    public boolean merge(LastSeen seen) {
        boolean changed = false;
        if (seen != null) {
            if (seen.when != null && (when == null || when.before(seen.when))) {
                when = seen.when;
                ua = seen.ua;
                changed = true;
            }
        }
        return changed;
    }
}
