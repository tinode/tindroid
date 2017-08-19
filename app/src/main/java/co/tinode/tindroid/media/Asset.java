package co.tinode.tindroid.media;

import java.io.Serializable;
import java.net.URL;
import java.util.Arrays;

/**
 * Asset: container for the media sent in a message
 */
public class Asset implements Serializable {
    // Media type == MIME Content-Type
    public String type;
    // File name or similar info
    public String name;
    // Caption
    public String caption;

    // Presentations, possibly multiple versions. At least one version must be present.
    // Version at index 0 is the preview/default one to show (could be the only one)
    public Media[] media;

    public Asset() {
    }

    public Asset(String type, String name, String caption) {
        this.type = type;
        this.name = name;
        this.caption = caption;
    }

    public Asset(String type) {
        this(type, null, null);
    }

    public int addMedia(Media m) {
        if (media == null) {
            media = new Media[]{m};
        } else {
            media = Arrays.copyOf(media, media.length + 1);
            media[media.length-1] = m;
        }
        return media.length;
    }

    // Base interface for defining media metadata, such as width, height, duration etc.
    public interface MetaData extends Serializable {
    }

    // Metadata for mime-types image/*
    public class ImageMetaData implements MetaData {
        // Width and height in pixels.
        public int width;
        public int height;
    }

    // Metadata for mime-types video/*
    public class VideoMetaData implements MetaData {
        public int width;
        public int height;
        public int duration; // in milliseconds
    }

    // Metadata for mime-types audio/*
    public class AudioMetaData implements MetaData {
        public int duration; // in milliseconds
    }

    // Actual media, in-band or out-of-band (remote)
    public class Media implements Serializable {
        // MIME Content-Type, if different from Asset::type,
        // for instance, image/jpeg preview of a video/mpeg media asset.
        // Optional.
        public String type;
        // Media metadata, such as width, height, duration etc. Optional.
        public MetaData meta;
        // Optional description of this part, i.e. "preview"
        public String desc;
        // Optional URL for remote media
        public URL url;
        // Media size, primarily for remote media. 0 == "unknown/don't care"
        public long size;
        // Optional, data for in-band media.
        public byte[] data;

        public Media() {
        }

        // Construct remote media
        public Media(String type, MetaData meta, String desc, URL url, long size) {
            this.type = type;
            this.meta = meta;
            this.desc = desc;
            this.url = url;
            this.size = size;
        }

        public Media(MetaData meta, URL url, long size) {
            this(null, meta, null, url, size);
        }

        // Construct in-band media
        public Media(String type, MetaData meta, String desc, byte[] data) {
            this.type = type;
            this.meta = meta;
            this.desc = desc;
            this.data = data;
        }

        public Media(MetaData meta, byte[] data) {
            this(null, meta, null, data);
        }
    }
}
