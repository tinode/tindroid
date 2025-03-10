package co.tinode.tindroid.widgets;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.net.Uri;
import android.util.Log;

import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.HashMap;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;

import co.tinode.tindroid.Const;
import co.tinode.tindroid.UiUtils;

import coil.Coil;
import coil.request.ImageRequest;
import coil.size.Scale;
import coil.target.Target;

/**
 * LayerDrawable with some of the layers set by Coil.
 */
public class UrlLayerDrawable extends LayerDrawable {
    private static final String TAG = "UrlLayerDrawable";
    private static final int INTRINSIC_SIZE = 128;

    private HashMap<Integer,Target> mTargets = null;

    private final WeakReference<Context> mContext;

    public UrlLayerDrawable(@NonNull Context context, @NonNull Drawable[] layers) {
        super(layers);
        mContext = new WeakReference<>(context);
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
        Context context = mContext.get();
        if (context == null) {
            return;
        }

        if (mTargets == null) {
            mTargets = new HashMap<>(getNumberOfLayers());
        }

        Target target = new Target() {
            final int mLayerId = layerId;
            final Resources mRes = res;
            @Override
            public void onSuccess(@NonNull Drawable result) {
                setDrawableByLayerId(mLayerId,
                        new RoundImageDrawable(mRes, UiUtils.bitmapFromDrawable(result)));
            }

            @Override
            public void onError(@Nullable Drawable errorDrawable) {
                if (errorDrawable != null) {
                    setDrawableByLayerId(mLayerId, errorDrawable);
                    invalidateSelf();
                }
                Log.w(TAG, "Error loading avatar");
            }

            @Override
            public void onStart(@Nullable Drawable placeholder) {
                if (placeholder != null) {
                    setDrawableByLayerId(mLayerId, placeholder);
                }
            }
        };

        ImageRequest.Builder c = new ImageRequest.Builder(mContext.get())
                .data(Uri.decode(url))
                .size(Const.MAX_AVATAR_SIZE, Const.MAX_AVATAR_SIZE)
                .scale(Scale.FILL);
        if (error != 0) {
            c.error(error);
        }
        if (placeholder != null) {
            c.placeholder(placeholder);
        }
        c.target(target);

        Coil.imageLoader(context).enqueue(c.build());
        mTargets.put(layerId, target);
    }
}
