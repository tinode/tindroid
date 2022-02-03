package co.tinode.tinodesdk.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import co.tinode.tinodesdk.Tinode;

/**
 * Common type of the `private` field of {meta}: holds structured
 * data, such as comment and archival status.
 */
public class PrivateType extends HashMap<String,Object> implements Mergeable, Serializable {
    public PrivateType() {
        super();
    }

    public PrivateType(String text) {
        super();
        String[] splitted = text.split(":");
        int counter = 0;
        while (splitted.length % 2 == 0 && counter < splitted.length) {
            put(splitted[counter], splitted[counter + 1]);
            counter = counter + 2;
        }
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
        put("comment", comment != null && comment.length() > 0 ? comment : Tinode.NULL_VALUE);
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
        if (!(another instanceof PrivateType)) {
            return false;
        }
        PrivateType apt = (PrivateType) another;
        for (Map.Entry<String, Object> e : apt.entrySet()) {
            put(e.getKey(), e.getValue());
        }
        return apt.size() > 0;
    }
}
