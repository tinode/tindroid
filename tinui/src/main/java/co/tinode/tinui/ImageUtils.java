package co.tinode.tinui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.content.res.ResourcesCompat;
import androidx.exifinterface.media.ExifInterface;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class ImageUtils {
    private static final String TAG = "ImageUtils";

    // Maximum linear dimensions of images.
    public static final int MAX_BITMAP_SIZE = 1024;
    public static final int AVATAR_THUMBNAIL_DIM = 36; // dip
    // Image thumbnail in quoted replies and reply/forward previews.
    public static final int REPLY_THUMBNAIL_DIM = 36;
    // Image preview size in messages.
    public static final int IMAGE_PREVIEW_DIM = 64;
    public static final int MIN_AVATAR_SIZE = 8;
    public static final int MAX_AVATAR_SIZE = 384;
    // Maximum byte size of avatar sent in-band.
    public static final int MAX_INBAND_AVATAR_SIZE = 4096;

    /**
     * Ensure that the bitmap is square and no larger than the given max size.
     * @param bmp       bitmap to scale
     * @param size   maximum linear size of the bitmap.
     * @return scaled bitmap or original, it it does not need ot be cropped or scaled.
     */
    @NonNull
    public static Bitmap scaleSquareBitmap(@NonNull Bitmap bmp, int size) {
        // Sanity check
        size = Math.min(size, MAX_BITMAP_SIZE);

        int width = bmp.getWidth();
        int height = bmp.getHeight();

        // Does it need to be scaled down?
        if (width > size && height > size) {
            // Scale down.
            if (width > height) /* landscape */ {
                width = width * size / height;
                height = size;
            } else /* portrait or square */ {
                height = height * size / width;
                width = size;
            }
            // Scale down.
            bmp = Bitmap.createScaledBitmap(bmp, width, height, true);
        }
        size = Math.min(width, height);

        if (width != height) {
            // Bitmap is not square. Chop the square from the middle.
            bmp = Bitmap.createBitmap(bmp, (width - size) / 2, (height - size) / 2,
                    size, size);
        }

        return bmp;
    }

    @NonNull
    public static ByteArrayInputStream bitmapToStream(@NonNull Bitmap bmp, String mimeType) {
        return new ByteArrayInputStream(bitmapToBytes(bmp, mimeType));
    }

    @NonNull
    public static byte[] bitmapToBytes(@NonNull Bitmap bmp, String mimeType) {
        Bitmap.CompressFormat fmt;
        if ("image/jpeg".equals(mimeType)) {
            fmt = Bitmap.CompressFormat.JPEG;
        } else {
            fmt = Bitmap.CompressFormat.PNG;
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        bmp.compress(fmt, 70, bos);
        byte[] bits = bos.toByteArray();
        try {
            bos.close();
        } catch (IOException ignored) {
        }

        return bits;
    }

    /**
     * Scale bitmap down to be under certain liner dimensions but no less than by the given amount.
     *
     * @param bmp       bitmap to scale.
     * @param maxWidth  maximum allowed bitmap width.
     * @param maxHeight maximum allowed bitmap height.
     * @return scaled bitmap or original, it it does not need ot be scaled.
     */
    @NonNull
    public static Bitmap scaleBitmap(@NonNull Bitmap bmp, final int maxWidth, final int maxHeight) {
        int width = bmp.getWidth();
        int height = bmp.getHeight();
        float factor = 1.0f;
        // Calculate scaling factor due to large linear dimensions.
        if (width >= height) {
            if (width > maxWidth) {
                factor = (float) width / maxWidth;
            }
        } else {
            if (height > maxHeight) {
                factor = (float) height / maxHeight;
            }
        }
        // Scale down.
        if (factor > 1.0) {
            height /= factor;
            width /= factor;
            return Bitmap.createScaledBitmap(bmp, width, height, true);
        }
        return bmp;
    }

    @NonNull
    public static Bitmap rotateBitmap(@NonNull Bitmap bmp, int orientation) {
        Matrix matrix = new Matrix();
        switch (orientation) {
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                matrix.setScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                matrix.setRotate(180);
                break;
            case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                matrix.setRotate(180);
                matrix.postScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_TRANSPOSE:
                matrix.setRotate(90);
                matrix.postScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_ROTATE_90:
                matrix.setRotate(90);
                break;
            case ExifInterface.ORIENTATION_TRANSVERSE:
                matrix.setRotate(-90);
                matrix.postScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                matrix.setRotate(-90);
                break;
            case ExifInterface.ORIENTATION_NORMAL:
            default:
                return bmp;
        }

        try {
            Bitmap rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), matrix, true);
            bmp.recycle();
            return rotated;
        } catch (OutOfMemoryError ex) {
            Log.e(TAG, "Out of memory while rotating bitmap");
            return bmp;
        }
    }

    /**
     * Convert drawable to bitmap.
     *
     * @param drawable vector drawable to convert to bitmap
     * @return bitmap extracted from the drawable.
     */
    public static Bitmap bitmapFromDrawable(Drawable drawable) {
        if (drawable instanceof BitmapDrawable) {
            return ((BitmapDrawable) drawable).getBitmap();
        }

        Bitmap bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(),
                drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);

        return bitmap;
    }

    // Creates LayerDrawable of the right size with gray background and 'fg' in the middle.
    // Used in chat bubbled to generate placeholder and error images for Picasso.
    public static Drawable getPlaceholder(Context ctx, Drawable fg, Drawable bkg, int width, int height) {
        Drawable filter;
        if (bkg == null) {
            // Uniformly gray background with rounded corners.
            bkg = ResourcesCompat.getDrawable(ctx.getResources(), R.drawable.tinui_placeholder_image_bkg, null);
            // Transparent filter.
            filter = new ColorDrawable(0x00000000);
        } else {
            // Translucent filter.
            filter = new ColorDrawable(0xCCCCCCCC);
        }

        final int fgWidth = fg.getIntrinsicWidth();
        final int fgHeight = fg.getIntrinsicHeight();
        final LayerDrawable result = new LayerDrawable(new Drawable[]{bkg, filter, fg});
        result.setBounds(0, 0, width, height);
        // Move foreground to the center of the drawable.
        int dx = Math.max((width - fgWidth) / 2, 0);
        int dy = Math.max((height - fgHeight) / 2, 0);
        fg.setBounds(dx, dy, dx + fgWidth, dy + fgHeight);
        return result;
    }
}
