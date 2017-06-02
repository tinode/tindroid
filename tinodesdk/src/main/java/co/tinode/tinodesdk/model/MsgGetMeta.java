package co.tinode.tinodesdk.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.Date;
import java.util.LinkedList;
import java.util.List;

/**
 * Topic metadata request.
 */
public class MsgGetMeta {
    public static final String DESC = "desc";
    public static final String SUB = "sub";
    public static final String DATA = "data";

    public String what;
    public MetaGetDesc desc;
    public MetaGetSub sub;
    public MetaGetData data;

    /**
     * Query to get everything
     */
    public MsgGetMeta() {
        this.what = DESC + " " + SUB + " " + DATA;
    }

    public MsgGetMeta(MetaGetDesc desc, MetaGetSub sub, MetaGetData data) {
        this.desc = desc;
        this.sub = sub;
        this.data = data;
        buildWhat();
    }

    /*
    @Override
    public String toString() {
        return "[" + what + "]" +
                " desc=[" + (desc != null ? desc.toString() : "null") + "]," +
                " sub=[" + (sub != null? sub.toString() : "null") + "]," +
                " data=[" + (data != null ? data.toString() : "null") + "]";
    }
    */

    public void setDesc(Date ims) {
        desc = new MetaGetDesc();
        desc.ims = ims;
    }

    public void setSub(Date ims, Integer limit) {
        sub = new MetaGetSub(ims, limit);
    }

    public void setData(Integer since, Integer before, Integer limit) {
        data = new MetaGetData(since, before, limit);
    }

    @JsonIgnore
    private void buildWhat() {
        List<String> parts = new LinkedList<>();
        StringBuilder sb = new StringBuilder();

        if (desc != null) {
            parts.add(DESC);
        }
        if (sub != null) {
            parts.add(SUB);
        }
        if (data != null) {
            parts.add(DATA);
        }

        if (!parts.isEmpty()) {
            sb.append(parts.get(0));
            for (int i=1; i < parts.size(); i++) {
                sb.append(" ").append(parts.get(i));
            }
        }
        what = sb.toString();
    }
}
