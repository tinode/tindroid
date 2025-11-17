package co.tinode.tindroid.widgets;

import android.content.Context;
import android.graphics.Bitmap;

import androidx.annotation.NonNull;
import co.tinode.tindroid.UtilsBitmap;
import coil.size.Size;
import coil.transform.Transformation;
import kotlin.coroutines.Continuation;

public class BlurTransformation implements Transformation {
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
        return UtilsBitmap.blurBitmap(context, input, radius);
    }
}
