package co.tinode.tindroid;

import java.util.Date;

/**
 * Created by gsokolov on 2/4/16.
 */
public class Contact {
    public String topic;
    public Date updated;
    public String mode;

    public boolean online;

    public int seq;
    public int read;
    public int recv;

    public VCard pub;
    public String priv;

    /* p2p only */
    public String with;
    public Seen seen;

    public class Seen {
        public Date when;
        public String ua;
    }
}
