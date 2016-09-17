package co.tinode.tinodesdk.model;

import java.util.Date;

/**
 * Topic metadata request.
 */
public class MsgGetMeta {
    public String what;
    public GetDesc desc;
    public GetSub sub;
    public GetData data;

    public static class GetDesc {
        // ims = If modified since...
        public Date ims;
    }

    public static class GetSub {
        public Date ims;
        public int limit;
    }

    public static class GetData {
        public int since;
        public int before;
        public int limit;
    }
}
