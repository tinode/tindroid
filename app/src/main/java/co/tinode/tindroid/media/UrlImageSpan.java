package co.tinode.tindroid.media;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.text.style.DynamicDrawableSpan;
import android.util.Log;
import android.view.View;

import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;

import java.lang.ref.WeakReference;
import java.net.URL;

import androidx.annotation.NonNull;

/* Spannable which updates associated image as it's loaded from the given URL */
public class UrlImageSpan extends DynamicDrawableSpan implements Target {
    private static final String TAG = "DynamicDrawableSpan";

    private final WeakReference<View> mParentRef;
    private Drawable mDrawable;
    private final Drawable mOnError;
    private final int mWidth;
    private final int mHeight;

    public UrlImageSpan(View parent, int width, int height, Drawable placeholder, Drawable onError) {
        Log.i(TAG, "WxH= " + width + " x " + height);
        mParentRef = new WeakReference<>(parent);
        mWidth = width;
        mHeight = height;
        mOnError = onError;
        mDrawable = placeholder;
    }

    public void load(URL from) {
        Log.i(TAG, "Fetching " + from);
        Picasso.get().load(Uri.parse(from.toString())).into(this);
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
            mDrawable = mOnError;
            parent.invalidate();
        }
    }

    @Override
    public void onPrepareLoad(Drawable placeHolderDrawable) {
    }

    @Override
    public int getSize(@NonNull Paint paint, CharSequence text,
                       int start, int end, Paint.FontMetricsInt fm) {
        Log.i(TAG, "Get size " + mWidth + " x " + mHeight);
        if (fm != null) {
            fm.ascent = -mHeight;
            fm.descent = 0;
            fm.top = fm.ascent;
            fm.bottom = 0;
        }
        return mWidth;
    }
}
