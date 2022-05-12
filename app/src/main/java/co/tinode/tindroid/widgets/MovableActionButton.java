package co.tinode.tindroid.widgets;

import android.content.Context;
import android.graphics.PointF;
import android.graphics.Rect;
import android.util.ArrayMap;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.Map;

/**
 * FloatingActionButton which can be dragged around.
 */
public class MovableActionButton extends FloatingActionButton implements View.OnTouchListener {
    private final static int MIN_DRAG_DISTANCE = 8;

    private int mDragToIgnore;

    private ConstraintChecker mConstraintChecker = null;
    private ActionListener mActionListener = null;

    private ArrayMap<Integer, Rect> mActionZones;

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

    public void setOnActionListener(ActionListener listener) {
        mActionListener = listener;
    }

    public void addActionZone(int id, Rect zone) {
        if (mActionZones == null) {
            mActionZones = new ArrayMap<>();
        }
        mActionZones.put(id, new Rect(zone));
    }

    @Override
    public boolean onTouch(View view, MotionEvent motionEvent) {
        int action = motionEvent.getAction();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                mRawStartX = motionEvent.getRawX();
                mRawStartY = motionEvent.getRawY();
                // Conversion from screen to view coordinates.
                mDiffX = view.getX() - mRawStartX;
                mDiffY = view.getY() - mRawStartY;
                return true;

            case MotionEvent.ACTION_UP:
                float dX = motionEvent.getRawX() - mRawStartX;
                float dY = motionEvent.getRawY() - mRawStartY;

                boolean putBack = false;
                if (mActionListener != null) {
                    putBack = mActionListener.onUp(dX, dY);
                }

                // Make sure the drag was long enough.
                if (Math.abs(dX) < mDragToIgnore && Math.abs(dY) < mDragToIgnore || putBack) {
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
                    newPos = mConstraintChecker.check(newPos,
                            new PointF(mRawStartX + mDiffX, mRawStartY + mDiffY), viewRect, parentRect);
                }

                // Animate view to the new position.
                view.animate().x(newPos.x).y(newPos.y).setDuration(0).start();

                // Check if the center of the button is inside the action zone.
                if (mActionZones != null && mActionListener != null) {
                    float x = newPos.x + view.getWidth() * 0.5f;
                    float y = newPos.y + view.getHeight() * 0.5f;
                    for (Map.Entry<Integer, Rect> e : mActionZones.entrySet()) {
                        if (e.getValue().contains((int) x, (int) y)) {
                            if (mActionListener.onZoneReached(e.getKey())) {
                                view.animate().x(mRawStartX + mDiffX).y(mRawStartY + mDiffY).setDuration(0).start();
                                break;
                            }
                        }
                    }
                }

                return true;

            default:
                return super.onTouchEvent(motionEvent);
        }
    }

    public static class ActionListener {
        public boolean onUp(float dX, float dY) {
            return false;
        }

        public boolean onZoneReached(int id) {
            return false;
        }
    }

    public interface ConstraintChecker {
        PointF check(PointF newPos, PointF startPos, Rect view, Rect parent);
    }
}
