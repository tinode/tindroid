package co.tinode.tindroid;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.preference.PreferenceManager;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import com.neovisionaries.ws.client.WebSocketException;

import java.io.IOException;
import java.text.DateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Map;

import co.tinode.tindroid.account.Utils;
import co.tinode.tindroid.db.BaseDb;
import co.tinode.tinodesdk.Tinode;
import co.tinode.tinodesdk.Topic;

/**
 * Static utilities for UI support.
 */
public class UiUtils {
    private static final String TAG = "UiUtils";
    // If StoredMessage activity is visible, this is the current topic in that activity.
    public static String sVisibleTopic = null;

    public static int COLOR_ONLINE = Color.argb(255, 0x40, 0xC0, 0x40);
    public static int COLOR_OFFLINE = Color.argb(255, 0xC0, 0xC0, 0xC0);

    public static final int SELECT_PICTURE = 1;

    private static final int BITMAP_SIZE = 128;

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
                                new Drawable[] {new RoundedImage(bmp), new OnlineDrawable(online)}));
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
                    LayerDrawable ld = new LayerDrawable(
                            new Drawable[] {drw, new OnlineDrawable(online)});
                    invertDrawable(drw);
                    toolbar.setLogo(ld);
                }
            }
        } else {
            toolbar.setLogo(null);
            toolbar.setTitle(R.string.app_name);
        }
    }

    private static void invertDrawable(Drawable drw) {
        final float[] NEGATIVE = {
                -1.0f, 0, 0, 0, 255, // red
                0, -1.0f, 0, 0, 255, // green
                0, 0, -1.0f, 0, 255, // blue
                0, 0, 0, 1.0f, 0  // alpha
        };

        drw.setColorFilter(new ColorMatrixColorFilter(NEGATIVE));
    }
    /*
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
    */

    public static String getVisibleTopic() {
        return sVisibleTopic;
    }

    public static void setVisibleTopic(String topic) {
        sVisibleTopic = topic;
    }

    /** Login successful. Show contacts activity */
    public static void onLoginSuccess(Activity activity, final Button button) {
        if (button != null) {
            activity.runOnUiThread(new Runnable() {
                public void run() {
                    button.setEnabled(true);
                }
            });
        }

        Intent intent = new Intent(activity, ContactsActivity.class);
        activity.startActivity(intent);
        activity.finish();
    }

    public static boolean checkAccountAccessPermission(Context context) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
                ActivityCompat.checkSelfPermission(context, android.Manifest.permission.GET_ACCOUNTS) ==
                        PackageManager.PERMISSION_GRANTED;
    }

    public static void loginWithSavedAccount(final Activity activity,
                                              final AccountManager accountManager,
                                              final Account account) {
        accountManager.getAuthToken(account, Utils.TOKEN_TYPE, null, false, new AccountManagerCallback<Bundle>() {
            @Override
            public void run(AccountManagerFuture<Bundle> future) {
                Bundle result = null;

                try {
                    result = future.getResult(); // This blocks until the future is ready.
                } catch (OperationCanceledException e) {
                    Log.i(TAG, "Get Existing Account canceled.");
                } catch (AuthenticatorException e) {
                    Log.e(TAG, "AuthenticatorException: ", e);
                } catch (IOException e) {
                    Log.e(TAG, "IOException: ", e);
                }

                boolean success = false;
                if (result != null) {
                    final String token = result.getString(AccountManager.KEY_AUTHTOKEN);
                    if (!TextUtils.isEmpty(token)) {
                        final SharedPreferences sharedPref
                                = PreferenceManager.getDefaultSharedPreferences(activity);
                        String hostName = sharedPref.getString(Utils.PREFS_HOST_NAME, Cache.HOST_NAME);
                        try {
                            // Connecting with synchronous calls because this is not the UI thread.
                            final Tinode tinode = Cache.getTinode();
                            tinode.connect(hostName).getResult();
                            tinode.loginToken(token).getResult();
                            // Logged in successfully. Save refreshed token for future use.
                            accountManager.setAuthToken(account, Utils.TOKEN_TYPE, tinode.getAuthToken());

                            // Go to Contacts
                            success = true;
                        } catch (WebSocketException | IOException ignored) {
                            // Login failed due to network error.
                            // If we have UID, go to Contacts, otherwise to Login
                            success = BaseDb.getInstance().isReady();
                            Log.d(TAG, "Network failure/" + (success ? "DB ready" : "DB NOT ready"));
                        }
                        catch (Exception ignored) {
                            Log.d(TAG, "Other failure", ignored);
                            // Login failed due to invalid (expired) token
                            accountManager.invalidateAuthToken(Utils.ACCOUNT_TYPE, token);
                        }
                    }
                }
                activity.startActivity(new Intent(activity, success ? ContactsActivity.class : LoginActivity.class));
            }
        }, null);
    }

    public static void setConnectedStatus(final Activity activity, final boolean online) {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                final Toolbar toolbar = (Toolbar) activity.findViewById(R.id.toolbar);
                if (toolbar != null) {
                    Menu menu = toolbar.getMenu();
                    if (menu != null) {
                        menu.setGroupVisible(R.id.offline, !online);
                    }
                    View line = activity.findViewById(R.id.offline_indicator);
                    if (line != null) {
                        line.setVisibility(online ? View.GONE : View.VISIBLE);
                    }
                }
            }
        });
    }

    public static String shortDate(Date date) {
        if (date != null) {
            Calendar now = Calendar.getInstance();
            Calendar then = Calendar.getInstance();
            then.setTime(date);

            if (then.get(Calendar.YEAR) == now.get(Calendar.YEAR)) {
                if (then.get(Calendar.MONTH) == now.get(Calendar.MONTH) &&
                        then.get(Calendar.DATE) == now.get(Calendar.DATE)) {
                    return DateFormat.getTimeInstance(DateFormat.SHORT).format(then.getTime());
                } else {
                    return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(then.getTime());
                }
            }
            return DateFormat.getInstance().format(then.getTime());
        }
        return "null date";
    }

    public static void requestAvatar(Fragment fragment) {
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);

        fragment.startActivityForResult(Intent.createChooser(intent, fragment.getString(R.string.select_image)),
                UiUtils.SELECT_PICTURE);
    }

    /*
    private void openImageIntent(Fragment fragment) {

// Determine Uri of camera image to save.
        final File root = new File(Environment.getExternalStorageDirectory() + File.separator + "MyDir" + File.separator);
        root.mkdirs();
        final String fname = Utils.getUniqueImageFilename();
        final File sdImageMainDirectory = new File(root, fname);
        outputFileUri = Uri.fromFile(sdImageMainDirectory);

        // Camera.
        final List<Intent> cameraIntents = new ArrayList<Intent>();
        final Intent captureIntent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
        final PackageManager packageManager = getPackageManager();
        final List<ResolveInfo> listCam = packageManager.queryIntentActivities(captureIntent, 0);
        for(ResolveInfo res : listCam) {
            final String packageName = res.activityInfo.packageName;
            final Intent intent = new Intent(captureIntent);
            intent.setComponent(new ComponentName(res.activityInfo.packageName, res.activityInfo.name));
            intent.setPackage(packageName);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, outputFileUri);
            cameraIntents.add(intent);
        }

        // Filesystem.
        final Intent galleryIntent = new Intent();
        galleryIntent.setType("image/*");
        galleryIntent.setAction(Intent.ACTION_GET_CONTENT);

        // Chooser of filesystem options.
        final Intent chooserIntent = Intent.createChooser(galleryIntent, "Select Source");

        // Add the camera options.
        chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, cameraIntents.toArray(new Parcelable[cameraIntents.size()]));

        startActivityForResult(chooserIntent, YOUR_SELECT_PICTURE_REQUEST_CODE);
    }
    */
    public static boolean acceptAvatar(Activity activity, ImageView avatar, Intent data) {
        try {
            Bitmap bmp = MediaStore.Images.Media.getBitmap(activity.getContentResolver(),
                    data.getData());
            int width = bmp.getWidth();
            int height = bmp.getHeight();
            if (width > height) {
                width = width * BITMAP_SIZE / height;
                height = BITMAP_SIZE;
                // Sanity check
                width = width > 1024 ? 1024 : width;
            } else {
                height = height * BITMAP_SIZE / width;
                width = BITMAP_SIZE;
                height = height > 1024 ? 1024 : height;
            }
            // Scale up or down.
            bmp = Bitmap.createScaledBitmap(bmp, width, height, true);
            // Chop the square from the middle.
            bmp = Bitmap.createBitmap(bmp, width - BITMAP_SIZE, height - BITMAP_SIZE,
                    BITMAP_SIZE, BITMAP_SIZE);
            avatar.setImageBitmap(bmp);
            return true;
        } catch (IOException ex) {
            Toast.makeText(activity, activity.getString(R.string.image_is_missing), Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    public static class EventListener extends Tinode.EventListener {
        private AppCompatActivity mActivity = null;
        private Boolean mOnline = null;

        public EventListener(AppCompatActivity owner, Boolean online) {
            super();
            mActivity = owner;
            mOnline = online;
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
            if (mActivity != null && (mOnline == null || online != mOnline)) {
                mOnline = online;
                UiUtils.setConnectedStatus(mActivity, online);
            } else {
                mOnline = null;
            }
        }
    }
}
