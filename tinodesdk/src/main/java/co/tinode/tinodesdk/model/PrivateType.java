package co.tinode.tinodesdk.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.HashMap;

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
        return (String) get("comment");
    }

    @JsonIgnore
    public String setComment(String comment) {
        return (String) put("comment", comment);
    }
}
