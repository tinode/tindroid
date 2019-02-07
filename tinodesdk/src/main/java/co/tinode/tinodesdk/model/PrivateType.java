package co.tinode.tinodesdk.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.HashMap;

import co.tinode.tinodesdk.Tinode;

/**
 * Common type of the `private` field of {meta}: holds structured
 * data, such as comment and archival status.
 */
public class PrivateType extends HashMap<String,Object> {
    public PrivateType() {
        super();
    }

    @JsonIgnore
    public String getComment() {
        String comment = (String) get("comment");
        if (Tinode.isNull(comment)) {
            return null;
        }
        return comment;
    }

    @JsonIgnore
    public void setComment(String comment) {
        put("comment", comment != null && comment.length() > 0 ? comment : Tinode.NULL_VALUE);
    }

    @JsonIgnore
    public Boolean isArchived() {
        Object arch = get("arch");
        if (arch == null) {
            return null;
        }
        if (Tinode.isNull(arch)) {
            return false;
        }
        return (Boolean) arch;
    }

    @JsonIgnore
    public void setArchived(boolean arch) {
        put("arch", arch ? true : Tinode.NULL_VALUE);
    }

}
