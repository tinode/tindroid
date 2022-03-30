package co.tinode.tindroid.widgets;

import android.content.Context;
import android.graphics.PointF;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

/**
 * FloatingActionButton which can be dragged around.
 */
public class MovableActionButton extends FloatingActionButton implements View.OnTouchListener {
    private final static int MIN_DRAG_DISTANCE = 8;
    private final static int ACTION_DRAG_DISTANCE = 32;

    private int mDragToIgnore;

    private ConstraintChecker mConstraintChecker = null;

    // Drag started.
    float mRawStartX;
    float mRawStartY;

    // Distance between the button and the location of the initial DOWN click.
    float mDiffX, mDiffY;

    public MovableActionButton(Context context) {
        super(context);
        initialize();
    }

    public MovableActionButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        initialize();
    }

    public MovableActionButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initialize();
    }

    private void initialize() {
        float density = getResources().getDisplayMetrics().density;
        mDragToIgnore = (int) (MIN_DRAG_DISTANCE * density);

        setOnTouchListener(this);
    }

    public void setConstraintChecker(ConstraintChecker checker) {
        mConstraintChecker = checker;
    }

    @Override
    public boolean onTouch(View view, MotionEvent motionEvent) {
        int action = motionEvent.getAction();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                Log.i("MAB", "DOWN");
                mRawStartX = motionEvent.getRawX();
                mRawStartY = motionEvent.getRawY();
                mDiffX = view.getX() - mRawStartX;
                mDiffY = view.getY() - mRawStartY;
                return true;

            case MotionEvent.ACTION_UP:
                Log.i("MAB", "UP");
                // Make sure the drag was long enough.
                if (Math.abs(motionEvent.getRawX() - mRawStartX) < mDragToIgnore &&
                        Math.abs(motionEvent.getRawY() - mRawStartY) < mDragToIgnore) {
                    // Not a drag: too short. Move back and register click.
                    view.animate().x(mRawStartX + mDiffX).y(mRawStartY + mDiffY).setDuration(0).start();
                    return performClick();
                }
                // A real drag.
                return true;

            case MotionEvent.ACTION_MOVE:
                PointF newPos = new PointF(motionEvent.getRawX() + mDiffX, motionEvent.getRawY() + mDiffY);

                // Ensure constraints.
                if (mConstraintChecker != null) {
                    ViewGroup.MarginLayoutParams layoutParams = (ViewGroup.MarginLayoutParams) view.getLayoutParams();
                    View viewParent = (View) view.getParent();

                    Rect viewRect = new Rect(view.getLeft(), view.getTop(), view.getRight(), view.getBottom());
                    Rect parentRect = new Rect(layoutParams.leftMargin,
                            layoutParams.topMargin,
                            viewParent.getWidth() - layoutParams.rightMargin,
                            viewParent.getHeight() - layoutParams.bottomMargin);
                    newPos = mConstraintChecker.check(newPos, viewRect, parentRect);
                }

                // Animate view to the new position.
                view.animate().x(newPos.x).y(newPos.y).setDuration(0).start();
                return true;

            default:
                return super.onTouchEvent(motionEvent);
        }
    }

    public interface ConstraintChecker {
        PointF check(PointF newPos, Rect view, Rect parent);
    }
}
