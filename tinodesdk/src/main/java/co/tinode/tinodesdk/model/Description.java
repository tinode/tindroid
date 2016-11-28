package co.tinode.tinodesdk.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Date;

import co.tinode.tinodesdk.Tinode;

/**
 * Topic description as deserialized from the server packet.
 */
public class Description<Pu,Pr> {
    public Date created;
    public Date updated;
    public Date deleted;
    public Defacs defacs;
    public Acs acs;
    public int seq;
    public int read;
    public int recv;
    public int clear;
    @JsonProperty("public")
    public Pu pub;
    @JsonProperty("private")
    public Pr priv;
    // P2P only
    public String with;

    public Description() {
    }

    /**
     * Copy non-null values to this object.
     *
     * @param desc object to copy.
     */
    public void merge(Description<Pu,Pr> desc) {
        if (created == null && desc.created != null) {
            created = desc.created;
        }
        if (desc.updated != null && (updated == null || updated.before(desc.updated))) {
            updated = desc.updated;
        }
        if (desc.defacs != null) {
            defacs = desc.defacs;
        }
        if (desc.acs != null) {
            acs = desc.acs;
        }
        if (desc.seq > seq) {
            seq = desc.seq;
        }
        if (desc.read > read) {
            read = desc.read;
        }
        if (desc.recv > recv) {
            recv = desc.recv;
        }
        if (desc.clear > clear) {
            clear = desc.clear;
        }
        if (desc.pub != null) {
            pub = desc.pub;
        }

        if (desc.priv != null) {
            priv = desc.priv;
        }

        if (desc.with != null) {
            with = desc.with;
        }

    }
}
