package co.tinode.tindroid;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ContentUris;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.ContactsContract;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.preference.PreferenceManager;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.Button;
import android.widget.CheckedTextView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.pchmn.materialchips.model.Chip;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import co.tinode.tindroid.account.Utils;
import co.tinode.tindroid.db.BaseDb;
import co.tinode.tindroid.media.VxCard;
import co.tinode.tindroid.widgets.LetterTileDrawable;
import co.tinode.tindroid.widgets.OnlineDrawable;
import co.tinode.tindroid.widgets.RoundImageDrawable;
import co.tinode.tinodesdk.MeTopic;
import co.tinode.tinodesdk.NotConnectedException;
import co.tinode.tinodesdk.NotSynchronizedException;
import co.tinode.tinodesdk.PromisedReply;
import co.tinode.tinodesdk.ServerResponseException;
import co.tinode.tinodesdk.Tinode;
import co.tinode.tinodesdk.Topic;
import co.tinode.tinodesdk.model.Acs;
import co.tinode.tinodesdk.model.PrivateType;
import co.tinode.tinodesdk.model.ServerMessage;

/**
 * Static utilities for UI support.
 */
public class UiUtils {
    static final int ACTION_UPDATE_SELF_SUB = 0;
    static final int ACTION_UPDATE_SUB = 1;
    static final int ACTION_UPDATE_AUTH = 2;
    static final int ACTION_UPDATE_ANON = 3;
    static final int SELECT_PICTURE = 1;
    static final int READ_EXTERNAL_STORAGE_PERMISSION = 100;
    static final String PREF_TYPING_NOTIF = "pref_typingNotif";
    static final String PREF_READ_RCPT = "pref_readReceipts";
    static final int COLOR_ONLINE = Color.argb(255, 0x40, 0xC0, 0x40);
    static final int COLOR_OFFLINE = Color.argb(255, 0xC0, 0xC0, 0xC0);
    static final int COLOR_MESSAGE_BUBBLE = 0xffc5e1a5;
    static final int COLOR_META_BUBBLE = 0xFFCFD8DC;
    private static final String TAG = "UiUtils";
    private static final int AVATAR_SIZE = 128;
    private static final int MAX_BITMAP_SIZE = 1024;
    // Material colors, shade #200.
    // TODO(gene): maybe move to resource file
    private static final Colorizer[] sColorizer = {
            new Colorizer(0xffffffff, 0xff212121),
            new Colorizer(0xffef9a9a, 0xff212121), new Colorizer(0xffc5e1a5, 0xff212121),
            new Colorizer(0xff90caf9, 0xff212121), new Colorizer(0xfffff59d, 0xff212121),
            new Colorizer(0xffb0bec5, 0xff212121), new Colorizer(0xfff48fb1, 0xff212121),
            new Colorizer(0xffb39ddb, 0xff212121), new Colorizer(0xff9fa8da, 0xff212121),
            new Colorizer(0xffffab91, 0xff212121), new Colorizer(0xffffe082, 0xff212121),
            new Colorizer(0xffa5d6a7, 0xff212121), new Colorizer(0xffbcaaa4, 0xff212121),
            new Colorizer(0xffeeeeee, 0xff212121), new Colorizer(0xff80deea, 0xff212121),
            new Colorizer(0xffe6ee9c, 0xff212121), new Colorizer(0xffce93d8, 0xff212121)
    };
    private static final Colorizer[] sColorizerDark = {
            new Colorizer(0xff424242, 0xffdedede),
            new Colorizer(0xffC62828, 0xffdedede), new Colorizer(0xffAD1457, 0xffdedede),
            new Colorizer(0xff6A1B9A, 0xffdedede), new Colorizer(0xff4527A0, 0xffdedede),
            new Colorizer(0xff283593, 0xffdedede), new Colorizer(0xff1565C0, 0xffdedede),
            new Colorizer(0xff0277BD, 0xffdedede), new Colorizer(0xff00838F, 0xffdedede),
            new Colorizer(0xff00695C, 0xffdedede), new Colorizer(0xff2E7D32, 0xffdedede),
            new Colorizer(0xff558B2F, 0xffdedede), new Colorizer(0xff9E9D24, 0xff212121),
            new Colorizer(0xffF9A825, 0xff212121), new Colorizer(0xffFF8F00, 0xff212121),
            new Colorizer(0xffEF6C00, 0xffdedede), new Colorizer(0xffD84315, 0xffdedede),
            new Colorizer(0xff4E342E, 0xffdedede), new Colorizer(0xff37474F, 0xffdedede)
    };
    private static final int COLOR_GREEN_BORDER = 0xFF4CAF50;
    private static final int COLOR_RED_BORDER = 0xFFE57373;
    private static final int COLOR_GRAY_BORDER = 0xFF9E9E9E;
    private static final int COLOR_BLUE_BORDER = 0xFF2196F3;
    private static final int COLOR_YELLOW_BORDER = 0xFFFFCA28;
    // If StoredMessage activity is visible, this is the current topic in that activity.
    static String sVisibleTopic = null;

    // Logo LayerDrawable IDs
    private static final int LOGO_LAYER_AVATAR = 0;
    private static final int LOGO_LAYER_ONLINE = 1;
    private static final int LOGO_LAYER_TYPING = 2;

    static void setupToolbar(final Activity activity, final VxCard pub,
                             final String topicName, final boolean online) {
        if (activity.isDestroyed() || activity.isFinishing()) {
            return;
        }

        final Toolbar toolbar = activity.findViewById(R.id.toolbar);
        if (toolbar == null) {
            return;
        }

        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (pub != null) {
                    toolbar.setTitle(" " + pub.fn);
                    constructToolbarLogo(activity, pub.getBitmap(), pub.fn, topicName, online);
                } else {
                    toolbar.setTitle(R.string.app_name);
                    toolbar.setLogo(null);
                }
            }
        });
    }

    // 0. [Avatar or LetterTileDrawable] 1. [Online indicator] 2. [Typing indicator]
    private static void constructToolbarLogo(final Activity activity, Bitmap avatar, String name,
                                             String uid, boolean online) {
        final Toolbar toolbar = activity.findViewById(R.id.toolbar);
        if (toolbar == null) {
            return;
        }

        Drawable avatarDrawable;
        if (avatar != null) {
            avatarDrawable = new RoundImageDrawable(avatar);
        } else {
            avatarDrawable = new LetterTileDrawable(activity)
                    .setLetterAndColor(name, uid)
                    .setContactTypeAndColor(Topic.getTopicTypeByName(uid) == Topic.TopicType.P2P ?
                            LetterTileDrawable.TYPE_PERSON : LetterTileDrawable.TYPE_GROUP);
        }
        AnimationDrawable typing = (AnimationDrawable)
                activity.getResources().getDrawable(R.drawable.typing_indicator);
        typing.setOneShot(false);
        typing.setVisible(false, true);
        typing.setAlpha(0);
        LayerDrawable layers = new LayerDrawable(
                new Drawable[]{
                        avatarDrawable,
                        new OnlineDrawable(online),
                        typing});
        layers.setId(0, LOGO_LAYER_AVATAR);
        layers.setId(1, LOGO_LAYER_ONLINE);
        layers.setId(2, LOGO_LAYER_TYPING);
        toolbar.setLogo(layers);
        Rect b = toolbar.getLogo().getBounds();
        typing.setBounds(b.right - b.width()/4, b.bottom - b.height()/4, b.right, b.bottom);
    }

    static Timer toolbarTypingIndicator(final Activity activity, Timer timer, int duration) {
        if (timer != null) {
            timer.cancel();
        }

        final Toolbar toolbar = (Toolbar) activity.findViewById(R.id.toolbar);
        if (toolbar == null) {
            return null;
        }
        Drawable logo = toolbar.getLogo();
        if (!(logo instanceof LayerDrawable)) {
            return null;
        }

        final AnimationDrawable typing = (AnimationDrawable) ((LayerDrawable) logo)
                .findDrawableByLayerId(LOGO_LAYER_TYPING);
        Rect b = logo.getBounds();
        typing.setBounds(b.right - b.width()/4, b.bottom - b.height()/4, b.right, b.bottom);
        typing.setVisible(true, false);
        typing.setAlpha(255);
        typing.start();

        timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (activity.isDestroyed() || activity.isFinishing()) {
                    return;
                }

                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        typing.setVisible(false, true);
                        typing.setAlpha(0);
                    }
                });
            }
        }, duration);
        return timer;
    }

    static void toolbarSetOnline(final Activity activity, boolean online) {
        final Toolbar toolbar = activity.findViewById(R.id.toolbar);
        if (toolbar == null) {
            return;
        }
        Drawable logo = toolbar.getLogo();
        if (!(logo instanceof LayerDrawable)) {
            return;
        }

        ((OnlineDrawable)((LayerDrawable) logo).findDrawableByLayerId(LOGO_LAYER_ONLINE)).setOnline(online);
    }

    public static String getVisibleTopic() {
        return sVisibleTopic;
    }

    static void setVisibleTopic(String topic) {
        sVisibleTopic = topic;
    }

    /**
     * Login successful. Show contacts activity
     */
    static void onLoginSuccess(Activity activity, final Button button) {
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

    static boolean checkPermission(Context context, String permission) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
                ActivityCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED;
    }

    static void loginWithSavedAccount(final Activity activity,
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

                Intent launch = new Intent(activity, LoginActivity.class);
                if (result != null) {
                    final String token = result.getString(AccountManager.KEY_AUTHTOKEN);
                    if (!TextUtils.isEmpty(token)) {
                        final SharedPreferences sharedPref
                                = PreferenceManager.getDefaultSharedPreferences(activity);
                        String hostName = sharedPref.getString(Utils.PREFS_HOST_NAME, Cache.HOST_NAME);
                        boolean tls = sharedPref.getBoolean(Utils.PREFS_USE_TLS, false);
                        try {
                            // Connecting with synchronous calls because this is not the UI thread.
                            final Tinode tinode = Cache.getTinode();
                            tinode.connect(hostName, tls).getResult();
                            ServerMessage msg = tinode.loginToken(token).getResult();
                            // Logged in successfully. Save refreshed token for future use.
                            accountManager.setAuthToken(account, Utils.TOKEN_TYPE, tinode.getAuthToken());
                            if (msg == null || msg.ctrl.code < 300) {
                                // Logged in successfully.
                                Log.d(TAG, "LoginWithSavedAccount succeeded, sending to contacts");
                                // Go to Contacts
                                launch = new Intent(activity, ContactsActivity.class);
                            } else {
                                Log.d(TAG, "LoginWithSavedAccount failed due to credentials, sending to login");
                                Iterator<String> it = msg.ctrl.getStringIteratorParam("cred");
                                launch.putExtra("credential", it.next());
                            }
                        } catch (IOException ex) {
                            // Login failed due to network error.
                            // If we have UID, go to Contacts, otherwise to Login
                            launch = new Intent(activity, BaseDb.getInstance().isReady() ?
                                    ContactsActivity.class : LoginActivity.class);
                            Log.d(TAG, "Network failure/" + (BaseDb.getInstance().isReady() ? "DB ready" : "DB NOT ready"));
                        } catch (Exception ex) {
                            Log.d(TAG, "Other failure", ex);
                            // Login failed due to invalid (expired) token
                            accountManager.invalidateAuthToken(Utils.ACCOUNT_TYPE, token);
                        }
                    }
                }
                activity.startActivity(launch);
            }
        }, null);
    }

    static Account getSavedAccount(final Activity activity, final AccountManager accountManager,
                                   final @NonNull String uid) {
        Account account = null;

        // Run-time check for permission to GET_ACCOUNTS
        if (!UiUtils.checkPermission(activity, android.Manifest.permission.GET_ACCOUNTS)) {
            // Don't have permission. It's the first launch or the user denied access.
            // Fail and go to full login. We should not ask for permission on the splash screen.
            Log.d(TAG, "NO permission to get accounts");
            return null;
        }

        // Have permission to access accounts. Let's find out if we already have a suitable account.
        // If one is not found, go to full login. It will create an account with suitable name.
        final Account[] availableAccounts = accountManager.getAccountsByType(Utils.ACCOUNT_TYPE);
        if (availableAccounts.length > 0) {
            // Found some accounts, let's find the one with the right name
            for (Account acc : availableAccounts) {
                if (uid.equals(acc.name)) {
                    account = acc;
                    break;
                }
            }
        }

        return account;
    }

    private static void setConnectedStatus(final Activity activity, final boolean online) {
        if (activity.isDestroyed() || activity.isFinishing()) {
            return;
        }

        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                final Toolbar toolbar = activity.findViewById(R.id.toolbar);
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

    // Date formatter for messages
    static String shortDate(Date date) {
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

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    static void requestAvatar(Fragment fragment) {
        final Activity activity = fragment.getActivity();
        if (activity == null) {
            return;
        }

        if (!checkPermission(activity, android.Manifest.permission.READ_EXTERNAL_STORAGE)) {
            ActivityCompat.requestPermissions(activity, new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE},
                    READ_EXTERNAL_STORAGE_PERMISSION);
        } else {
            Intent intent = new Intent();
            intent.setType("image/*");
            intent.setAction(Intent.ACTION_GET_CONTENT);

            fragment.startActivityForResult(Intent.createChooser(intent, fragment.getString(R.string.select_image)),
                    UiUtils.SELECT_PICTURE);
        }
    }

    static Bitmap scaleSquareBitmap(Bitmap bmp) {
        int width = bmp.getWidth();
        int height = bmp.getHeight();
        if (width > height) {
            width = width * AVATAR_SIZE / height;
            height = AVATAR_SIZE;
            // Sanity check
            width = width > MAX_BITMAP_SIZE ? MAX_BITMAP_SIZE : width;
        } else {
            height = height * AVATAR_SIZE / width;
            width = AVATAR_SIZE;
            height = height > MAX_BITMAP_SIZE ? MAX_BITMAP_SIZE : height;
        }
        // Scale up or down.
        bmp = Bitmap.createScaledBitmap(bmp, width, height, true);
        // Chop the square from the middle.
        return Bitmap.createBitmap(bmp, (width - AVATAR_SIZE)/2, (height - AVATAR_SIZE)/2,
                AVATAR_SIZE, AVATAR_SIZE);
    }

    static Bitmap scaleBitmap(Bitmap bmp) {
        int width = bmp.getWidth();
        int height = bmp.getHeight();
        boolean changed = false;
        if (width >= height) {
            if (width > MAX_BITMAP_SIZE) {
                changed = true;
                height = height * MAX_BITMAP_SIZE / width;
                width = MAX_BITMAP_SIZE;
            }
        } else {
            if (height > MAX_BITMAP_SIZE) {
                changed = true;
                width = width * MAX_BITMAP_SIZE / height;
                height = MAX_BITMAP_SIZE;
            }
        }
        return changed ? Bitmap.createScaledBitmap(bmp, width, height, true) : bmp;
    }

    static Bitmap extractBitmap(final Activity activity, final Intent data) {
        try {
            return MediaStore.Images.Media.getBitmap(activity.getContentResolver(),
                    data.getData());
        } catch (IOException ex) {
            return null;
        }
    }

    static boolean acceptAvatar(final ImageView avatar, final Bitmap bmp) {
        avatar.setImageDrawable(new RoundImageDrawable(scaleSquareBitmap(bmp)));
        return true;
    }

    static boolean acceptAvatar(final Activity activity, final ImageView avatar, final Intent data) {
        final Bitmap bmp = extractBitmap(activity, data);
        if (bmp == null) {
            Toast.makeText(activity, activity.getString(R.string.image_is_missing), Toast.LENGTH_SHORT).show();
            return false;
        }
        return acceptAvatar(avatar, bmp);
    }

    static void assignBitmap(Context context, ImageView icon, Bitmap bmp, String name, String address) {
        if (bmp != null) {
            icon.setImageDrawable(new RoundImageDrawable(bmp));
        } else {
            LetterTileDrawable drawable = new LetterTileDrawable(context);
            drawable.setContactTypeAndColor(
                    Topic.getTopicTypeByName(address) == Topic.TopicType.P2P ?
                            LetterTileDrawable.TYPE_PERSON : LetterTileDrawable.TYPE_GROUP)
                    .setLetterAndColor(name, address)
                    .setIsCircular(true);
            icon.setImageDrawable(drawable);
        }
    }

    /**
     * Decodes and scales a contact's image from a file pointed to by a Uri in the contact's data,
     * and returns the result as a Bitmap. The column that contains the Uri varies according to the
     * platform version.
     *
     * @param photoData For platforms prior to Android 3.0, provide the Contact._ID column value.
     *                  For Android 3.0 and later, provide the Contact.PHOTO_THUMBNAIL_URI value.
     * @param imageSize The desired target width and height of the output image in pixels.
     * @return A Bitmap containing the contact's image, resized to fit the provided image size. If
     * no thumbnail exists, returns null.
     */
    static Bitmap loadContactPhotoThumbnail(Fragment fragment, String photoData, int imageSize) {

        // Ensures the Fragment is still added to an activity. As this method is called in a
        // background thread, there's the possibility the Fragment is no longer attached and
        // added to an activity. If so, no need to spend resources loading the contact photo.
        if (!fragment.isAdded() || fragment.getActivity() == null) {
            return null;
        }

        // Instantiates an AssetFileDescriptor. Given a content Uri pointing to an image file, the
        // ContentResolver can return an AssetFileDescriptor for the file.

        // This "try" block catches an Exception if the file descriptor returned from the Contacts
        // Provider doesn't point to an existing file.
        Uri thumbUri = Uri.parse(photoData);
        try (AssetFileDescriptor afd = fragment.getActivity().getContentResolver().openAssetFileDescriptor(thumbUri, "r")) {

            // Retrieves a file descriptor from the Contacts Provider. To learn more about this
            // feature, read the reference documentation for
            // ContentResolver#openAssetFileDescriptor.

            // Gets a FileDescriptor from the AssetFileDescriptor. A BitmapFactory object can
            // decode the contents of a file pointed to by a FileDescriptor into a Bitmap.
            if (afd != null) {
                // Decodes a Bitmap from the image pointed to by the FileDescriptor, and scales it
                // to the specified width and height
                return ImageLoader.decodeSampledBitmapFromStream(
                        new BufferedInputStream(new FileInputStream(afd.getFileDescriptor())), imageSize, imageSize);
            }
        } catch (IOException e) {
            // If the file pointed to by the thumbnail URI doesn't exist, or the file can't be
            // opened in "read" mode, ContentResolver.openAssetFileDescriptor throws a
            // FileNotFoundException.
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Contact photo thumbnail not found for contact " + photoData
                        + ": " + e.toString());
            }
        }
        // If an AssetFileDescriptor was returned, try to close it
        // Closing a file descriptor might cause an IOException if the file is
        // already closed. Nothing extra is needed to handle this.

        // If the decoding failed, returns null
        return null;
    }

    static ByteArrayInputStream bitmapToStream(Bitmap bmp, String mimeType) {
        Bitmap.CompressFormat fmt;
        if ("image/jpeg".equals(mimeType)) {
            fmt = Bitmap.CompressFormat.JPEG;
        } else {
            fmt = Bitmap.CompressFormat.PNG;
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        bmp.compress(fmt, 70, bos);
        return new ByteArrayInputStream(bos.toByteArray());
    }

    /**
     * Gets the preferred height for each item in the ListView, in pixels, after accounting for
     * screen density. ImageLoader uses this value to resize thumbnail images to match the ListView
     * item height.
     *
     * @return The preferred height in pixels, based on the current theme.
     */
    static int getListPreferredItemHeight(Fragment fragment) {
        final TypedValue typedValue = new TypedValue();

        final Activity activity = fragment.getActivity();
        if (activity == null) {
            return -1;
        }

        // Resolve list item preferred height theme attribute into typedValue
        activity.getTheme().resolveAttribute(
                android.R.attr.listPreferredItemHeight, typedValue, true);

        // Create a new DisplayMetrics object
        final DisplayMetrics metrics = new android.util.DisplayMetrics();

        // Populate the DisplayMetrics
        activity.getWindowManager().getDefaultDisplay().getMetrics(metrics);

        // Return theme value based on DisplayMetrics
        return (int) typedValue.getDimension(metrics);
    }

    public static Colorizer getColorsFor(String uid) {
        int index = uid != null ? Math.abs(uid.hashCode()) : 0;
        return sColorizer[index % sColorizer.length];
    }

    public static Colorizer getDarkColorsFor(int index) {
        if (index >= sColorizerDark.length) {
            index = index % sColorizerDark.length;
        }
        return sColorizerDark[index];
    }

    public static Colorizer getDarkColorsFor(String uid) {
        int index = uid != null ? Math.abs(uid.hashCode()) : 0;
        return sColorizerDark[index % sColorizerDark.length];
    }

    static AccessModeLabel[] accessModeLabels(final Acs acs, final int status) {
        ArrayList<AccessModeLabel> result = new ArrayList<>(2);
        if (acs != null) {
            if (acs.isModeDefined()) {
                if (!acs.isJoiner() || (!acs.isWriter() && !acs.isReader())) {
                    result.add(new AccessModeLabel(R.string.modeBlocked, COLOR_RED_BORDER));
                } else if (acs.isOwner()) {
                    result.add(new AccessModeLabel(R.string.modeOwner, COLOR_GREEN_BORDER));
                } else if (acs.isAdmin()) {
                    result.add(new AccessModeLabel(R.string.modeAdmin, COLOR_GREEN_BORDER));
                } else if (!acs.isWriter()) {
                    result.add(new AccessModeLabel(R.string.modeReadOnly, COLOR_YELLOW_BORDER));
                } else if (!acs.isReader()) {
                    result.add(new AccessModeLabel(R.string.modeWriteOnly, COLOR_YELLOW_BORDER));
                }
            } else if (!acs.isInvalid()) {
                // The mode is undefined (NONE)
                if (acs.isGivenDefined() && !acs.isWantDefined()) {
                    result.add(new AccessModeLabel(R.string.modeInvited, COLOR_GRAY_BORDER));
                } else if (!acs.isGivenDefined() && acs.isWantDefined()) {
                    result.add(new AccessModeLabel(R.string.modeRequested, COLOR_GRAY_BORDER));
                } else {
                    // Undefined state
                    result.add(new AccessModeLabel(R.string.modeUndefined, COLOR_GRAY_BORDER));
                }
            }
        }
        if (status == BaseDb.STATUS_QUEUED) {
            result.add(new AccessModeLabel(R.string.modePending, COLOR_GRAY_BORDER));
        }

        return !result.isEmpty() ?
                result.toArray(new AccessModeLabel[0]) : null;
    }

    static List<Chip> createChipsInputFilteredList(Cursor cursor) {
        List<Chip> list = new ArrayList<>();

        if (cursor != null) {
            cursor.moveToFirst();
            while (!cursor.isAfterLast()) {
                final String uid = cursor.getString(UiUtils.ContactsQuery.IM_HANDLE);
                final String uriString = cursor.getString(UiUtils.ContactsQuery.PHOTO_THUMBNAIL_DATA);
                final Uri photoUri = uriString == null ? null : Uri.parse(uriString);
                final String displayName = cursor.getString(UiUtils.ContactsQuery.DISPLAY_NAME);
                list.add(new Chip(uid, photoUri, displayName, null));
                cursor.moveToNext();
            }
        }

        return list;
    }

    static void showEditPermissions(final Activity activity, final Topic topic,
                                    @NonNull final String mode,
                                    final String uid, final int what,
                                    boolean noOwner) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        final LayoutInflater inflater = LayoutInflater.from(builder.getContext());
        final LinearLayout editor = (LinearLayout) inflater.inflate(R.layout.dialog_edit_permissions, null);
        builder
                .setView(editor)
                .setTitle(R.string.edit_permissions);
        final LinkedHashMap<Character, Integer> checks = new LinkedHashMap<>(8);
        if (!noOwner) {
            checks.put('O', R.string.permission_owner);
        }
        checks.put('J', R.string.permission_join);
        checks.put('R', R.string.permission_read);
        checks.put('W', R.string.permission_write);
        checks.put('A', R.string.permission_approve);
        checks.put('S', R.string.permission_share);
        checks.put('P', R.string.permission_notifications);
        checks.put('D', R.string.permission_delete);
        View.OnClickListener checkListener = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                boolean checked = !((CheckedTextView) view).isChecked();
                ((CheckedTextView) view).setChecked(checked);
            }
        };
        for (Character key : checks.keySet()) {
            if (noOwner && key.equals('O')) {
                continue;
            }
            CheckedTextView check = (CheckedTextView) inflater.inflate(R.layout.edit_one_permission, editor, false);
            check.setChecked(mode.contains(key.toString()));
            check.setText(checks.get(key));
            check.setTag(key);
            check.setOnClickListener(checkListener);
            editor.addView(check, editor.getChildCount());
        }

        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                StringBuilder newAcsStr = new StringBuilder();
                for (int i = 0; i < editor.getChildCount(); i++) {
                    CheckedTextView check = (CheckedTextView) editor.getChildAt(i);
                    if (check.isChecked()) {
                        newAcsStr.append(check.getTag());
                    }
                }
                if (newAcsStr.length() == 0) {
                    newAcsStr.append('N');
                }
                Log.d(TAG, "New access mode: " + newAcsStr);
                try {
                    PromisedReply reply = null;
                    switch (what) {
                        case ACTION_UPDATE_SELF_SUB:
                            Log.d(TAG, "Update self sub: " + newAcsStr);
                            reply = topic.updateMode(null, newAcsStr.toString());
                            break;
                        case ACTION_UPDATE_SUB:
                            Log.d(TAG, "Update other sub: " + newAcsStr);
                            reply = topic.updateMode(uid, newAcsStr.toString());
                            break;
                        case ACTION_UPDATE_AUTH:
                            Log.d(TAG, "Update auth: " + newAcsStr);
                            reply = topic.updateDefAcs(newAcsStr.toString(), null);
                            break;
                        case ACTION_UPDATE_ANON:
                            Log.d(TAG, "Update anon: " + newAcsStr);
                            reply = topic.updateDefAcs(null, newAcsStr.toString());
                            break;
                        default:
                            Log.d(TAG, "Unknown action " + what);
                    }

                    Log.d(TAG, "Reply is " + reply);
                    if (reply != null) {
                        ((PromisedReply<ServerMessage>) reply).thenApply(
                                new PromisedReply.SuccessListener<ServerMessage>() {
                                     @Override
                                     public PromisedReply<ServerMessage> onSuccess(ServerMessage result) {
                                         Log.d(TAG, "Successful update");
                                         return null;
                                     }
                                 },
                                new PromisedReply.FailureListener<ServerMessage>() {
                                    @Override
                                    public PromisedReply<ServerMessage> onFailure(final Exception err) {
                                        if (activity.isFinishing() || activity.isDestroyed()) {
                                            return null;
                                        }

                                        activity.runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                Log.d(TAG, "Failed", err);
                                                Toast.makeText(activity, R.string.action_failed, Toast.LENGTH_SHORT).show();
                                            }
                                        });
                                        return null;
                                    }
                                });
                    }
                } catch (NotConnectedException ignored) {
                    Toast.makeText(activity, R.string.no_connection, Toast.LENGTH_SHORT).show();
                } catch (Exception ignored) {
                    Toast.makeText(activity, R.string.action_failed, Toast.LENGTH_SHORT).show();
                }
            }
        });
        builder.setNegativeButton(android.R.string.cancel, null);
        builder.show();
    }

    static <T extends Topic<VxCard,PrivateType,?,?>> boolean updateAvatar(final Activity activity,
                                                                          final T topic, final Intent data) {
        Bitmap bmp = UiUtils.extractBitmap(activity, data);
        if (bmp == null) {
            Toast.makeText(activity, activity.getString(R.string.image_is_missing), Toast.LENGTH_SHORT).show();
            return false;
        }
        VxCard pub = topic.getPub();
        if (pub != null) {
            pub = pub.copy();
        } else {
            pub = new VxCard();
        }

        pub.setBitmap(bmp);
        try {
            topic.setDescription(pub, null).thenApply(null, new ToastFailureListener(activity));
        } catch (NotConnectedException ignored) {
            Toast.makeText(activity, R.string.no_connection, Toast.LENGTH_SHORT).show();
            return false;
        } catch (Exception ignored) {
            Toast.makeText(activity, R.string.action_failed, Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    static <T extends Topic<VxCard,PrivateType,?,?>> boolean updateTitle(final Activity activity,
                                                                         T topic, String title, String comment) {
        VxCard pub = null;
        if (title != null) {
            pub = topic.getPub();
            if (pub == null) {
                pub = new VxCard();
            } else {
                pub = pub.copy();
            }
            if (title.equals(pub.fn)) {
                pub = null;
            } else {
                pub.fn = title;
            }
        }

        if (comment != null) {
            String oldComment = topic.getPriv().getComment();
            if (comment.equals(oldComment)) {
                comment = null;
            }
        }

        if (pub != null || comment != null) {
            try {
                PrivateType priv = new PrivateType();
                priv.setComment(comment);
                topic.setDescription(pub, priv).thenApply(null, new ToastFailureListener(activity));
            } catch (NotConnectedException ignored) {
                Toast.makeText(activity, R.string.no_connection, Toast.LENGTH_SHORT).show();
                return false;
            } catch (Exception ignored) {
                Toast.makeText(activity, R.string.action_failed, Toast.LENGTH_SHORT).show();
                return false;
            }
        }
        Log.d(TAG, "OK");

        return true;
    }

    @SuppressWarnings("unchecked")
    static void attachMeTopic(final Activity activity, Topic.Listener l) {
        try {
            setProgressIndicator(activity, true);
            Cache.attachMeTopic(l)
                    .thenApply(new PromisedReply.SuccessListener() {
                        @Override
                        public PromisedReply onSuccess(Object result) throws Exception {
                            UiUtils.setProgressIndicator(activity, false);
                            return null;
                        }
                    }, new PromisedReply.FailureListener() {
                        @Override
                        public PromisedReply onFailure(Exception err) throws Exception {
                            Log.d(TAG, "Error subscribing to 'me' topic", err);
                            UiUtils.setProgressIndicator(activity, false);
                            if (err instanceof ServerResponseException) {
                                ServerResponseException sre = (ServerResponseException) err;
                                if (sre.getCode() == 404) {
                                    Cache.getTinode().logout();
                                    activity.startActivity(new Intent(activity, LoginActivity.class));
                                }
                            }
                            return null;
                        }
                    });
        } catch (NotSynchronizedException ignored) {
            setProgressIndicator(activity,false);
            /* */
        } catch (NotConnectedException ignored) {
            /* offline - ignored */
            setProgressIndicator(activity,false);
            Toast.makeText(activity, R.string.no_connection, Toast.LENGTH_SHORT).show();
        } catch (Exception err) {
            Log.i(TAG, "Subscription failed", err);
            setProgressIndicator(activity,false);
            Toast.makeText(activity, R.string.failed_to_attach, Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Show or hide progress indicator in the toolbar.
     *
     * @param activity activity making the call.
     * @param active show when true, hide when false.
     */
     private static void setProgressIndicator(final Activity activity, final boolean active) {
        if (activity.isFinishing() || activity.isDestroyed()) {
            return;
        }

        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ProgressBar progressBar = activity.findViewById(R.id.toolbar_progress_bar);
                if (progressBar != null) {
                    progressBar.setVisibility(active ? View.VISIBLE : View.GONE);
                }
            }
        });
    }

    interface ContactsQuery {
        String[] PROJECTION = {
                ContactsContract.Data._ID,
                ContactsContract.Data.CONTACT_ID,
                ContactsContract.CommonDataKinds.Im.DISPLAY_NAME_PRIMARY,
                ContactsContract.CommonDataKinds.Im.PHOTO_THUMBNAIL_URI,
                ContactsContract.CommonDataKinds.Im.DATA,
                ContactsContract.Data.MIMETYPE,
                ContactsContract.CommonDataKinds.Im.PROTOCOL,
                ContactsContract.CommonDataKinds.Im.CUSTOM_PROTOCOL,
        };

        int ID = 0;
        int CONTACT_ID = 1;
        int DISPLAY_NAME = 2;
        int PHOTO_THUMBNAIL_DATA = 3;
        int IM_HANDLE = 4;

        String SELECTION = ContactsContract.Data.MIMETYPE + "=? AND " +
                ContactsContract.CommonDataKinds.Im.PROTOCOL + "=? AND " +
                ContactsContract.CommonDataKinds.Im.CUSTOM_PROTOCOL + "=?";
        String[] SELECTION_ARGS = {
                ContactsContract.CommonDataKinds.Im.CONTENT_ITEM_TYPE,
                Integer.toString(ContactsContract.CommonDataKinds.Im.PROTOCOL_CUSTOM),
                Utils.IM_PROTOCOL,
        };
        String SORT_ORDER = ContactsContract.CommonDataKinds.Im.DISPLAY_NAME_PRIMARY;
    }

    interface ContactsLoaderResultReceiver {
        void receiveResult(int id, Cursor c);
    }

    public static class EventListener extends Tinode.EventListener {
        private AppCompatActivity mActivity;
        private Boolean mConnected;

        EventListener(AppCompatActivity owner, Boolean connected) {
            super();
            mActivity = owner;
            mConnected = connected;
        }

        @Override
        public void onConnect(int code, String reason, Map<String, Object> params) {
            // Show that we are connected
            setConnected(true);
        }

        @Override
        public void onDisconnect(boolean byServer, int code, String reason) {
            // Show that we are disconnected
            if (code <= 0) {
                Log.d(TAG, "Network error");
            } else {
                Log.d(TAG, "Tinode error: " + code);
            }
            setConnected(false);
        }

        private void setConnected(final boolean connected) {
            if (mActivity != null &&
                    (mConnected == null || connected != mConnected)) {
                mConnected = connected;
                setConnectedStatus(mActivity, connected);
            } else {
                mConnected = null;
            }
        }
    }

    public static class Colorizer {
        int bg;
        int fg;

        Colorizer(int bg, int fg) {
            this.bg = bg;
            this.fg = fg;
        }
    }

    public static class AccessModeLabel {
        int nameId;
        public int color;

        AccessModeLabel(int nameId, int color) {
            this.nameId = nameId;
            this.color = color;
        }
    }

    static class ContactsLoaderCallback implements LoaderManager.LoaderCallbacks<Cursor> {
        private Activity mActivity;
        private ContactsLoaderResultReceiver mReceiver;
        private int mLoaderId = -1;

        ContactsLoaderCallback(Activity activity, ContactsLoaderResultReceiver receiver) {
            mActivity = activity;
            mReceiver = receiver;
        }

        @NonNull
        @Override
        public Loader<Cursor> onCreateLoader(int id, Bundle args) {
            mLoaderId = id;

            // Returns a new CursorLoader for querying the Contacts table. No arguments are used
            // for the selection clause. The search string is either encoded onto the content URI,
            // or no contacts search string is used. The other search criteria are constants. See
            // the ContactsQuery interface.
            return new CursorLoader(mActivity,
                    ContactsContract.Data.CONTENT_URI,
                    ContactsQuery.PROJECTION,
                    ContactsQuery.SELECTION,
                    ContactsQuery.SELECTION_ARGS,
                    ContactsQuery.SORT_ORDER);
        }

        @Override
        public void onLoadFinished(@NonNull Loader<Cursor> loader, Cursor data) {
            // This swaps the new cursor into the adapter.
            Log.d(TAG, "delivered cursor with items: " + data.getCount());
            mReceiver.receiveResult(mLoaderId, data);
        }

        @Override
        public void onLoaderReset(@NonNull Loader<Cursor> loader) {
            // When the loader is being reset, clear the cursor from the adapter. This allows the
            // cursor resources to be freed.
            mReceiver.receiveResult(mLoaderId, null);
        }
    }

    static class ToastFailureListener extends PromisedReply.FailureListener<ServerMessage> {
        private Activity mActivity;

        ToastFailureListener(Activity activity) {
            mActivity = activity;
        }

        @Override
        public PromisedReply<ServerMessage> onFailure(final Exception err) {
            if (mActivity.isFinishing() || mActivity.isDestroyed()) {
                return null;
            }

            mActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (err instanceof NotConnectedException) {
                        Toast.makeText(mActivity, R.string.no_connection, Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(mActivity, R.string.action_failed, Toast.LENGTH_SHORT).show();
                    }
                }
            });
            return null;
        }
    }

    // Find path to content: DocumentProvider, DownloadsProvider, MediaProvider, MediaStore, File.
    static String getPath(Context context, Uri uri) {
        // DocumentProvider
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT &&
                DocumentsContract.isDocumentUri(context, uri)) {
            final String docId = DocumentsContract.getDocumentId(uri);
            String authority = uri.getAuthority();
            if (authority != null) {
                switch (authority) {
                    case "com.android.externalstorage.documents": {
                        // ExternalStorageProvider
                        final String[] split = docId.split(":");
                        final String type = split[0];

                        if ("primary".equalsIgnoreCase(type)) {
                            return Environment.getExternalStorageDirectory() + "/" + split[1];
                        }
                        // TODO handle non-primary volumes
                    }
                    break;
                    case "com.android.providers.downloads.documents": {
                        // DownloadsProvider

                        final Uri contentUri = ContentUris.withAppendedId(Uri.parse("content://downloads/public_downloads"),
                                Long.valueOf(docId));
                        return getDataColumn(context, contentUri, null, null);
                    }
                    case "com.android.providers.media.documents": {
                        // MediaProvider

                        final String[] split = docId.split(":");
                        final String type = split[0];
                        Uri contentUri = null;
                        if ("image".equals(type)) {
                            contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                        } else if ("video".equals(type)) {
                            contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                        } else if ("audio".equals(type)) {
                            contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                        }
                        final String selection = "_id=?";
                        final String[] selectionArgs = new String[]{split[1]};
                        return getDataColumn(context, contentUri, selection, selectionArgs);
                    }
                    default:
                        Log.d(TAG, "Unknown content authority " + uri.getAuthority());
                }
            } else {
                Log.d(TAG, "URI has no content authority " + uri);
            }
        } else if ("content".equalsIgnoreCase(uri.getScheme())) {
            // MediaStore (and general)
            // Return the remote address
            if ("com.google.android.apps.photos.content".equals(uri.getAuthority())) {
                return uri.getLastPathSegment();
            }
            return getDataColumn(context, uri, null, null);
        } else if ("file".equalsIgnoreCase(uri.getScheme())) {
            // File
            return uri.getPath();
        }
        return null;
    }

    private static String getDataColumn(Context context, Uri uri, String selection, String[] selectionArgs) {
        final String column = "_data";
        final String[] projection = { column };
        try (Cursor cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                final int index = cursor.getColumnIndexOrThrow(column);
                return cursor.getString(index);
            }
        }
        return null;
    }

    static String bytesToHumanSize(long bytes) {
        if (bytes <= 0) {
            return "0 Bytes";
        }

        String[] sizes = new String[] {"Bytes", "KB", "MB", "GB", "TB"};
        int bucket = (63 - Long.numberOfLeadingZeros(bytes)) / 10;
        double count = bytes / Math.pow(1024, bucket);
        int roundTo = bucket > 0 ? (count < 10 ? 2 : (count < 100 ? 1 : 0)) : 0;
        NumberFormat fmt = DecimalFormat.getInstance();
        fmt.setMaximumFractionDigits(roundTo);
        return fmt.format(count) + " " + sizes[bucket];
    }

    static String getMimeType(Uri uri) {
        if (uri == null) {
            return null;
        }

        MimeTypeMap mimeTypeMap = MimeTypeMap.getSingleton();
        String ext = MimeTypeMap.getFileExtensionFromUrl(uri.toString());
        if (mimeTypeMap.hasExtension(ext)) {
            return mimeTypeMap.getMimeTypeFromExtension(ext);
        }
        return null;
    }
}
