package co.tinode.tindroid;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.io.ByteArrayOutputStream;

/**
 * Utility class: constructs a Bitmap from bytes/serializes to bytes
 */
public class AvatarPhoto {
    @JsonIgnore
    protected Bitmap mImage = null;

    public byte[] data;
    public String type;
    public String uri;

    public AvatarPhoto() {}

    public AvatarPhoto(byte[] bits) {
        data = bits;
        constructBitmap();
    }

    public AvatarPhoto(Bitmap bmp) {
        this.mImage = bmp;
        serializeBitmap();
    }

    public boolean constructBitmap() {
        if (data != null) {
            mImage = BitmapFactory.decodeByteArray(data, 0, data.length);
        }
        return mImage != null;
    }

    @JsonIgnore
    public Bitmap getBitmap() {
        return mImage;
    }

    protected void serializeBitmap() {
        if (mImage != null) {
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            mImage.compress(Bitmap.CompressFormat.JPEG, 80, byteArrayOutputStream);
            this.data = byteArrayOutputStream.toByteArray();
            this.type = "jpg";
        }
    }
}

