package co.tinode.tindroid;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.annotation.SuppressLint;
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
import android.graphics.Rect;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
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
import android.widget.Toast;

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
import java.util.Locale;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.preference.PreferenceManager;

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
    private static final String TAG = "UiUtils";
    private static final int AVATAR_SIZE = 128;
    private static final int MAX_BITMAP_SIZE = 1024;
    private static final int MIN_TAG_LENGTH = 4;

    private static final int COLOR_GREEN_BORDER = 0xFF4CAF50;
    private static final int COLOR_RED_BORDER = 0xFFE57373;
    private static final int COLOR_GRAY_BORDER = 0xFF9E9E9E;
    private static final int COLOR_BLUE_BORDER = 0xFF2196F3;
    private static final int COLOR_YELLOW_BORDER = 0xFFFFCA28;
    // Logo LayerDrawable IDs
    private static final int LOGO_LAYER_AVATAR = 0;
    private static final int LOGO_LAYER_ONLINE = 1;
    private static final int LOGO_LAYER_TYPING = 2;
    // If StoredMessage activity is visible, this is the current topic in that activity.
    private static String sVisibleTopic = null;

    static void setupToolbar(final Activity activity, final VxCard pub,
                             final String topicName, final boolean online) {
        if (activity == null || activity.isDestroyed() || activity.isFinishing()) {
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
            avatarDrawable = new RoundImageDrawable(activity.getResources(), avatar);
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
        typing.setBounds(b.right - b.width() / 4, b.bottom - b.height() / 4, b.right, b.bottom);
    }

    @SuppressWarnings("SameParameterValue")
    static Timer toolbarTypingIndicator(final Activity activity, Timer timer, int duration) {
        if (timer != null) {
            timer.cancel();
        }

        final Toolbar toolbar = activity.findViewById(R.id.toolbar);
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
        typing.setBounds(b.right - b.width() / 4, b.bottom - b.height() / 4, b.right, b.bottom);
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

        ((OnlineDrawable) ((LayerDrawable) logo).findDrawableByLayerId(LOGO_LAYER_ONLINE)).setOnline(online);
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

        Intent intent = new Intent(activity, ChatsActivity.class);
        activity.startActivity(intent);
        activity.finish();
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
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
                            tinode.setAutoLoginToken(token);
                            tinode.connect(hostName, tls).getResult();
                            ServerMessage msg = tinode.loginToken(token).getResult();
                            // Logged in successfully. Save refreshed token for future use.
                            accountManager.setAuthToken(account, Utils.TOKEN_TYPE, tinode.getAuthToken());
                            if (msg == null || msg.ctrl.code < 300) {
                                // Logged in successfully.
                                Log.d(TAG, "LoginWithSavedAccount succeeded, sending to contacts");
                                // Go to Contacts
                                launch = new Intent(activity, ChatsActivity.class);
                            } else {
                                Log.d(TAG, "LoginWithSavedAccount failed due to credentials, sending to login");
                                Iterator<String> it = msg.ctrl.getStringIteratorParam("cred");
                                launch.putExtra("credential", it.next());
                            }
                        } catch (IOException ex) {
                            // Login failed due to network error.
                            // If we have UID, go to Contacts, otherwise to Login
                            launch = new Intent(activity, BaseDb.getInstance().isReady() ?
                                    ChatsActivity.class : LoginActivity.class);
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
        if (activity == null || activity.isDestroyed() || activity.isFinishing()) {
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
                    } else {
                        Log.i(TAG, "Toolbar menu is null");
                    }
                    View line = activity.findViewById(R.id.offline_indicator);
                    if (line != null) {
                        line.setVisibility(online ? View.INVISIBLE : View.VISIBLE);
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
    static void requestAvatar(@Nullable Fragment fragment) {
        if (fragment == null) {
            return;
        }

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

    private static Bitmap scaleSquareBitmap(Bitmap bmp) {
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
        return Bitmap.createBitmap(bmp, (width - AVATAR_SIZE) / 2, (height - AVATAR_SIZE) / 2,
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

    private static Bitmap extractBitmap(final Activity activity, final Intent data) {
        try {
            return MediaStore.Images.Media.getBitmap(activity.getContentResolver(),
                    data.getData());
        } catch (IOException | SecurityException ex) {
            Log.w(TAG, "Failed to get bitmap", ex);
            return null;
        }
    }

    private static void acceptAvatar(final ImageView avatar, final Bitmap bmp) {
        avatar.setImageDrawable(new RoundImageDrawable(avatar.getResources(), scaleSquareBitmap(bmp)));
    }

    static void acceptAvatar(final Activity activity, final ImageView avatar, final Intent data) {
        final Bitmap bmp = extractBitmap(activity, data);
        if (bmp == null) {
            Toast.makeText(activity, activity.getString(R.string.image_is_unavailable), Toast.LENGTH_SHORT).show();
            return;
        }
        acceptAvatar(avatar, bmp);
    }

    static Drawable avatarDrawable(Context context, Bitmap bmp, String name, String address) {
        if (bmp != null) {
            return new RoundImageDrawable(context.getResources(), bmp);
        } else {
            LetterTileDrawable drawable = new LetterTileDrawable(context);
            drawable.setContactTypeAndColor(
                    Topic.getTopicTypeByName(address) == Topic.TopicType.P2P ?
                            LetterTileDrawable.TYPE_PERSON : LetterTileDrawable.TYPE_GROUP)
                    .setLetterAndColor(name, address)
                    .setIsCircular(true);
            return drawable;
        }
    }

    /*
     * An ImageLoader object loads and resizes an image in the background and binds it to the
     * each item layout of the ListView. ImageLoader implements memory caching for each image,
     * which substantially improves refreshes of the ListView as the user scrolls through it.
     *
     * http://developer.android.com/training/displaying-bitmaps/
     */
    static ImageLoader getImageLoaderInstance(final Fragment parent) {
        FragmentActivity activity = parent.getActivity();
        if (activity == null) {
            return null;
        }

        ImageLoader il = new ImageLoader(getListPreferredItemHeight(parent),
                activity.getSupportFragmentManager()) {
            @Override
            protected Bitmap processBitmap(Object data) {
                // This gets called in a background thread and passed the data from
                // ImageLoader.loadImage().
                return UiUtils.loadContactPhotoThumbnail(parent,
                        (String) data, getImageSize());
            }
        };
        // Set a placeholder loading image for the image loader
        il.setLoadingImage(activity, R.drawable.ic_person_circle);

        return il;
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
    private static Bitmap loadContactPhotoThumbnail(Fragment fragment, String photoData, int imageSize) {

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
     * Identifies the start of the search string (needle) in the display name (haystack).
     * E.g. If display name was "Adam" and search query was "da" this would
     * return 1.
     *
     * @param haystack The contact display name.
     * @return The starting position of the search string in the display name, 0-based. The
     * method returns -1 if the string is not found in the display name, or if the search
     * string is empty or null.
     */
    static int indexOfSearchQuery(String haystack, String needle) {
        if (!TextUtils.isEmpty(needle)) {
            return haystack.toLowerCase(Locale.getDefault()).indexOf(
                    needle.toLowerCase(Locale.getDefault()));
        }

        return -1;
    }

    /**
     * Gets the preferred height for each item in the ListView, in pixels, after accounting for
     * screen density. ImageLoader uses this value to resize thumbnail images to match the ListView
     * item height.
     *
     * @return The preferred height in pixels, based on the current theme.
     */
    private static int getListPreferredItemHeight(Fragment fragment) {
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

    static void showEditPermissions(final Activity activity, final Topic topic,
                                    @NonNull final String mode,
                                    final String uid, final int what,
                                    String skip) {
        final int[] permissionsMap = new int[]{
                R.string.permission_join,
                R.string.permission_read,
                R.string.permission_write,
                R.string.permission_notifications,
                R.string.permission_approve,
                R.string.permission_share,
                R.string.permission_delete,
                R.string.permission_owner
        };
        final AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        final LayoutInflater inflater = LayoutInflater.from(builder.getContext());
        @SuppressLint("InflateParams") final LinearLayout editor = (LinearLayout) inflater.inflate(R.layout.dialog_edit_permissions, null);
        builder
                .setView(editor)
                .setTitle(R.string.edit_permissions);

        View.OnClickListener checkListener = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                boolean checked = !((CheckedTextView) view).isChecked();
                ((CheckedTextView) view).setChecked(checked);
            }
        };

        for (int i = 0; i < "JRWPASDO".length(); i++) {
            char c = "JRWPASDO".charAt(i);
            if (skip.indexOf(c) >= 0) {
                continue;
            }

            CheckedTextView check = (CheckedTextView) inflater.inflate(R.layout.edit_one_permission,
                    editor, false);
            check.setChecked(mode.indexOf(c) >= 0);
            check.setText(permissionsMap[i]);
            check.setTag(c);
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
                try {
                    PromisedReply<ServerMessage> reply = null;
                    switch (what) {
                        case ACTION_UPDATE_SELF_SUB:
                            //noinspection unchecked
                            reply = topic.updateMode(null, newAcsStr.toString());
                            break;
                        case ACTION_UPDATE_SUB:
                            //noinspection unchecked
                            reply = topic.updateMode(uid, newAcsStr.toString());
                            break;
                        case ACTION_UPDATE_AUTH:
                            //noinspection unchecked
                            reply = topic.updateDefAcs(newAcsStr.toString(), null);
                            break;
                        case ACTION_UPDATE_ANON:
                            //noinspection unchecked
                            reply = topic.updateDefAcs(null, newAcsStr.toString());
                            break;
                        default:
                            Log.w(TAG, "Unknown action " + what);
                    }

                    if (reply != null) {
                        reply.thenApply(
                                new PromisedReply.SuccessListener<ServerMessage>() {
                                    @Override
                                    public PromisedReply<ServerMessage> onSuccess(ServerMessage result) {
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
                                                Log.w(TAG, "Failed", err);
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

    static <T extends Topic<VxCard, PrivateType, ?, ?>> void updateAvatar(final Activity activity,
                                                                          final T topic, final Intent data) {
        Bitmap bmp = UiUtils.extractBitmap(activity, data);
        if (bmp == null) {
            Toast.makeText(activity, activity.getString(R.string.image_is_unavailable), Toast.LENGTH_SHORT).show();
            return;
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
        } catch (Exception ignored) {
            Toast.makeText(activity, R.string.action_failed, Toast.LENGTH_SHORT).show();
        }
    }

    static <T extends Topic<VxCard, PrivateType, ?, ?>> void updateTitle(final Activity activity,
                                                                         T topic, String title, String comment) {
        VxCard pub = null;
        if (title != null) {
            VxCard oldPub = topic.getPub();
            if (oldPub != null && !title.equals(oldPub.fn)) {
                pub = new VxCard();
                pub.fn = title;
            }
        }

        if (comment != null) {
            PrivateType priv = topic.getPriv();
            String oldComment = priv != null ? priv.getComment() : null;
            if (comment.equals(oldComment)) {
                comment = null;
            }
        }

        if (pub != null || comment != null) {
            try {
                PrivateType priv = null;
                if (comment != null) {
                    priv = new PrivateType();
                    priv.setComment(comment);
                }
                topic.setDescription(pub, priv).thenApply(null, new ToastFailureListener(activity));
            } catch (NotConnectedException ignored) {
                Toast.makeText(activity, R.string.no_connection, Toast.LENGTH_SHORT).show();
            } catch (Exception ignored) {
                Toast.makeText(activity, R.string.action_failed, Toast.LENGTH_SHORT).show();
            }
        }
    }

    static void attachMeTopic(final Activity activity, MeTopic.MeListener l) {
        boolean success = false;
        try {
            setProgressIndicator(activity, true);
            Cache.attachMeTopic(l)
                    .thenApply(new PromisedReply.SuccessListener<ServerMessage>() {
                        @Override
                        public PromisedReply<ServerMessage> onSuccess(ServerMessage result) {
                            UiUtils.setProgressIndicator(activity, false);
                            return null;
                        }
                    }, new PromisedReply.FailureListener<ServerMessage>() {
                        @Override
                        public PromisedReply<ServerMessage> onFailure(Exception err) {
                            Log.w(TAG, "Error subscribing to 'me' topic", err);
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
            success = true;
        } catch (NotSynchronizedException ignored) {
        } catch (NotConnectedException ignored) {
            /* offline - ignored */
        } catch (Exception err) {
            Log.i(TAG, "Subscription failed", err);
            Toast.makeText(activity, R.string.action_failed, Toast.LENGTH_LONG).show();
        } finally {
            if (!success) {
                setProgressIndicator(activity, false);
            }
        }
    }

    static String[] parseTags(final String tagList) {
        if (tagList == null) {
            return null;
        }

        String[] rawTags = TextUtils.split(tagList, ",");
        ArrayList<String> tags = new ArrayList<>();
        for (String tag : rawTags) {
            tag = tag.trim();
            if (tag.length() >= MIN_TAG_LENGTH) {
                tags.add(tag);
            }
        }
        if (tags.size() == 0) {
            return null;
        }
        return tags.toArray(new String[]{});
    }

    /**
     * Show or hide progress indicator in the toolbar.
     *
     * @param activity activity making the call.
     * @param active   show when true, hide when false.
     */
    private static void setProgressIndicator(final Activity activity, final boolean active) {
        if (activity.isFinishing() || activity.isDestroyed()) {
            return;
        }

        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // TODO: show progress indicator while loading contacts.
            }
        });
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
                        // TODO: handle non-primary volumes
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
        final String[] projection = {column};
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

        String[] sizes = new String[]{"Bytes", "KB", "MB", "GB", "TB"};
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

    public static class EventListener extends Tinode.EventListener {
        private Activity mActivity;
        private Boolean mConnected;

        EventListener(Activity owner, Boolean connected) {
            super();
            mActivity = owner;
            mConnected = connected;
            setConnectedStatus(mActivity, connected);
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

    public static class AccessModeLabel {
        public int color;
        int nameId;

        AccessModeLabel(int nameId, int color) {
            this.nameId = nameId;
            this.color = color;
        }
    }

    static class ToastFailureListener extends PromisedReply.FailureListener<ServerMessage> {
        private Activity mActivity;

        ToastFailureListener(Activity activity) {
            mActivity = activity;
        }

        @Override
        public PromisedReply<ServerMessage> onFailure(final Exception err) {
            if (mActivity == null || mActivity.isFinishing() || mActivity.isDestroyed()) {
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
}
