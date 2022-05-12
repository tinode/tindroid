package co.tinode.tindroid.widgets;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatTextView;

/**
 * Text view which permits animation of embedded ImageSpans.
 */
public class DynamicTextView extends AppCompatTextView {

    public DynamicTextView(@NonNull Context context) {
        this(context, null);
    }

    public DynamicTextView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public DynamicTextView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public void invalidateDrawable(@NonNull Drawable who) {
        postInvalidate();
    }

    @Override
    public void scheduleDrawable(@NonNull Drawable who, @NonNull Runnable what, long when) {
        postDelayed(what, when - SystemClock.uptimeMillis());
    }

    @Override
    public void unscheduleDrawable(@NonNull Drawable who, @NonNull Runnable what) {
        removeCallbacks(what);
    }
}
