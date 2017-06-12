package co.tinode.tinodesdk.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.Date;
import java.util.LinkedList;
import java.util.List;

/**
 * Topic metadata request.
 */
public class MsgGetMeta {
    private static final int DESC_SET = 0x01;
    private static final int SUB_SET = 0x02;
    private static final int DATA_SET = 0x04;

    public static final String DESC = "desc";
    public static final String SUB = "sub";
    public static final String DATA = "data";

    @JsonIgnore
    private int mSet = 0;

    public String what;
    public MetaGetDesc desc;
    public MetaGetSub sub;
    public MetaGetData data;

    /**
     * Generate query to get everything
     */
    public MsgGetMeta() {
        this.what = DESC + " " + SUB + " " + DATA;
    }

    /**
     * Generate query to get specific data:
     *
     * @param desc request topic description
     * @param sub request subscriptions
     * @param data request data messages
     */
    public MsgGetMeta(MetaGetDesc desc, MetaGetSub sub, MetaGetData data) {
        this.desc = desc;
        this.sub = sub;
        this.data = data;
        buildWhat();
    }

    protected MsgGetMeta(String what) {
        this.what = what;
    }


    @Override
    public String toString() {
        return "[" + what + "]" +
                " desc=[" + (desc != null ? desc.toString() : "null") + "]," +
                " sub=[" + (sub != null? sub.toString() : "null") + "]," +
                " data=[" + (data != null ? data.toString() : "null") + "]";
    }


    /**
     * Request topic description
     *
     * @param ims timestamp to receive public if it's newer than ims; could be null
     */
    public void setDesc(Date ims) {
        if (ims != null) {
            desc = new MetaGetDesc();
            desc.ims = ims;
        }
        mSet |= DESC_SET;
        buildWhat();
    }

    public void setSub(Date ims, Integer limit) {
        if (ims != null || limit != null) {
            sub = new MetaGetSub(ims, limit);
        }
        mSet |= SUB_SET;
        buildWhat();
    }

    public void setData(Integer since, Integer before, Integer limit) {
        if (since != null || before != null || limit != null) {
            data = new MetaGetData(since, before, limit);
        }
        mSet |= DATA_SET;
        buildWhat();
    }

    @JsonIgnore
    private void buildWhat() {
        List<String> parts = new LinkedList<>();
        StringBuilder sb = new StringBuilder();

        if (desc != null || (mSet & DESC_SET) != 0) {
            parts.add(DESC);
        }
        if (sub != null || (mSet & SUB_SET) != 0) {
            parts.add(SUB);
        }
        if (data != null || (mSet & DATA_SET) != 0) {
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

    public static MsgGetMeta desc() {
        return new MsgGetMeta(DESC);
    }

    public static MsgGetMeta data() {
        return new MsgGetMeta(DATA);
    }

    public static MsgGetMeta sub() {
        return new MsgGetMeta(SUB);
    }
}
