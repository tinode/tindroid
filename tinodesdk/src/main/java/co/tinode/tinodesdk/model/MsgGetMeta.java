package co.tinode.tinodesdk.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Date;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_DEFAULT;

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

    /**
     * Query to get all
     */
    public MsgGetMeta() {
        this(new String[]{DESC, SUB, DATA});
    }

    public MsgGetMeta(String[] what) {
        StringBuilder sb = new StringBuilder();
        if (what.length > 0) {
            sb.append(what[0]);
            for(int i=1; i < what.length; i++){
                sb.append(" ").append(what[i]);
            }
        }
        this.what = sb.toString();
    }

    public MsgGetMeta(GetDesc desc, GetSub sub, GetData data) {
        StringBuilder sb = new StringBuilder();

        if (desc != null) {
            sb.append(DESC);
            this.desc = desc;
        }
        if (sub != null) {
            sb.append(" ").append(SUB);
            this.sub = sub;
        }
        if (data != null) {
            sb.append(" ").append(DATA);
            this.data = data;
        }
        this.what = sb.toString().trim();
    }

    @JsonInclude(NON_DEFAULT)
    public static class GetDesc {
        // ims = If modified since...
        public Date ims;

        public GetDesc() {}
    }

    @JsonInclude(NON_DEFAULT)
    public static class GetSub {
        public Date ims;
        public Integer limit;

        public GetSub() {}
    }

    @JsonInclude(NON_DEFAULT)
    public static class GetData {
        public Integer since;
        public Integer before;
        public Integer limit;

        public GetData() {}
    }
}
