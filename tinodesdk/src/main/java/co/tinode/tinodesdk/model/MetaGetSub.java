package co.tinode.tinodesdk.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Date;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_DEFAULT;

/**
 * Parameter of MsgGetMeta
 */

@JsonInclude(NON_DEFAULT)
public class MetaGetSub {
    public Date ims;
    public Integer limit;

    public MetaGetSub() {}

    @Override
    public String toString() {
        return "ims=" + ims + ", limit=" + limit;
    }
}
