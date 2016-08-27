package co.tinode.tindroid;

import android.graphics.Bitmap;

/**
 * VCard - contact descriptor.
 */
public class VCard {
    // Full name
    public String fn;
    // Avatar photo
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
