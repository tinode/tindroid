package co.tinode.tindroid.widgets;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import androidx.annotation.NonNull;
import co.tinode.tindroid.UiUtils;
import coil.size.Size;
import coil.transform.Transformation;
import kotlin.coroutines.Continuation;

public class BlurTransformation implements Transformation {
    private static final String TAG = "BlurTransformation";

    private final Context context;
    private final float radius;

    public BlurTransformation(Context context, float radius) {
        this.context = context;
        this.radius = radius;
    }

    @NonNull
    @Override
    public String getCacheKey() {
        return "blur_" + radius;
    }

    @NonNull
    @Override
    public Object transform(@NonNull Bitmap input, @NonNull Size size,
                            @NonNull Continuation<? super Bitmap> continuation) {
        Log.i(TAG, "transforming " + input.getWidth() + "x" + input.getHeight() + "; radius=" + radius);
        return UiUtils.blurBitmap(context, input, radius);
    }
}
