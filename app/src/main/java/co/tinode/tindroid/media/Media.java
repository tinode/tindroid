package co.tinode.tindroid.media;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.io.Serializable;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 * Actual media, in-band or out-of-band (remote).
 */
public class Media implements Serializable {
    // MIME Content-Type of this part,
    // for instance, image/jpeg preview of a video/mpeg media asset.
    // Optional, null means text/plain.
    public String mime;
    // Content-disposition: "att" - attachment, "alt" - alternative,
    // "inl" or null or "" - inline (referenced from another media block by media.name).
    public String disp;
    // Media metadata, such as width, height, duration etc. Optional.
    public Map<String,String> meta;
    // Optional description of this part, i.e. "preview" or file name of this attachment.
    public String name;
    // Optional URL for remote media
    public URL url;
    // Optional, data for in-band media.
    public Object data;

    public Media() {
    }

    // Construct Media object with out of band data.
    public Media(String mime, Map<String,String> meta, String name, URL url) {
        this.mime = mime;
        this.meta = meta;
        this.name = name;
        this.url = url;
    }

    // Construct Media object with in-band data
    public Media(String mime, Map<String,String> meta, String name, Object data) {
        this.mime = mime;
        this.meta = meta;
        this.name = name;
        this.data = data;
    }

    // Get metadata as Long or null if data is missing or cannot be parsed as Long.
    @JsonIgnore
    public Long getLongMeta(String name) {
        try {
            return Long.decode(meta.get(name));
        } catch (Exception ignored) {
            return null;
        }
    }

    // Get metadata as string
    @JsonIgnore
    public String getStringMeta(String name) {
        return meta != null ? meta.get(name) : null;
    }

    @JsonIgnore
    public void setMeta(String name, long val) {
        if (meta == null) {
            meta = new HashMap<>();
        }
        meta.put(name, Long.toString(val));
    }

    @JsonIgnore
    public void setMeta(String name, String val) {
        if (val != null) {
            if (meta == null) {
                meta = new HashMap<>();
            }
            meta.put(name, val);
        }
    }
}
