package co.tinode.tindroid.media;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.io.ByteArrayOutputStream;

import androidx.annotation.NonNull;
import co.tinode.tindroid.Const;
import co.tinode.tindroid.UiUtils;
import co.tinode.tinodesdk.model.Mergeable;
import co.tinode.tinodesdk.model.TheCard;

/**
 * VxCard - contact descriptor.
 * Adds avatar conversion from bits to Android bitmap and back.
 */
public class VxCard extends TheCard {
    // Cached copy of the image data (photo.data).
    @JsonIgnore
    protected transient Bitmap mImage = null;

    public VxCard() {
    }

    public VxCard(String fullName) {
        fn = fullName;
    }

    public VxCard(String fullName, String note) {
        this.fn = fullName;
        this.note = note;
    }

    @Override
    public VxCard copy() {
        VxCard dst = copy(new VxCard(), this);
        dst.mImage = mImage;
        return dst;
    }

    @JsonIgnore
    public Bitmap getBitmap() {
        if (mImage == null) {
            constructBitmap();
        }
        return mImage;
    }

    @JsonIgnore
    public void setBitmap(Bitmap bmp) {
        mImage = bmp;
        photo = serializeBitmap(bmp);
    }

    @Override
    public boolean merge(Mergeable another) {
        if (!(another instanceof VxCard)) {
            return false;
        }

        boolean changed = super.merge(another);
        if (changed) {
            constructBitmap();
        }
        return changed;
    }

    public void constructBitmap() {
        mImage = null;
        if (photo != null && photo.data != null) {
            Bitmap bmp = BitmapFactory.decodeByteArray(photo.data, 0, photo.data.length);
            if (bmp != null) {
                mImage = UiUtils.scaleSquareBitmap(bmp, Const.MAX_AVATAR_SIZE);
                // createScaledBitmap may return the same object if scaling is not required.
                if (bmp != mImage) {
                    bmp.recycle();
                }
            }
        }
    }

    private static Photo serializeBitmap(Bitmap bmp) {
        if (bmp != null) {
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            bmp.compress(Bitmap.CompressFormat.JPEG, 80, byteArrayOutputStream);
            return new Photo(byteArrayOutputStream.toByteArray(), "jpeg");
        }
        return null;
    }

    @Override
    @NonNull
    public String toString() {
        return "{fn:'" + fn + "'; photo:" +
                (photo != null ? ("'" + photo.type + "'") : "null") + "}";
    }
}
