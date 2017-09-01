package co.tinode.tindroid.media;


import java.io.Serializable;
import java.util.LinkedList;
import java.util.List;

/**
 * Asset: container for the media sent in a message.
 * With everything being default, it adds minimal overhead.
 */
public class Asset extends Media implements Serializable {
    public static final String MIME_TYPE = "application/vnd.tinode.asset";

    // Attachments or other versions.
    // It is the preview/default one to show (could be the only one).
    public List<Media> parts;

    public Asset() {
    }

    public int addMedia(Media media) {
        if (parts == null) {
            parts = new LinkedList<>();
        }
        parts.add(media);
        return parts.size();
    }
}
