package co.tinode.tindroid.widgets;

import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.FloatRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import co.tinode.tindroid.R;

/**
 * Drawable which visualizes sound amplitudes as a waveform.
 */
public class WaveDrawable extends Drawable implements Runnable {
    // Bars and spacing sizes in DP.
    private static final float LINE_WIDTH = 3f;
    private static final float SPACING = 1f;

    // Time between redraws in milliseconds.
    private static final int FRAME_DURATION = 500;

    // Display density.
    private static float sDensity = -1f;

    // Bars and spacing sizes in pixels.
    private static float sLineWidth;
    private static float sSpacing;
    private static float sThumbRadius;

    // Duration of the audio in milliseconds.
    private int mDuration;

    // Current thumb position as a fraction of the total 0..1
    private float mSeekPosition = -1f;

    // Amplitude values received from the caller and resampled to fit the screen.
    private float[] mBuffer;
    // Count of amplitude values actually added to the buffer.
    private int mContains;
    // Entry point in mBuffer (mBuffer is a circular buffer).
    private int mIndex;
    // Array of 4 values for each amplitude bar: startX, startY, stopX, stopY.
    private float[] mBars = null;
    // Canvas width which fits whole number of bars.
    private int mEffectiveWidth;
    // If the Drawable is animated.
    private boolean mRunning  = false;

    // Paints for individual components of the drawable.
    private final Paint mBarPaint;
    private final Paint mPastBarPaint;
    private final Paint mThumbPaint;

    private Rect mSize = new Rect();

    public WaveDrawable(Resources res) {
        super();

        if (sDensity <= 0) {
            sDensity = res.getDisplayMetrics().density;
            sLineWidth = LINE_WIDTH * sDensity;
            sSpacing = SPACING * sDensity;
            sThumbRadius = sLineWidth * 1.5f;
        }

        // Waveform in the future.
        mBarPaint = new Paint();
        mBarPaint.setStyle(Paint.Style.STROKE);
        mBarPaint.setStrokeWidth(sLineWidth);
        mBarPaint.setStrokeCap(Paint.Cap.ROUND);
        mBarPaint.setAntiAlias(true);
        mBarPaint.setColor(res.getColor(R.color.waveform));

        // Waveform in the past.
        mPastBarPaint = new Paint();
        mPastBarPaint.setStyle(Paint.Style.STROKE);
        mPastBarPaint.setStrokeWidth(sLineWidth);
        mPastBarPaint.setStrokeCap(Paint.Cap.ROUND);
        mPastBarPaint.setAntiAlias(true);
        mPastBarPaint.setColor(res.getColor(R.color.waveformPast));

        // Seek thumb.
        mThumbPaint = new Paint();
        mThumbPaint.setAntiAlias(true);
        mThumbPaint.setColor(res.getColor(R.color.colorAccent));
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        mSize = new Rect(bounds);

        int maxBars = (int) ((mSize.width() - sSpacing) / (sLineWidth + sSpacing));
        mEffectiveWidth = (int) (maxBars * (sLineWidth + sSpacing) + sSpacing);
        mBuffer = new float[maxBars];

        invalidateSelf();
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
        if (mBars == null) {
            return;
        }

        if (mSeekPosition >= 0) {
            // Draw past - future bars and thumb on top of them.
            float cx = seekPositionToX();

            int dividedAt = (int) (mBars.length * 0.25f * mSeekPosition) * 4;

            // Already played amplitude bars.
            canvas.drawLines(mBars, 0, dividedAt, mPastBarPaint);

            // Not yet played amplitude bars.
            canvas.drawLines(mBars, dividedAt, mBars.length - dividedAt, mBarPaint);

            // Draw thumb.
            canvas.drawCircle(cx, mSize.height() * 0.5f, sThumbRadius, mThumbPaint);
        } else {
            // Just plain amplitude bars in one color.
            canvas.drawLines(mBars, mBarPaint);
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
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    @Override
    public void run() {
        Callback cb = getCallback();
        if (cb == null) {
            Log.i("WD", "run: No callback");
        } else {
            Log.i("WD", "run: Callback class = " + cb.getClass().getSimpleName());
        }

        float pos = mSeekPosition + (float) FRAME_DURATION / mDuration;
        seekTo(pos);
        if (pos < 1) {
            nextFrame();
        }
    }

    public void start() {
        Callback cb = getCallback();
        if (cb == null) {
            Log.i("WD", "start: No callback");
        } else {
            Log.i("WD", "start: Callback class = " + cb.getClass().getSimpleName());
        }
        if (!mRunning) {
            Log.i("WD", "start");
            mRunning = true;
            nextFrame();
        }
    }

    public void stop() {
        Log.i("WD", "STOP");
        mRunning = false;
        unscheduleSelf(this);
    }

    private void nextFrame() {
        unscheduleSelf(this);
        scheduleSelf(this, SystemClock.uptimeMillis() + FRAME_DURATION);
    }

    public void seekTo(@FloatRange(from = 0f, to = 1f) float fraction) {
        if (mSeekPosition != fraction) {
            Log.i("WD", "Seek to " + fraction);
            mSeekPosition = Math.max(Math.min(fraction, 1f), 0f);
            invalidateSelf();
        }
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

    // Get thumb position for level.
    private float seekPositionToX() {
        float base = (int) (mBars.length / 4f * mSeekPosition);
        return mBars[(int) base * 4] + (base - (int) base) * (sLineWidth + sSpacing);
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
