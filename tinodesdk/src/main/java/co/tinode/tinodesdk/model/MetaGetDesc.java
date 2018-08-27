package co.tinode.tinodesdk.model;

import java.util.Date;

/**
 * Parameter of GetMeta.
 */
// @JsonInclude(NON_DEFAULT)
public class MetaGetDesc {
    // ims = If modified since...
    public Date ims;

    public MetaGetDesc() {}

    @Override
    public String toString() {
        return "ims=" + ims;
    }
}