package co.tinode.tindroid.widgets;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.net.Uri;
import android.util.Log;

import com.squareup.picasso.Picasso;
import com.squareup.picasso.RequestCreator;
import com.squareup.picasso.Target;

import java.util.HashMap;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.core.content.res.ResourcesCompat;
import co.tinode.tindroid.Const;
import co.tinode.tindroid.UiUtils;

/**
 * LayerDrawable with some of the layers set by Picasso.
 */
public class UrlLayerDrawable extends LayerDrawable {
    private static final String TAG = "UrlLayerDrawable";
    private static final int INTRINSIC_SIZE = 128;

    HashMap<Integer,Target> mTargets = null;

    public UrlLayerDrawable(@NonNull Drawable[] layers) {
        super(layers);
    }

    @Override
    public int getIntrinsicWidth() {
        // This has to be set otherwise it does not show in toolbar
        return INTRINSIC_SIZE;
    }

    @Override
    public int getIntrinsicHeight() {
        return INTRINSIC_SIZE;
    }

    public void setUrlByLayerId(Resources res, int layerId, String url,
                                Drawable placeholder, @DrawableRes int error) {
        if (mTargets == null) {
            mTargets = new HashMap<>(getNumberOfLayers());
        }

        Target target = new Target() {
            final int mLayerId = layerId;
            final Resources mRes = res;
            @Override
            public void onBitmapLoaded(Bitmap bitmap, Picasso.LoadedFrom from) {
                setDrawableByLayerId(mLayerId, new RoundImageDrawable(mRes, bitmap));
            }

            @Override
            public void onBitmapFailed(Exception e, Drawable errorDrawable) {
                if (errorDrawable != null) {
                    setDrawableByLayerId(mLayerId, errorDrawable);
                    invalidateSelf();
                }
                Log.w(TAG, "Error loading avatar", e);
            }

            @Override
            public void onPrepareLoad(Drawable placeHolderDrawable) {
                if (placeHolderDrawable != null) {
                    setDrawableByLayerId(mLayerId, placeHolderDrawable);
                }
            }
        };
        RequestCreator c = Picasso.get()
                .load(Uri.decode(url))
                .resize(Const.MAX_AVATAR_SIZE, Const.MAX_AVATAR_SIZE)
                .centerCrop();
        if (error != 0) {
            c = c.error(error);
        }
        if (placeholder != null) {
            c = c.placeholder(placeholder);
        }
        c.into(target);
        mTargets.put(layerId, target);
    }
}
