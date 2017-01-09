package co.tinode.tinodesdk.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_DEFAULT;

/**
 * Parameter of MsgGetMeta
 */
@JsonInclude(NON_DEFAULT)
public class MetaGetData {
    public Integer since;
    public Integer before;
    public Integer limit;

    public MetaGetData() {}

    @Override
    public String toString() {
        return "since=" + since + ", before=" + before + ", limit=" + limit;
    }
}