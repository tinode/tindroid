package co.tinode.tindroid.media;

import android.graphics.Bitmap;

import com.fasterxml.jackson.annotation.JsonIgnore;

import co.tinode.tinodesdk.model.VCard;


/**
 * VxCard - contact descriptor.
 * Adds avatar conversion from bits to Android bitmap and back.
 */
public class VxCard extends VCard {
    @JsonIgnore
    public AvatarPhoto avatar;

    public VxCard() {
    }

    public VxCard(String fullName, byte[] avatar, String avatarImageType) {
        super(fullName, avatar, avatarImageType);
        constructBitmap();
    }

    public VxCard(String fullName, Bitmap bmp) {
        fn = fullName;
        if (bmp != null) {
            avatar = new AvatarPhoto(bmp);
            photo = new Photo(avatar.data, avatar.type);
        }
    }

    @Override
    public VxCard copy() {
        VxCard dst = copy(new VxCard(), this);
        dst.avatar = avatar;
        return dst;
    }

    @JsonIgnore
    public Bitmap getBitmap() {
        if (avatar == null) {
            constructBitmap();
        }
        return (avatar != null) ? avatar.getBitmap() : null;
    }
    @JsonIgnore
    public void setBitmap(Bitmap bmp) {
        avatar = new AvatarPhoto(bmp);
        photo = new Photo(avatar.data, avatar.type);
    }
    @JsonIgnore
    public void setAvatar(AvatarPhoto bmp) {
        avatar = bmp;
        photo = new Photo(avatar.data, avatar.type);
    }

    public void constructBitmap() {
        if (photo != null) {
            avatar = new AvatarPhoto(photo.data);
        }
    }
}
