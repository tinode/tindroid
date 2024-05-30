package co.tinode.tindroid.widgets;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;

/**
 * Helper class to make avatars round.
 */
public class RoundImageDrawable extends BitmapDrawable {
    private static final Matrix sMatrix = new Matrix();

    private final Paint mPaint = new Paint();

    private final Bitmap mBitmap;
    private final Rect mBitmapRect;

    public RoundImageDrawable(Resources res, Bitmap bmp) {
        super(res, bmp);

        mPaint.setAntiAlias(true);
        mPaint.setFilterBitmap(true);
        mPaint.setDither(true);

        mPaint.setShader(new BitmapShader(bmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));

        mBitmap = bmp;
        mBitmapRect = new Rect(0, 0, bmp.getWidth(), bmp.getHeight());
    }

    @Override
    public void draw(Canvas canvas) {
        // Create shader from bitmap.
        BitmapShader shader = (BitmapShader) mPaint.getShader();
        if (shader == null) {
            shader = new BitmapShader(mBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        }
        // Fit bitmap to the bounds of the drawable.
        sMatrix.reset();
        Rect dst = getBounds();
        float scale = Math.max((float) dst.width() / mBitmapRect.width(),
                (float) dst.height() / mBitmapRect.height());
        sMatrix.postScale(scale, scale);
        // Translate bitmap to dst bounds.
        sMatrix.postTranslate(dst.left, dst.top);
        shader.setLocalMatrix(sMatrix);
        mPaint.setShader(shader);
        canvas.drawCircle(dst.centerX(), dst.centerY(), dst.width() * 0.5f, mPaint);
    }

    @Override
    public void setAlpha(int alpha) {
        if (mPaint.getAlpha() != alpha) {
            mPaint.setAlpha(alpha);
            invalidateSelf();
        }
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
        mPaint.setColorFilter(cf);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    @Override
    public int getIntrinsicWidth() {
        return mBitmapRect.width();
    }

    @Override
    public int getIntrinsicHeight() {
        return mBitmapRect.height();
    }

    @Override
    public void setFilterBitmap(boolean filter) {
        mPaint.setFilterBitmap(filter);
        invalidateSelf();
    }
}
