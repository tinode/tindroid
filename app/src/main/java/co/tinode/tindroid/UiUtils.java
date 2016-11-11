package co.tinode.tindroid;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Build;
import android.support.design.widget.AppBarLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import java.util.Map;

import co.tinode.tinodesdk.Tinode;
import co.tinode.tinodesdk.Topic;

/**
 * Static utilities for UI support.
 */
public class UiUtils {
    private static final String TAG = "UiUtils";
    // If Message activity is visible, this is the current topic in that activity.
    public static String sVisibleTopic = null;

    public static int COLOR_ONLINE = Color.argb(255, 0x40, 0xC0, 0x40);
    public static int COLOR_OFFLINE = Color.argb(255, 0xC0, 0xC0, 0xC0);

    public static void setupToolbar(final AppCompatActivity activity, VCard pub,
                                    Topic.TopicType topicType, boolean online) {
        final Toolbar toolbar = (Toolbar) activity.findViewById(R.id.toolbar);
        if (toolbar == null) {
            return;
        }

        if (pub != null) {
            toolbar.setTitle(" " + pub.fn);

            pub.constructBitmap();
            Bitmap bmp = pub.getBitmap();
            if (bmp != null) {
                toolbar.setLogo(
                        new LayerDrawable(
                                new Drawable[] {
                                        new RoundedImage(bmp),
                                        new OnlineDrawable(online)}));
            } else {
                Drawable drw;
                int res = -1;
                if (topicType == Topic.TopicType.GRP) {
                    res = R.drawable.ic_group_circle;
                } else if (topicType == Topic.TopicType.P2P || topicType == Topic.TopicType.ME) {
                    res = R.drawable.ic_person_circle;
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    drw = activity.getResources().getDrawable(res, activity.getTheme());
                } else {
                    drw = activity.getResources().getDrawable(res);
                }
                if (drw != null) {
                    toolbar.setLogo(new LayerDrawable(new Drawable[] {drw, new OnlineDrawable(online)}));
                }
            }
        } else {
            toolbar.setLogo(null);
            toolbar.setTitle(R.string.app_name);
        }
    }

    public static void setOnlineStatus(final AppCompatActivity activity, boolean online) {
        final Toolbar toolbar = (Toolbar) activity.findViewById(R.id.toolbar);
        if (toolbar == null) {
            return;
        }

        LayerDrawable logo = (LayerDrawable) toolbar.getLogo();
        if (logo != null) {
            OnlineDrawable indicator = (OnlineDrawable) logo.getDrawable(1);
            if (indicator != null) {
                indicator.setOnline(online);
                toolbar.setLogo(logo);
            }
        }
    }

    public static String getVisibleTopic() {
        return sVisibleTopic;
    }

    public static void setVisibleTopic(String topic) {
        sVisibleTopic = topic;
    }

    public static class EventListener extends Tinode.EventListener {
        private AppCompatActivity mActivity = null;
        private Boolean mOnline = null;

        private EventListener() {}

        public EventListener(AppCompatActivity owner) {
            super();
            mActivity = owner;
        }

        @Override
        public void onConnect(int code, String reason, Map<String, Object> params) {
            // Show that we are connected
            setOnlineStatus(true);
        }

        @Override
        public void onDisconnect(boolean byServer, int code, String reason) {
            // Show that we are disconnected
            if (code <= 0) {
                Log.d(TAG, "Network error");
            } else {
                Log.d(TAG, "Tinode error: " + code);
            }
            setOnlineStatus(false);
        }

        private void setOnlineStatus(final boolean online) {
            if (mActivity == null) {
                return;
            }

            mActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    final Toolbar toolbar = (Toolbar) mActivity.findViewById(R.id.toolbar);
                    if (toolbar != null && (mOnline == null || online != mOnline)) {
                        mOnline = online;
                        Menu menu = toolbar.getMenu();
                        if (menu != null) {
                            menu.setGroupVisible(R.id.offline, !online);
                        }
                        View line = mActivity.findViewById(R.id.offline_indicator);
                        if (line != null) {
                            line.setVisibility(online ? View.GONE : View.VISIBLE);
                        }
                    } else {
                        mOnline = null;
                    }
                }
            });
        }
    }
}
