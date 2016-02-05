package co.tinode.tindroid;

/**
 * Created by gsokolov on 2/4/16.
 */
public class VCard {
    public String fn;
    public Photo phot;

    public class Photo {
        public byte[] data;
        public String type;
    }
}
