package co.tinode.tinodesdk.model;

import java.util.Date;

/**
 * Created by gsokolov on 2/12/16.
 */
public class MsgGetMeta {
    public String what;
    public Desc desc;
    public Sub sub;
    public Data data;

    public class Desc {
        public Date ims;
    }

    public class Sub {
        public Date ims;
        public int limit;
    }

    public class Data {
        public int since;
        public int before;
        public int limit;
    }
}
