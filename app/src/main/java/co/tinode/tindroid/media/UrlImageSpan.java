package co.tinode.tindroid.media;

import android.graphics.Bitmap;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.net.Uri;
import android.text.style.DynamicDrawableSpan;
import android.util.Log;
import android.view.View;

import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;

import java.lang.ref.WeakReference;
import java.net.URL;

import androidx.annotation.NonNull;
import androidx.core.content.res.ResourcesCompat;
import co.tinode.tindroid.R;

/* Spannable which updates associated image as it's loaded from the given URL */
public class UrlImageSpan extends DynamicDrawableSpan implements Target {
    private static final String TAG = "DynamicDrawableSpan";

    private final WeakReference<View> mParentRef;
    private Drawable mDrawable;
    private final Drawable mOnError;
    private final int mWidth;
    private final int mHeight;

    public UrlImageSpan(View parent, int width, int height, Drawable placeholder, Drawable onError) {
        mParentRef = new WeakReference<>(parent);
        mWidth = width;
        mHeight = height;
        mOnError = onError;
        mDrawable = getPlaceholder(parent, placeholder);
    }

    public void load(URL from) {
        Log.i(TAG, "Fetching " + from);
        Picasso.get().load(Uri.parse(from.toString())).resize(mWidth, mHeight).into(this);
    }

    @Override
    public Drawable getDrawable() {
        return mDrawable;
    }

    @Override
    public void onBitmapLoaded(Bitmap bitmap, Picasso.LoadedFrom from) {
        Log.i(TAG, "Received bitmap " + from.name());
        View parent = mParentRef.get();
        if (parent != null) {
            mDrawable = new BitmapDrawable(parent.getResources(), bitmap);
            parent.invalidate();
        }
    }

    @Override
    public void onBitmapFailed(Exception e, Drawable errorDrawable) {
        Log.i(TAG, "Bitmap failed", e);
        View parent = mParentRef.get();
        if (parent != null) {
            mDrawable = getPlaceholder(parent, mOnError);
            parent.invalidate();
        }
    }

    @Override
    public void onPrepareLoad(Drawable placeHolderDrawable) {
    }

    @Override
    public int getSize(@NonNull Paint paint, CharSequence text,
                       int start, int end, Paint.FontMetricsInt fm) {
        if (fm != null) {
            fm.ascent = -mHeight;
            fm.descent = 0;
            fm.top = fm.ascent;
            fm.bottom = 0;
        }
        return mWidth;
    }

    // Creates LayerDrawable of the right size with gray background and 'fg' in the middle.
    private Drawable getPlaceholder(View parent, Drawable fg) {
        Drawable bkg = ResourcesCompat.getDrawable(parent.getResources(),
                R.drawable.placeholder_image_bkg, null);
        int fgWidth = fg.getIntrinsicWidth();
        int fgHeight = fg.getIntrinsicHeight();
        LayerDrawable result = new LayerDrawable(new Drawable[] {bkg, fg});
        result.setBounds(0, 0, mWidth, mHeight);
        //bkg.setBounds(0, 0, mWidth, mHeight);
        //
        // Move foreground to the center of the drawable.
        int dx = Math.max((mWidth - fgWidth)/2, 0);
        int dy = Math.max((mHeight - fgHeight)/2, 0);
        //result.setLayerInset(1, 50, 50, 50, 50);
        fg.setBounds(dx, dy, dx+fgWidth, dy+fgHeight);
        Log.i(TAG, "bg=" + mWidth + " x " + mHeight +
                "; fg="+fgWidth + " x " + fgHeight + "; dx=" + dx + "; dy=" + dy);
        return result;
    }
}
