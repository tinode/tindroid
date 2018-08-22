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
    private static final int DEL_SET = 0x08;
    private static final int TAGS_SET = 0x10;

    public static final String DESC = "desc";
    public static final String SUB = "sub";
    public static final String DATA = "data";
    public static final String DEL = "del";
    public static final String TAGS = "tags";

    @JsonIgnore
    private int mSet = 0;

    public String what;
    public MetaGetDesc desc;
    public MetaGetSub sub;
    public MetaGetData data;
    public MetaGetData del;

    /**
     * Generate query to get everything
     */
    public MsgGetMeta() {
        this.what = DESC + " " + SUB + " " + DATA + " " + DEL + " " + TAGS;
    }

    /**
     * Generate query to get specific data:
     *
     * @param desc request topic description
     * @param sub request subscriptions
     * @param data request data messages
     */
    public MsgGetMeta(MetaGetDesc desc, MetaGetSub sub, MetaGetData data, MetaGetData del, Boolean tags) {
        this.desc = desc;
        this.sub = sub;
        this.data = data;
        this.del = del;
        if (tags != null && tags) {
            this.mSet |= TAGS_SET;
        }
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
                " data=[" + (data != null ? data.toString() : "null") + "]," +
                " del=[" + (del != null ? del.toString() : "null") + "]" +
                " tags=[" + ((mSet & TAGS_SET) != 0 ? "set" : "null") + "]";
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

    public void setSub(String user, Date ims, Integer limit) {
        if (user!= null || ims != null || limit != null) {
            sub = new MetaGetSub(user, ims, limit);
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

    public void setDel(Integer since, Integer limit) {
        if (since != null || limit != null) {
            del = new MetaGetData(since, null, limit);
        }
        mSet |= DEL_SET;
        buildWhat();
    }

    public void setTags() {
        mSet |= TAGS_SET;
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
        if (del != null || (mSet & DEL_SET) != 0) {
            parts.add(DEL);
        }
        if ((mSet & TAGS_SET) != 0) {
            parts.add(TAGS);
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

    public static MsgGetMeta del() {
        return new MsgGetMeta(DEL);
    }

    public static MsgGetMeta tags() {
        return new MsgGetMeta(TAGS);
    }
}
