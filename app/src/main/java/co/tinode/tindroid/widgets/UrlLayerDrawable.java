package co.tinode.tindroid.widgets;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.net.Uri;

import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;

import java.util.HashMap;

import androidx.annotation.NonNull;

/**
 * LayerDrawable with some of the layer set by Picasso.
 */
public class UrlLayerDrawable extends LayerDrawable {
    HashMap<Integer,Target> mTargets = null;
    public UrlLayerDrawable(@NonNull Drawable[] layers) {
        super(layers);
    }

    public void setUrlByLayerId(Resources res, int id, String url) {
        if (mTargets == null) {
            mTargets = new HashMap<>(getNumberOfLayers());
        }
        Target target = new Target() {
            final int mId = id;
            final Resources mRes = res;
            @Override
            public void onBitmapLoaded(Bitmap bitmap, Picasso.LoadedFrom from) {
                setDrawableByLayerId(mId, new RoundImageDrawable(mRes, bitmap));
            }

            @Override
            public void onBitmapFailed(Exception e, Drawable errorDrawable) {
            }

            @Override
            public void onPrepareLoad(Drawable placeHolderDrawable) {
            }
        };
        Picasso.get().load(Uri.decode(url)).fit().into(target);
        mTargets.put(id, target);
    }
}
