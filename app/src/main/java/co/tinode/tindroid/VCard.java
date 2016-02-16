package co.tinode.tindroid;

import android.graphics.Bitmap;

/**
 * Created by gsokolov on 2/4/16.
 */
public class VCard {
    public String fn;
    public AvatarPhoto photo;

    public VCard() {
    }

    public Bitmap getBitmap() {
        return (photo != null) ? photo.getBitmap() : null;
    }

    public boolean constructBitmap() {
        return photo != null && photo.construct();
    }
}
