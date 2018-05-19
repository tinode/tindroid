package co.tinode.tindroid.media;

import android.graphics.Bitmap;

import com.fasterxml.jackson.annotation.JsonIgnore;


/**
 * VCard - contact descriptor. Adds avatar conversion from bits to bitmap and back.
 */
public class VCard extends co.tinode.tinodesdk.model.VCard {
    // Avatar photo
    public AvatarPhoto photo;

    @JsonIgnore
    public Bitmap getBitmap() {
        return (photo != null) ? photo.getBitmap() : null;
    }
    @JsonIgnore
    public void setBitmap(Bitmap bmp) {
        photo = new AvatarPhoto(bmp);
    }

    public boolean constructBitmap() {
        return photo != null && photo.constructBitmap();
    }
}
