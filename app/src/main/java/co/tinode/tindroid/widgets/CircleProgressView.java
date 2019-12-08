package co.tinode.tindroid.widgets;

/*
 * Copyright 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.OvalShape;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.Transformation;

import androidx.appcompat.widget.AppCompatImageView;
import androidx.core.view.ViewCompat;
import androidx.swiperefreshlayout.widget.CircularProgressDrawable;

/**
 * This class is used to display circular progress indicator (infinite spinner).
 * It matches the style of the spinner in SwipeRefreshLayout + optionally can be shown only after a
 * 500 ms delay like ContentLoadingProgressBar.
 * <p>
 * Adopted from android/9.0.0/androidx/swiperefreshlayout/widget/circleimageview.java
 */
public class CircleProgressView extends AppCompatImageView {
    private static final String TAG = "CircleProgressView";

    // This is the same functionality as ContentLoadingProgressBar.
    // If stop is called earlier than this, the spinner is not shown at all.
    private static final int MIN_SHOW_TIME = 300; // ms
    private static final int MIN_DELAY = 500; // ms

    private long mStartTime = -1;
    private boolean mPostedHide = false;
    private boolean mPostedShow = false;
    private boolean mDismissed = false;

    private static final int CIRCLE_BG_LIGHT = 0xFFFAFAFA;

    private static final int KEY_SHADOW_COLOR = 0x1E000000;
    private static final int FILL_SHADOW_COLOR = 0x3D000000;
    // PX
    private static final float X_OFFSET = 0f;
    private static final float Y_OFFSET = 1.75f;
    private static final float SHADOW_RADIUS = 3.5f;
    private static final int SHADOW_ELEVATION = 4;

    private int mMediumAnimationDuration;
    private static final int SCALE_DOWN_DURATION = 150;

    private CircularProgressDrawable mProgress;
    private Animation.AnimationListener mListener;
    int mShadowRadius;

    private Animation.AnimationListener mProgressStartListener = new AnimationEndListener() {
        @Override
        public void onAnimationEnd(Animation animation) {
            // mProgressView is already visible, start the spinner.
            mProgress.start();
        }
    };

    private Animation.AnimationListener mProgressStopListener = new AnimationEndListener() {
        @Override
        public void onAnimationEnd(Animation animation) {
            clearAnimation();
            mProgress.stop();
            setVisibility(View.GONE);
            setAnimationProgress(0);
        }
    };

    private final Runnable mDelayedHide = new Runnable() {
        @Override
        public void run() {
            mPostedHide = false;
            mStartTime = -1;
            stop();
        }
    };
    private final Runnable mDelayedShow = new Runnable() {
        @Override
        public void run() {
            mPostedShow = false;
            if (!mDismissed) {
                mStartTime = System.currentTimeMillis();
                start();
            }
        }
    };

    public CircleProgressView(Context context) {
        this(context, CIRCLE_BG_LIGHT);
    }

    public CircleProgressView(Context context, AttributeSet attrSet) {
        super(context, attrSet);
        init(context, CIRCLE_BG_LIGHT);
    }

    public CircleProgressView(Context context, int color) {
        super(context);
        init(context, color);
    }

    private void init(Context context, int color) {
        final float density = getContext().getResources().getDisplayMetrics().density;
        final int shadowYOffset = (int) (density * Y_OFFSET);
        final int shadowXOffset = (int) (density * X_OFFSET);

        mShadowRadius = (int) (density * SHADOW_RADIUS);

        ShapeDrawable circle;
        if (elevationSupported()) {
            circle = new ShapeDrawable(new OvalShape());
            ViewCompat.setElevation(this, SHADOW_ELEVATION * density);
        } else {
            circle = new ShapeDrawable(new OvalShadow(this, mShadowRadius));
            setLayerType(View.LAYER_TYPE_SOFTWARE, circle.getPaint());
            circle.getPaint().setShadowLayer(mShadowRadius, shadowXOffset, shadowYOffset,
                    KEY_SHADOW_COLOR);
            final int padding = mShadowRadius;
            // set padding so the inner image sits correctly within the shadow.
            setPadding(padding, padding, padding, padding);
        }
        circle.getPaint().setColor(color);
        ViewCompat.setBackground(this, circle);

        mMediumAnimationDuration = getResources().getInteger(android.R.integer.config_mediumAnimTime);

        mProgress = new CircularProgressDrawable(context);
        mProgress.setStyle(CircularProgressDrawable.DEFAULT);
        setImageDrawable(mProgress);
    }

    /**
     * Hide the progress view if it's visible. The progress view will not be
     * hidden until it has been shown for at least a minimum show time. If the
     * progress view was not yet visible, cancels showing the progress view.
     */
    public synchronized void hide() {
        mDismissed = true;
        removeCallbacks(mDelayedShow);
        mPostedShow = false;
        long diff = System.currentTimeMillis() - mStartTime;
        if (diff >= MIN_SHOW_TIME || mStartTime == -1) {
            // The progress spinner has been shown long enough OR was not shown yet.
            // If it wasn't shown yet, it will just never be shown.
            if (getVisibility() == View.VISIBLE) {
                stop();
            }
        } else if (!mPostedHide) {
            // The progress spinner is shown, but not long enough,
            // so post a delayed message to hide it when its been shown long enough.
            mPostedHide = true;
            postDelayed(mDelayedHide, MIN_SHOW_TIME - diff);
        }
    }

    /**
     * Show the progress view after waiting for a minimum delay. If
     * during that time, hide() is called, the view is never made visible.
     */
    public synchronized void show() {
        // Reset the start time.
        mStartTime = -1;
        mDismissed = false;
        removeCallbacks(mDelayedHide);
        mPostedHide = false;
        if (!mPostedShow) {
            postDelayed(mDelayedShow, MIN_DELAY);
            mPostedShow = true;
        }
    }

    /**
     * Start progress animation immediately: scale the circle from 0 to 1, then start the spinner.
     */
    public void start() {
        setVisibility(View.VISIBLE);
        Animation scale = new Animation() {
            @Override
            public void applyTransformation(float interpolatedTime, Transformation t) {
                setAnimationProgress(interpolatedTime);
            }
        };
        scale.setDuration(mMediumAnimationDuration);
        setAnimationListener(mProgressStartListener);
        clearAnimation();
        startAnimation(scale);
    }

    /**
     * Stop progress animation immediately: scale the circle from 1 to 0.
     */
    public void stop() {
        Animation down = new Animation() {
            @Override
            public void applyTransformation(float interpolatedTime, Transformation t) {
                setAnimationProgress(1 - interpolatedTime);
            }
        };
        down.setDuration(SCALE_DOWN_DURATION);
        setAnimationListener(mProgressStopListener);
        clearAnimation();
        startAnimation(down);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (!elevationSupported()) {
            setMeasuredDimension(getMeasuredWidth() + mShadowRadius * 2, getMeasuredHeight()
                    + mShadowRadius * 2);
        }
    }

    private boolean elevationSupported() {
        return android.os.Build.VERSION.SDK_INT >= 21;
    }

    public void setAnimationListener(Animation.AnimationListener listener) {
        mListener = listener;
    }

    @Override
    public void onAnimationStart() {
        super.onAnimationStart();
        if (mListener != null) {
            mListener.onAnimationStart(getAnimation());
        }
    }

    @Override
    public void onAnimationEnd() {
        super.onAnimationEnd();
        if (mListener != null) {
            mListener.onAnimationEnd(getAnimation());
        }
    }

    @Override
    public void setBackgroundColor(int color) {
        if (getBackground() instanceof ShapeDrawable) {
            ((ShapeDrawable) getBackground()).getPaint().setColor(color);
        }
    }

    private void setAnimationProgress(float progress) {
        setScaleX(progress);
        setScaleY(progress);
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        removeCallbacks(mDelayedHide);
        removeCallbacks(mDelayedShow);
    }

    private static class OvalShadow extends OvalShape {
        private Paint mShadowPaint;
        private int mShadowRadius;
        private CircleProgressView mCircleProgressView;

        OvalShadow(CircleProgressView circleProgressView, int shadowRadius) {
            super();
            mCircleProgressView = circleProgressView;
            mShadowPaint = new Paint();
            mShadowRadius = shadowRadius;
            updateRadialGradient((int) rect().width());
        }

        @Override
        protected void onResize(float width, float height) {
            super.onResize(width, height);
            updateRadialGradient((int) width);
        }

        @Override
        public void draw(Canvas canvas, Paint paint) {
            final int width = mCircleProgressView.getWidth() / 2;
            final int height = mCircleProgressView.getHeight() / 2;
            canvas.drawCircle(width, height, width, mShadowPaint);
            canvas.drawCircle(width, height, width - mShadowRadius, paint);
        }

        private void updateRadialGradient(int diameter) {
            mShadowPaint.setShader(new RadialGradient(
                    diameter * .5f,
                    diameter * .5f,
                    mShadowRadius,
                    new int[]{FILL_SHADOW_COLOR, Color.TRANSPARENT},
                    null,
                    Shader.TileMode.CLAMP));
        }
    }

    // Boilerplate hidden.
    private static abstract class AnimationEndListener implements Animation.AnimationListener {
        @Override
        public void onAnimationStart(Animation animation) {
        }

        @Override
        public void onAnimationRepeat(Animation animation) {
        }
    }
}
