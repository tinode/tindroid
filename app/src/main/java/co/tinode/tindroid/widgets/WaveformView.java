package co.tinode.tindroid.widgets;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import java.util.Arrays;

import co.tinode.tindroid.R;

/**
 * Widget to draw audio amplitude bars
 */
public class WaveformView extends View {
    private static final float LINE_WIDTH = 3f;
    private static final float SPACING = 1f;

    // Amplitude values passed by the caller resampled to fit the screen.
    private float[] mBuffer;
    // Count of amplitude values actually added to the buffer.
    private int mContains;
    // Entry point in mBuffer (mBuffer is a circular buffer).
    private int mIndex;
    // Position of the playback as a fraction of the total.
    private float mCurrent;
    // The number of amplitude bars which can fit onto canvas;
    private int mMaxBars;
    // Array of 4 values for each bar: startX, startY, stopX, stopY.
    private float[] mBars;
    // Canvas width which fits whole number of bars.
    private int mEffectiveWidth;

    private final Paint mBarPaint;
    private final Paint mThumbPaint;

    public WaveformView(Context context, AttributeSet attr) {
        super(context, attr);
        mContains = 0;
        mCurrent = 0;
        mBuffer = null;
        mIndex = 0;
        mBars = new float[0];
        mMaxBars = 0;
        mBarPaint = new Paint();
        mThumbPaint = new Paint();
        if (attr != null) {
            TypedArray a = context.obtainStyledAttributes(attr, R.styleable.WaveformView);
            try {
                int color = a.getColor(R.styleable.WaveformView_colorBar, 0);
                mBarPaint.setColor(color);
                color = a.getColor(R.styleable.WaveformView_colorThumb, 0);
                mThumbPaint.setColor(color);
            } finally {
                a.recycle();
            }
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        mMaxBars = (int) ((right - left -SPACING) / (LINE_WIDTH + SPACING));
        mEffectiveWidth = (int) (mMaxBars * (LINE_WIDTH + SPACING) + SPACING);
        mBuffer = Arrays.copyOf(mBuffer, mMaxBars);
    }

    @Override
    public void onDraw(Canvas canvas) {
        // Draw amplitude bars.
        canvas.drawLines(mBars, mBarPaint);

        // Draw thumb.
        if (mCurrent >= 0) {
            canvas.drawCircle(mCurrent * mEffectiveWidth + LINE_WIDTH * 2f, getHeight() * 0.5f,
                    LINE_WIDTH * 2, mThumbPaint);
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
        postInvalidate();
    }

    // Add entire waveform at once.
    public void put(int[] amplitudes) {
        resampleBars(amplitudes, mBuffer);
        mIndex = 0;
        mContains = mBuffer.length;
        recalcBars();
        postInvalidate();
    }

    public void seekTo(float pos) {
        if (pos < 0 || pos > 1) {
            throw new IllegalArgumentException("Seek position must be within [0..1] range");
        }
        mCurrent = pos;
        postInvalidate();
    }

    // Calculate vertices of amplitude bars.
    private void recalcBars() {
        if (mBuffer.length == 0) {
            return;
        }

        int width = mEffectiveWidth;
        int height = getHeight();
        if (width <= 0 || height <= 0) {
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
            float x = 1.0f + i * (LINE_WIDTH + SPACING) + LINE_WIDTH * 0.5f;
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
    private static void resampleBars(int[] original, float[] amps) {
        float factor = (float) original.length / amps.length;
        float max = -1;
        for (int i = 0; i < amps.length; i++) {
            int lo = (int) (i * factor); // low bound;
            int hi = (int) ((i + 1) * factor); // high bound;
            float amp = 0f;
            for (int j = lo; j <= hi; j++) {
                amp += original[j];
            }
            amps[i] = Math.max(0, amp / (hi - lo + 1));
            max = Math.max(amps[i], max);
        }

        if (max > 0) {
            for (int i = 0; i < amps.length; i++) {
                amps[i] = amps[i] /max;
            }
        }
    }
}
