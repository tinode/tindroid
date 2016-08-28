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

    public class GetDesc {
        // ims = If modified since...
        public Date ims;
    }

    public class GetSub {
        public Date ims;
        public int limit;
    }

    public class GetData {
        public int since;
        public int before;
        public int limit;
    }
}
