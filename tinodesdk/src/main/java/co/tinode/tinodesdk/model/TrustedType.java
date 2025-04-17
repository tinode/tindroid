package co.tinode.tinodesdk.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.io.Serializable;
import java.util.HashMap;

import co.tinode.tinodesdk.Tinode;

/**
 * Common type of the `private` field of {meta}: holds structured
 * data, such as comment and archival status.
 */
public class TrustedType extends HashMap<String,Object> implements Mergeable, Serializable {
    public TrustedType() {
        super();
    }

    @JsonIgnore
    public Boolean getBooleanValue(String name) {
        Object val = get(name);
        if (Tinode.isNull(val)) {
            return false;
        }
        if (val instanceof Boolean) {
            return (boolean) val;
        }
        return false;
    }

    @Override
    public boolean merge(Mergeable another) {
        if (!(another instanceof TrustedType apt)) {
            return false;
        }
        this.putAll(apt);
        return apt.size() > 0;
    }
}
