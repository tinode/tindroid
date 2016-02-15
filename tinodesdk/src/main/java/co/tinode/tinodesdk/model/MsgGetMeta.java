package co.tinode.tinodesdk.model;

import java.util.Date;

/**
 * Created by gsokolov on 2/12/16.
 */
public class MsgGetMeta {
    public String what;
    public GetDesc desc;
    public GetSub sub;
    public GetData data;

    public class GetDesc {
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
