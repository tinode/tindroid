package co.tinode.tindroid;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.support.v7.widget.Toolbar;
import android.util.Log;

import co.tinode.tinodesdk.Tinode;
import co.tinode.tinodesdk.Topic;

/**
 * Shared resources.
 */
public class InmemoryCache {
    private static final String TAG = "InmemoryCache";
    private static final String API_KEY = "AQEAAAABAAD_rAp4DJh05a1HAwFT3A6K";

    private static Tinode sTinode;

    //public static String sHost = "10.0.2.2:6060"; // local
    //public static String sHost = "api.tinode.co"; // remote

    public static Tinode getTinode() {
        if (sTinode == null) {
            sTinode = new Tinode("Tindroid", API_KEY);
            // Default types for parsing Public, Private, Content fields of messages
            sTinode.setDefaultTypes(VCard.class, String.class, String.class);
        }

        return sTinode;
    }

    public static void setupToolbar(Context context, Toolbar toolbar, VCard pub, Topic.TopicType topicType) {
        if (pub != null) {
            toolbar.setTitle(" " + pub.fn);

            pub.constructBitmap();
            Bitmap bmp = pub.getBitmap();
            if (bmp != null) {
                toolbar.setLogo(new RoundedImage(bmp));
            } else {
                Drawable drw = null;
                int res = -1;
                if (topicType == Topic.TopicType.GRP) {
                    res = R.drawable.ic_group;
                } else if (topicType == Topic.TopicType.P2P || topicType == Topic.TopicType.ME) {
                    res = R.drawable.ic_person;
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    drw = context.getResources().getDrawable(res, context.getTheme());
                } else {
                    drw = context.getResources().getDrawable(res);
                }
                if (drw != null) {
                    toolbar.setLogo(drw);
                }
            }
            if (bmp != null) {
                toolbar.setLogo(new RoundedImage(bmp));
            }
        }
    }
}
