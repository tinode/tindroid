package co.tinode.tindroid.widgets;

import android.content.Context;
import android.os.Handler;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.RelativeLayout;

import androidx.core.content.ContextCompat;
import co.tinode.tindroid.R;

public class ConnectionAnimView extends RelativeLayout {
    private static final String TAG = "ConnectionAnimView";

    private final Handler handler = new Handler();
    private boolean isAnimating;

    public ConnectionAnimView(Context context, AttributeSet attrs) {
        super(context, attrs);
        isAnimating = false;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stop();
    }

    public void start() {
        if (isAnimating) {
            return;
        }
        isAnimating = true;
        setVisibility(View.VISIBLE);
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isAnimating) {
                    createAndAnimate();
                    handler.postDelayed(this, 500);
                }
            }
        }, 0);
    }

    public void stop() {
        isAnimating = false;
        setVisibility(View.INVISIBLE);
        handler.removeCallbacksAndMessages(null);
    }

    private void createAndAnimate() {
        final Context context = getContext();
        if (context == null) {
            return;
        }

        final View dot = new View(context);
        dot.setBackgroundColor(ContextCompat.getColor(context, R.color.colorOfflineDot));
        int size = (int) context.getResources().getDimension(R.dimen.connecting_dot); // 2dp
        dot.setLayoutParams(new RelativeLayout.LayoutParams(size * 3, size));
        addView(dot);

        // Load and start animation.
        Animation animation = AnimationUtils.loadAnimation(context, R.anim.connecting);
        animation.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
            }
            @Override
            public void onAnimationEnd(Animation animation) {
                dot.clearAnimation();
                // The delay is needed to prevent crash on simulator.
                postDelayed(() -> removeView(dot), 300);
            }
            @Override
            public void onAnimationRepeat(Animation animation) {
            }
        });

        dot.startAnimation(animation);
    }
}
