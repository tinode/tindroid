package co.tinode.tindroid;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
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
public class UIUtils {
    private static final String TAG = "UIUtils";
    // If Message activity is visible, this is the current topic in that activity.
    public static String sVisibleTopic = null;

    public static void setupToolbar(final AppCompatActivity activity, VCard pub, Topic.TopicType topicType) {
        final Toolbar toolbar = (Toolbar) activity.findViewById(R.id.toolbar);
        if (toolbar == null) {
            return;
        }

        if (pub != null) {
            toolbar.setTitle(" " + pub.fn);

            pub.constructBitmap();
            Bitmap bmp = pub.getBitmap();
            if (bmp != null) {
                toolbar.setLogo(new RoundedImage(bmp));
            } else {
                Drawable drw;
                int res = -1;
                if (topicType == Topic.TopicType.GRP) {
                    res = R.drawable.ic_group;
                } else if (topicType == Topic.TopicType.P2P || topicType == Topic.TopicType.ME) {
                    res = R.drawable.ic_person;
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    drw = activity.getResources().getDrawable(res, activity.getTheme());
                } else {
                    drw = activity.getResources().getDrawable(res);
                }
                if (drw != null) {
                    toolbar.setLogo(drw);
                }
            }
        } else {
            toolbar.setTitle(R.string.app_name);
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
