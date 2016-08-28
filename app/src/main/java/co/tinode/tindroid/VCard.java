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

    public VCard(String fullName, Bitmap avatar) {
        this.fn = fullName;
        this.photo = new AvatarPhoto(avatar);
    }

    public Bitmap getBitmap() {
        return (photo != null) ? photo.getBitmap() : null;
    }

    public boolean constructBitmap() {
        return photo != null && photo.construct();
    }
}
