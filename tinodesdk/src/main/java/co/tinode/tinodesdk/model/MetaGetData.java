package co.tinode.tinodesdk.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.util.Arrays;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_DEFAULT;

/**
 * Parameter of MsgGetMeta
 */
@JsonInclude(NON_DEFAULT)
public class MetaGetData implements Serializable {
    // Inclusive (closed): ID >= since.
    public Integer since;
    // Exclusive (open): ID < before.
    public Integer before;
    public Integer limit;
    public MsgRange[] ranges;

    public MetaGetData() {}

    public MetaGetData(Integer since, Integer before, Integer limit) {
        this.since = since;
        this.before = before;
        this.limit = limit;
    }

    public MetaGetData(MsgRange[] ranges, Integer limit) {
        this.ranges = ranges;
        this.limit = limit;
    }

    @NotNull
    @Override
    public String toString() {
        return "since=" + since + ", before=" + before +
                ", ranges=" + Arrays.toString(ranges) + ", limit=" + limit;
    }
}