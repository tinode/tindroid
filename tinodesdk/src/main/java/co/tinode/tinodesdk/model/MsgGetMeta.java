package co.tinode.tinodesdk.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Arrays;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

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
        this.what = DESC + " " + SUB + " " + DATA;
    }

    public MsgGetMeta(GetDesc desc, GetSub sub, GetData data) {
        this.desc = desc;
        this.sub = sub;
        this.data = data;
        buildWhat();
    }

    @JsonIgnore
    public void setDesc(GetDesc desc) {
        this.desc = desc;
        buildWhat();
    }

    @JsonIgnore
    public void setDesc(GetSub sub) {
        this.sub = sub;
        buildWhat();
    }

    @JsonIgnore
    public void setData(GetData data) {
        this.data = data;
        buildWhat();
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
            for(int i=1; i < parts.size(); i++){
                sb.append(" ").append(parts.get(i));
            }
        }
        what = sb.toString();
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
