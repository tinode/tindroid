package co.tinode.tinodesdk.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_DEFAULT;

/**
 * Topic metadata request.
 */
@JsonInclude(NON_DEFAULT)
public class MsgGetMeta implements Serializable {
    private static final int DESC_SET = 0x01;
    private static final int SUB_SET = 0x02;
    private static final int DATA_SET = 0x04;
    private static final int DEL_SET = 0x08;
    private static final int TAGS_SET = 0x10;
    private static final int CRED_SET = 0x20;
    private static final int AUX_SET = 0x40;
    private static final String DESC = "desc";
    private static final String SUB = "sub";
    private static final String DATA = "data";
    private static final String DEL = "del";
    private static final String TAGS = "tags";
    private static final String CRED = "cred";
    private static final String AUX = "aux";
    @JsonIgnore
    private int mSet = 0;

    public String what;
    public MetaGetDesc desc;
    public MetaGetSub sub;
    public MetaGetData data;
    public MetaGetData del;

    /**
     * Empty query.
     */
    public MsgGetMeta() { }

    /**
     * Generate query to get specific data:
     *
     * @param desc request topic description
     * @param sub request subscriptions
     * @param data request data messages
     */
    public MsgGetMeta(MetaGetDesc desc, MetaGetSub sub, MetaGetData data, MetaGetData del,
                      Boolean tags, Boolean cred, Boolean aux) {
        this.desc = desc;
        this.sub = sub;
        this.data = data;
        this.del = del;
        if (tags != null && tags) {
            this.mSet = TAGS_SET;
        }
        if (cred != null && cred) {
            this.mSet |= CRED_SET;
        }
        if (aux != null && aux) {
            this.mSet |= AUX_SET;
        }
        buildWhat();
    }

    /**
     * Generate query to get subscription:
     *
     * @param sub request subscriptions
     */
    public MsgGetMeta(MetaGetSub sub) {
        this.sub = sub;
        buildWhat();
    }

    private MsgGetMeta(String what) {
        this.what = what;
    }

    @NotNull
    @Override
    public String toString() {
        return "[" + what + "]" +
                " desc=[" + (desc != null ? desc.toString() : "null") + "]," +
                " sub=[" + (sub != null? sub.toString() : "null") + "]," +
                " data=[" + (data != null ? data.toString() : "null") + "]," +
                " del=[" + (del != null ? del.toString() : "null") + "]" +
                " tags=[" + ((mSet & TAGS_SET) != 0 ? "set" : "null") + "]" +
                " cred=[" + ((mSet & CRED_SET) != 0 ? "set" : "null") + "]" +
                " aux=[" + ((mSet & AUX_SET) != 0 ? "set" : "null") + "]";
    }


    /**
     * Request topic description
     *
     * @param ims timestamp to receive public if it's newer than ims; could be null
     */
    // Do not add @JsonIgnore here.
    public void setDesc(Date ims) {
        if (ims != null) {
            desc = new MetaGetDesc();
            desc.ims = ims;
        }
        mSet |= DESC_SET;
        buildWhat();
    }

    // Do not add @JsonIgnore here.
    public void setSub(Date ims, Integer limit) {
        if (ims != null || limit != null) {
            sub = new MetaGetSub(ims, limit);
        }
        mSet |= SUB_SET;
        buildWhat();
    }

    @JsonIgnore
    public void setSubUser(String user, Date ims, Integer limit) {
        if (ims != null || limit != null || user != null) {
            sub = new MetaGetSub(ims, limit);
            sub.setUser(user);
        }
        mSet |= SUB_SET;
        buildWhat();
    }

    @JsonIgnore
    public void setSubTopic(String topic, Date ims, Integer limit) {
        if (ims != null || limit != null || topic != null) {
            sub = new MetaGetSub(ims, limit);
            sub.setTopic(topic);
        }
        mSet |= SUB_SET;
        buildWhat();
    }

    // Do not add @JsonIgnore here.
    public void setData(Integer since, Integer before, Integer limit) {
        if (since != null || before != null || limit != null) {
            data = new MetaGetData(since, before, limit);
        }
        mSet |= DATA_SET;
        buildWhat();
    }

    public void setData(MsgRange[] ranges, Integer limit) {
        if (ranges != null || limit != null) {
            data = new MetaGetData(ranges, limit);
        }
        mSet |= DATA_SET;
        buildWhat();
    }

    // Do not add @JsonIgnore here.
    public void setDel(Integer since, Integer limit) {
        if (since != null || limit != null) {
            del = new MetaGetData(since, null, limit);
        }
        mSet |= DEL_SET;
        buildWhat();
    }

    // Do not add @JsonIgnore here.
    public void setTags() {
        mSet |= TAGS_SET;
        buildWhat();
    }

    // Do not add @JsonIgnore here.
    public void setCred() {
        mSet |= CRED_SET;
        buildWhat();
    }

    // Do not add @JsonIgnore here.
    public void setAux() {
        mSet |= AUX_SET;
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
        if ((mSet & CRED_SET) != 0) {
            parts.add(CRED);
        }
        if ((mSet & AUX_SET) != 0) {
            parts.add(AUX);
        }

        if (!parts.isEmpty()) {
            sb.append(parts.get(0));
            for (int i=1; i < parts.size(); i++) {
                sb.append(" ").append(parts.get(i));
            }
        }
        what = sb.toString();
    }

    @JsonIgnore
    public boolean isEmpty() {
        return mSet == 0;
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

    public static MsgGetMeta cred() {
        return new MsgGetMeta(CRED);
    }
}
