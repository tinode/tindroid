package co.tinode.tindroid;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.HardwareRenderer;
import android.graphics.Matrix;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.RecordingCanvas;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.hardware.HardwareBuffer;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlur;
import android.util.Log;
import android.view.Gravity;
import android.widget.ImageView;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.exifinterface.media.ExifInterface;

import co.tinode.tindroid.widgets.BlurTransformation;

import coil.Coil;
import coil.request.ImageRequest;
import coil.target.Target;

public class UtilsBitmap {
    private static final String TAG = "UtilsBitmap";

    private static ColorMatrixColorFilter sInverter = null;

    /**
     * Ensure that the bitmap is square and no larger than the given max size.
     * @param bmp       bitmap to scale
     * @param size   maximum linear size of the bitmap.
     * @return scaled bitmap or original, it it does not need ot be cropped or scaled.
     */
    @NonNull
    public static Bitmap scaleSquareBitmap(@NonNull Bitmap bmp, int size) {
        // Sanity check
        size = Math.min(size, Const.MAX_BITMAP_SIZE);

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

    /**
     * Scale bitmap down to be under certain liner dimensions.
     *
     * @param bmp       bitmap to scale.
     * @param maxWidth  maximum allowed bitmap width.
     * @param maxHeight maximum allowed bitmap height.
     * @param upscale enable increasing size of the image (up to 10x).
     * @return scaled bitmap or original, it it does not need to be scaled.
     */
    @NonNull
    public static Bitmap scaleBitmap(@NonNull Bitmap bmp, final int maxWidth, final int maxHeight,
                                     final boolean upscale) {
        int width = bmp.getWidth();
        int height = bmp.getHeight();

        // Calculate scaling factor.
        float factor = Math.max((float) width / maxWidth, upscale ? 0.1f : 1.0f);
        factor = Math.max((float) height / maxHeight, factor);

        // Scale down.
        if (upscale || factor > 1.0) {
            height = (int) (height / factor);
            width = (int) (width / factor);
            return Bitmap.createScaledBitmap(bmp, width, height, true);
        }
        return bmp;
    }

    @NonNull
    static Bitmap rotateBitmap(@NonNull Bitmap bmp, int orientation) {
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

    @SuppressWarnings("deprecation")
    public static Bitmap blurBitmap(Context context, Bitmap bitmap, float radius) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return blurBitmap_OLD(context, bitmap, radius);
        }
        return blurBitmap_S(bitmap, radius);
    }

    // Blur bitmap using deprecated API (<S - API 31).
    @Deprecated
    @SuppressWarnings("deprecation")
    private static Bitmap blurBitmap_OLD(Context context, Bitmap bitmap, float radius) {
        Bitmap.Config config = bitmap.getConfig();
        if (config == null) {
            config = Bitmap.Config.ARGB_8888;
        }

        Bitmap output = Bitmap.createBitmap(
                bitmap.getWidth(),
                bitmap.getHeight(),
                config
        );

        RenderScript rs = RenderScript.create(context);
        ScriptIntrinsicBlur script = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs));
        Allocation inAlloc = Allocation.createFromBitmap(rs, bitmap);
        Allocation outAlloc = Allocation.createFromBitmap(rs, output);

        script.setRadius(Math.min(radius, 25f)); // 0 < radius <= 25
        script.setInput(inAlloc);
        script.forEach(outAlloc);
        outAlloc.copyTo(output);

        script.destroy();
        inAlloc.destroy();
        outAlloc.destroy();
        rs.destroy();

        return output;
    }

    // Blur bitmap using modern approach.
    @RequiresApi(api = Build.VERSION_CODES.S)
    private static Bitmap blurBitmap_S(Bitmap bitmap, float radius) {
        ImageReader imageReader = ImageReader.newInstance(
                bitmap.getWidth(), bitmap.getHeight(),
                PixelFormat.RGBA_8888, 1,
                HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE | HardwareBuffer.USAGE_GPU_COLOR_OUTPUT
        );
        RenderNode renderNode = new RenderNode("BlurEffect");
        HardwareRenderer hardwareRenderer = new HardwareRenderer();

        hardwareRenderer.setSurface(imageReader.getSurface());
        hardwareRenderer.setContentRoot(renderNode);
        renderNode.setPosition(0, 0, imageReader.getWidth(), imageReader.getHeight());
        RenderEffect blurRenderEffect = RenderEffect.createBlurEffect(
                radius, radius,
                Shader.TileMode.MIRROR
        );
        renderNode.setRenderEffect(blurRenderEffect);

        RecordingCanvas renderCanvas = renderNode.beginRecording();
        renderCanvas.drawBitmap(bitmap, 0f, 0f, null);
        renderNode.endRecording();
        hardwareRenderer.createRenderRequest()
                .setWaitForPresent(true)
                .syncAndDraw();

        Image image = imageReader.acquireNextImage();
        HardwareBuffer hardwareBuffer = image.getHardwareBuffer();

        Bitmap result = bitmap;
        if (hardwareBuffer != null) {
            result = Bitmap.wrapHardwareBuffer(hardwareBuffer, null);
            hardwareBuffer.close();
        }

        image.close();
        imageReader.close();
        renderNode.discardDisplayList();
        hardwareRenderer.destroy();

        return result;
    }

    // Convert DP units to pixels.
    public static int dpToPx(Context ctx, int dp) {
        return Math.round(dp * ctx.getResources().getDisplayMetrics().density);
    }

    public static void assignBackgroundImage(final Context context,
                                             final ImageView target,
                                             final String sourceUrl,
                                             final int size,
                                             final int blur) {
        if (sInverter == null) {
            ColorMatrix cm = new ColorMatrix();
            cm.set(new float[] {
                    -1,  0,  0, 0, 255,  // R
                    0, -1,  0, 0, 255,  // G
                    0,  0, -1, 0, 255,  // B
                    0,  0,  0, 1,   0   // A
            });
            sInverter = new ColorMatrixColorFilter(cm);
        }

        ImageRequest.Builder builder = new ImageRequest.Builder(context)
                .data(sourceUrl);
        if (size > 0) {
            builder.size(dpToPx(context, size));
        } else if (blur > 0 && blur <= 5) {
            float[] radius = new float[]{0, 1, 2, 4, 8, 16};
            builder.transformations(new BlurTransformation(context, radius[blur]));
        }

        final boolean nightMode = UiUtils.isNightMode(context);
        ImageRequest request = builder.target(new Target() {
                    @Override
                    public void onSuccess(@NonNull Drawable result) {
                        if (result instanceof BitmapDrawable) {
                            if (size > 0) {
                                // Pattern.
                                target.setScaleType(ImageView.ScaleType.FIT_XY);
                                ((BitmapDrawable) result).setTileModeXY(Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
                            } else {
                                // Wallpaper.
                                target.setScaleType(ImageView.ScaleType.CENTER_CROP);
                                ((BitmapDrawable) result).setGravity(Gravity.CENTER);
                            }
                        }
                        if (nightMode) {
                            if (size > 0) {
                                target.setColorFilter(sInverter);
                            } else {
                                target.setColorFilter(Color.rgb(192, 192, 192),
                                        PorterDuff.Mode.MULTIPLY);
                            }
                        } else {
                            target.clearColorFilter();
                        }

                        target.setImageDrawable(result);
                    }

                    @Override
                    public void onError(@Nullable Drawable error) {
                        target.setImageDrawable(null);
                    }
                })
                .build();
        Coil.imageLoader(context).enqueue(request);
    }

    @NonNull
    static ByteArrayInputStream bitmapToStream(@NonNull Bitmap bmp, String mimeType) {
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
     * Convert drawable to bitmap.
     *
     * @param drawable vector drawable to convert to bitmap
     * @return bitmap extracted from the drawable.
     */
    public static Bitmap bitmapFromDrawable(Drawable drawable) {
        if (drawable == null) {
            return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
        }

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
}
