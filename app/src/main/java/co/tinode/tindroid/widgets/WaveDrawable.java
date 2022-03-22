package co.tinode.tindroid.widgets;

import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import co.tinode.tindroid.R;

/**
 * Drawable which visualizes sound amplitudes as a waveform.
 */
public class WaveDrawable extends Drawable {
    // Bars and spacing sizes in DP.
    private static final float LINE_WIDTH = 3f;
    private static final float SPACING = 1f;

    // Display density.
    private static float sDensity = -1f;

    // Bars and spacing sizes in pixels.
    private static float sLineWidth;
    private static float sSpacing;

    // Amplitude values passed by the caller resampled to fit the screen.
    private final float[] mBuffer;
    // Count of amplitude values actually added to the buffer.
    private int mContains;
    // Entry point in mBuffer (mBuffer is a circular buffer).
    private int mIndex;
    // Position of the playback as a fraction of the total.
    private float mCurrent;
    // Array of 4 values for each bar: startX, startY, stopX, stopY.
    private float[] mBars = null;
    // Canvas width which fits whole number of bars.
    private final int mEffectiveWidth;

    @ColorInt private final int mBkgColor;
    private final Paint mBarPaint;
    private final Paint mThumbPaint;

    private final Rect mSize;

    public WaveDrawable(Resources res, Rect size) {
        super();

        mSize = new Rect(size);
        setBounds(mSize);

        if (sDensity <= 0) {
            sDensity = res.getDisplayMetrics().density;
            sLineWidth = LINE_WIDTH * sDensity;
            sSpacing = SPACING * sDensity;
        }

        int maxBars = (int) ((mSize.width() - sSpacing) / (sLineWidth + sSpacing));
        mEffectiveWidth = (int) (maxBars * (sLineWidth + sSpacing) + sSpacing);
        mBuffer = new float[maxBars];

        // Background color.
        mBkgColor = res.getColor(R.color.colorChipBackground);

        // Waveform.
        mBarPaint = new Paint();
        mBarPaint.setStyle(Paint.Style.STROKE);
        mBarPaint.setStrokeWidth(sLineWidth);
        mBarPaint.setStrokeCap(Paint.Cap.ROUND);
        mBarPaint.setAntiAlias(true);
        mBarPaint.setDither(true);
        mBarPaint.setColor(res.getColor(R.color.waveform));

        // Seek thumb.
        mThumbPaint = new Paint();
        mThumbPaint.setAntiAlias(true);
        mThumbPaint.setDither(true);
        mThumbPaint.setColor(res.getColor(R.color.colorAccent));
    }

    @Override
    public int getIntrinsicWidth() {
        return mSize.width();
    }

    @Override
    public int getIntrinsicHeight() {
        return mSize.height();
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        // TODO: Draw background.
        // canvas.drawColor(mBkgColor);

        if (mBars == null) {
            return;
        }

        // Draw amplitude bars.
        canvas.drawLines(mBars, mBarPaint);

        // Draw thumb.
        if (mCurrent >= 0) {
            canvas.drawCircle(mCurrent * mEffectiveWidth + sLineWidth * 2f, mSize.height() * 0.5f,
                    sLineWidth * 1.5f, mThumbPaint);
        }
    }

    @Override
    public void setAlpha(int alpha) {
        if (mBarPaint.getAlpha() != alpha) {
            mBarPaint.setAlpha(alpha);
            invalidateSelf();
        }
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        mBarPaint.setColorFilter(colorFilter);
        mThumbPaint.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.OPAQUE;
    }

    // Add another bar to waveform.
    public void put(int amplitude) {
        mBuffer[mIndex + mContains] = amplitude;
        if (mContains == mBuffer.length) {
            mIndex ++;
            mIndex %= mBuffer.length;
        } else {
            mContains ++;
        }
        recalcBars();
        invalidateSelf();
    }

    // Add entire waveform at once.
    public void put(byte[] amplitudes) {
        resampleBars(amplitudes, mBuffer);
        mIndex = 0;
        mContains = mBuffer.length;
        recalcBars();
        invalidateSelf();
    }

    public void seekTo(float pos) {
        if (pos < 0 || pos > 1) {
            throw new IllegalArgumentException("Seek position must be within [0..1] range");
        }
        mCurrent = pos;
        invalidateSelf();
    }

    // Calculate vertices of amplitude bars.
    private void recalcBars() {
        if (mBuffer.length == 0) {
            return;
        }

        int height = mSize.height();
        if (mEffectiveWidth <= 0 || height <= 0) {
            return;
        }

        // Values for scaling amplitude.
        float max = Integer.MIN_VALUE;
        for (float amp : mBuffer) {
            if (amp > max) {
                max = amp;
            }
        }

        mBars = new float[mContains * 4];
        for (int i = 0; i < mContains; i++) {
            float amp = (float) mBuffer[i];
            if (amp < 0) {
                amp = 0f;
            }

            // startX, endX
            float x = 1.0f + i * (sLineWidth + sSpacing) + sLineWidth * 0.5f;
            // Y length
            float y = amp / max * height * 0.9f;
            // startX
            mBars[i * 4] = x;
            // startY
            mBars[i * 4 + 1] = (height - y) * 0.5f;
            // stopX
            mBars[i * 4 + 2] = x;
            // stopY
            mBars[i * 4 + 3] = (height + y) * 0.5f;
        }
    }

    // Quick and dirty resampling of the original preview bars into a smaller (or equal) number of bars we can display here.
    private static void resampleBars(byte[] src, float[] dst) {
        // Resampling factor. Couple be lower or higher than 1.
        float factor = (float) src.length / dst.length;
        float max = -1;
        // src = 100, dst = 200, factor = 0.5
        // src = 200, dst = 100, factor = 2.0
        for (int i = 0; i < dst.length; i++) {
            int lo = (int) (i * factor); // low bound;
            int hi = (int) ((i + 1) * factor); // high bound;
            if (hi == lo) {
                dst[i] = src[lo];
            } else {
                float amp = 0f;
                for (int j = lo; j < hi; j++) {
                    amp += src[j];
                }
                dst[i] = Math.max(0, amp / (hi - lo));
            }
            max = Math.max(dst[i], max);
        }

        if (max > 0) {
            for (int i = 0; i < dst.length; i++) {
                dst[i] = dst[i] /max;
            }
        }
    }
}
