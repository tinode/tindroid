package co.tinode.tinui.widgets;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import co.tinode.tinui.R;

// This is the top-level layout of a message. It's used to intercept click coordinates
// to pass them to the ripple layer. These coordinates are different from coordinates in
// the TextView with message content.
public class RippleFrameLayout extends FrameLayout {
    private View mOverlay;

    public RippleFrameLayout(@NonNull Context context) {
        this(context, null);
    }

    public RippleFrameLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public View getOverlayView() {
        return mOverlay;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (mOverlay == null) {
            mOverlay = new View(getContext());
            mOverlay.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
            TypedValue outValue = new TypedValue();
            getContext().getTheme().resolveAttribute(R.attr.selectableItemBackground, outValue, true);
            mOverlay.setBackground(ContextCompat.getDrawable(getContext(), outValue.resourceId));
            mOverlay.setClickable(false);
            mOverlay.setFocusable(false);
            addView(mOverlay);
        }

        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            Drawable background = mOverlay.getBackground();
            background.setHotspot(ev.getX(), ev.getY());
        }

        return false;
    }
}
