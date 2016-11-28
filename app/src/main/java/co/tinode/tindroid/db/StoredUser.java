package co.tinode.tindroid.db;

import java.util.Date;

/**
 * Topic subscriber stored in the database
 */
public class StoredUser<Pu,Pr> {
    public long id;
    public String uid;

    public int senderIdx;

    public String mode;

    public int recv;
    public int read;

    public Date updated;
    public Date deleted;

    public Pu pub;
    public Pu priv;
}
