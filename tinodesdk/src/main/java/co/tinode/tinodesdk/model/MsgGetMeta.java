package co.tinode.tinodesdk.model;

import java.util.Date;

/**
 * Topic metadata request.
 */
public class MsgGetMeta {
    public static final String DESC = "desc";
    public static final String SUB = "sub";
    public static final String DATA = "data";

    public String what;
    public GetDesc desc;
    public GetSub sub;
    public GetData data;

    public MsgGetMeta(String what) {
        this.what = what;
    }

    public MsgGetMeta(GetDesc desc, GetSub sub, GetData data) {
        this.what = "";

        if (desc != null) {
            this.what = DESC;
            this.desc = desc;
        }
        if (sub != null) {
            this.what += " " + SUB;
            this.sub = sub;
        }
        if (data != null) {
            this.what += " " + DATA;
            this.data = data;
        }
        this.what = this.what.trim();
    }

    public static class GetDesc {
        // ims = If modified since...
        public Date ims;
    }

    public static class GetSub {
        public Date ims;
        public Integer limit;
    }

    public static class GetData {
        public Integer since;
        public Integer before;
        public Integer limit;
    }
}
