package co.tinode.tindroid;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

/**
 * Created by gene on 13/02/16.
 */

public class Photo {
    protected Bitmap mImage = null;

    public byte[] data;
    public String type;

    public Photo() {}

    public boolean construct() {
        if (data != null) {
            mImage = BitmapFactory.decodeByteArray(data, 0, data.length);
        }
        return mImage != null;
    }

    public Bitmap getBitmap() {
        return mImage;
    }
}

