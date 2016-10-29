package co.tinode.tindroid;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;

/**
 * Helper class to make avatars round
 */
public class RoundedImage extends BitmapDrawable {
    private Paint mPaint;
    private RectF mRectF;
    private int mBitmapWidth;
    private int mBitmapHeight;
    private Boolean mOnline;
    private Paint mPaintOnline;

    public RoundedImage(Bitmap bmp) {
        super(bmp);
        init(bmp);
    }

    public RoundedImage(Resources res, Bitmap bmp) {
        super(res, bmp);
        init(bmp);
    }

    public RoundedImage(Bitmap bmp, boolean online) {
        super(bmp);
        init(bmp);
        setOnline(online);
    }


    private void init(Bitmap bmp) {
        mOnline = null;
        mRectF = new RectF();
        mPaint = new Paint();
        mPaint.setAntiAlias(true);
        mPaint.setDither(true);
        mPaint.setShader(new BitmapShader(bmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
        mBitmapWidth = bmp.getWidth();
        mBitmapHeight = bmp.getHeight();
    }

    public Bitmap getRoundedBitmap() {
        Bitmap bmp = Bitmap.createBitmap(mBitmapWidth, mBitmapHeight, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        draw(canvas);
        return bmp;
    }

    @Override
    public void draw(Canvas canvas) {
        canvas.drawOval(mRectF, mPaint);
        if (mOnline != null) {
            float radius = mBitmapWidth / 8.0f;
            canvas.drawCircle(mRectF.right - radius, mRectF.bottom - radius, radius, mPaintOnline);
        }
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        super.onBoundsChange(bounds);
        mRectF.set(bounds);
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
        return mBitmapWidth;
    }

    @Override
    public int getIntrinsicHeight() {
        return mBitmapHeight;
    }


    @Override
    public void setFilterBitmap(boolean filter) {
        mPaint.setFilterBitmap(filter);
        invalidateSelf();
    }

    public void setOnline(boolean online) {
        mOnline = online;
        mPaintOnline = new Paint();
        mPaintOnline.setColor(online ? UiUtils.COLOR_ONLINE : UiUtils.COLOR_OFFLINE);
    }
}
