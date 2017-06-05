package co.tinode.tindroid;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.preference.PreferenceManager;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import com.neovisionaries.ws.client.WebSocketException;
import com.pchmn.materialchips.model.Chip;

import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;

import co.tinode.tindroid.account.Utils;
import co.tinode.tindroid.db.BaseDb;
import co.tinode.tinodesdk.Tinode;
import co.tinode.tinodesdk.Topic;
import co.tinode.tinodesdk.model.Acs;

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

    public static final int READ_EXTERNAL_STORAGE_PERMISSION = 100;

    public static void setupToolbar(final Activity activity, VCard pub,
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
                                new Drawable[] {new RoundImageDrawable(bmp), new OnlineDrawable(online)}));
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

    public static boolean checkPermission(Context context, String permission) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
                ActivityCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED;
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

    // Date formatter for messages
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

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    public static void requestAvatar(Fragment fragment) {
        Activity activity = fragment.getActivity();
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

    public static Bitmap scaleSquareBitmap(Bitmap bmp) {
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
        return Bitmap.createBitmap(bmp, width - BITMAP_SIZE, height - BITMAP_SIZE,
                BITMAP_SIZE, BITMAP_SIZE);
    }

    public static Bitmap extractBitmap(final Activity activity, final Intent data) {
        try {
            return MediaStore.Images.Media.getBitmap(activity.getContentResolver(),
                    data.getData());
        } catch (IOException ex) {
            return null;
        }
    }

    public static boolean acceptAvatar(final ImageView avatar, final Bitmap bmp) {
        avatar.setImageBitmap(scaleSquareBitmap(bmp));
        return true;
    }

    public static boolean acceptAvatar(final Activity activity, final ImageView avatar, final Intent data) {
        final Bitmap bmp = extractBitmap(activity, data);
        if (bmp == null) {
            Toast.makeText(activity, activity.getString(R.string.image_is_missing), Toast.LENGTH_SHORT).show();
            return false;
        }
        return acceptAvatar(avatar, bmp);
    }

    public static void assignBitmap(Context context, ImageView icon, Bitmap bmp, int defaultDrawable) {
        if (bmp != null) {
            icon.setImageDrawable(new RoundImageDrawable(bmp));
        } else {
            Drawable drw;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                drw = context.getResources().getDrawable(defaultDrawable, context.getTheme());
            } else {
                drw = context.getResources().getDrawable(defaultDrawable);
            }
            if (drw != null) {
                icon.setImageDrawable(drw);
            }
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
    public static Bitmap loadContactPhotoThumbnail(Fragment fragment, String photoData, int imageSize) {

        // Ensures the Fragment is still added to an activity. As this method is called in a
        // background thread, there's the possibility the Fragment is no longer attached and
        // added to an activity. If so, no need to spend resources loading the contact photo.
        if (!fragment.isAdded() || fragment.getActivity() == null) {
            return null;
        }

        // Instantiates an AssetFileDescriptor. Given a content Uri pointing to an image file, the
        // ContentResolver can return an AssetFileDescriptor for the file.
        AssetFileDescriptor afd = null;

        // This "try" block catches an Exception if the file descriptor returned from the Contacts
        // Provider doesn't point to an existing file.
        try {
            Uri thumbUri = Uri.parse(photoData);

            // Retrieves a file descriptor from the Contacts Provider. To learn more about this
            // feature, read the reference documentation for
            // ContentResolver#openAssetFileDescriptor.
            afd = fragment.getActivity().getContentResolver().openAssetFileDescriptor(thumbUri, "r");

            // Gets a FileDescriptor from the AssetFileDescriptor. A BitmapFactory object can
            // decode the contents of a file pointed to by a FileDescriptor into a Bitmap.
            FileDescriptor fileDescriptor = afd.getFileDescriptor();
            if (fileDescriptor != null) {
                // Decodes a Bitmap from the image pointed to by the FileDescriptor, and scales it
                // to the specified width and height
                return ImageLoader.decodeSampledBitmapFromDescriptor(
                        fileDescriptor, imageSize, imageSize);
            }
        } catch (FileNotFoundException e) {
            // If the file pointed to by the thumbnail URI doesn't exist, or the file can't be
            // opened in "read" mode, ContentResolver.openAssetFileDescriptor throws a
            // FileNotFoundException.
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Contact photo thumbnail not found for contact " + photoData
                        + ": " + e.toString());
            }
        } finally {
            // If an AssetFileDescriptor was returned, try to close it
            if (afd != null) {
                try {
                    afd.close();
                } catch (IOException unused) {
                    // Closing a file descriptor might cause an IOException if the file is
                    // already closed. Nothing extra is needed to handle this.
                }
            }
        }

        // If the decoding failed, returns null
        return null;
    }

    /**
     * Gets the preferred height for each item in the ListView, in pixels, after accounting for
     * screen density. ImageLoader uses this value to resize thumbnail images to match the ListView
     * item height.
     *
     * @return The preferred height in pixels, based on the current theme.
     */
    public static int getListPreferredItemHeight(Fragment fragment) {
        final TypedValue typedValue = new TypedValue();

        // Resolve list item preferred height theme attribute into typedValue
        fragment.getActivity().getTheme().resolveAttribute(
                android.R.attr.listPreferredItemHeight, typedValue, true);

        // Create a new DisplayMetrics object
        final DisplayMetrics metrics = new android.util.DisplayMetrics();

        // Populate the DisplayMetrics
        fragment.getActivity().getWindowManager().getDefaultDisplay().getMetrics(metrics);

        // Return theme value based on DisplayMetrics
        return (int) typedValue.getDimension(metrics);
    }

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

    public static class Colorizer {
        int bg;
        int fg;

        Colorizer(int bg, int fg) {
            this.bg = bg;
            this.fg = fg;
        }
    }

    public static Colorizer getColorsFor(int index) {
        if (index >= sColorizer.length) {
            index = index % sColorizer.length;
        }
        return sColorizer[index];
    }

    public static Colorizer getDarkColorsFor(int index) {
        if (index >= sColorizerDark.length) {
            index = index % sColorizerDark.length;
        }
        return sColorizerDark[index];
    }

    public static class AccessModeLabel {
        public int nameId;
        public int color;

        public AccessModeLabel(int nameId, int color) {
            this.nameId = nameId;
            this.color = color;
        }
    }

    private static final int COLOR_GREEN_BORDER = 0xFF4CAF50;
    private static final int COLOR_RED_BORDER = 0xFFE57373;
    private static final int COLOR_GRAY_BORDER = 0xFF9E9E9E;
    private static final int COLOR_BLUE_BORDER = 0xFF2196F3;
    private static final int COLOR_YELLOW_BORDER = 0xFFFFCA28;

    public static AccessModeLabel[] accessModeLabels(final Acs acs, final int status) {
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
                result.toArray(new AccessModeLabel[result.size()]) : null;
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

    static class ContactsLoaderCallback implements LoaderManager.LoaderCallbacks<Cursor> {
        private Activity mActivity;
        private ContactsLoaderResultReceiver mReceiver;
        private int mLoaderId = -1;

        public ContactsLoaderCallback(Activity activity, ContactsLoaderResultReceiver receiver) {
            mActivity = activity;
            mReceiver = receiver;
        }

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
        public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
            // This swaps the new cursor into the adapter.
            Log.d(TAG, "delivered cursor with items: " + data.getCount());
            mReceiver.receiveResult(mLoaderId, data);
        }

        @Override
        public void onLoaderReset(Loader<Cursor> loader) {
            // When the loader is being reset, clear the cursor from the adapter. This allows the
            // cursor resources to be freed.
            mReceiver.receiveResult(mLoaderId, null);
        }
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
}
