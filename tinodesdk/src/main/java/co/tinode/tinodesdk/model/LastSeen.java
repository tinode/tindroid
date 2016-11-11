package co.tinode.tinodesdk.model;

import java.util.Date;

/**
 * Class to hold last seen date-time and User Agent.
 */
public class LastSeen {
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

    public void merge(LastSeen seen) {
        if (seen != null) {
            if (seen.when != null && (when == null || when.before(seen.when))) {
                when = seen.when;
                ua = seen.ua;
            }
        }
    }
}
