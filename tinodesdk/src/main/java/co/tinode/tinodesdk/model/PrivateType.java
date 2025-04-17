package co.tinode.tinodesdk.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.io.Serializable;
import java.util.HashMap;

import co.tinode.tinodesdk.Tinode;

/**
 * Common type of the `private` field of {meta}: holds structured
 * data, such as comment and archival status.
 */
public class PrivateType extends HashMap<String,Object> implements Mergeable, Serializable {
    public PrivateType() {
        super();
    }

    @JsonIgnore
    private Object getValue(String name) {
        Object value = get(name);
        if (Tinode.isNull(value)) {
            value = null;
        }
        return value;
    }

    @JsonIgnore
    public String getComment() {
        try {
            return (String) getValue("comment");
        } catch (ClassCastException ignored) {}
        return null;
    }

    @JsonIgnore
    public void setComment(String comment) {
        put("comment", comment != null && !comment.isEmpty() ? comment : Tinode.NULL_VALUE);
    }

    public Boolean isArchived() {
        try {
            return (Boolean) getValue("arch");
        } catch (ClassCastException ignored) {}
        return Boolean.FALSE;
    }

    @JsonIgnore
    public void setArchived(boolean arch) {
        put("arch", arch ? true : Tinode.NULL_VALUE);
    }

    @Override
    public boolean merge(Mergeable another) {
        if (!(another instanceof PrivateType apt)) {
            return false;
        }
        this.putAll(apt);
        return apt.size() > 0;
    }
}
