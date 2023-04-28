package co.tinode.tindroid;

import android.Manifest;
import android.accounts.Account;
import android.accounts.AccountManager;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.LayerDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Parcelable;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.Base64;
import android.util.Log;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.Button;
import android.widget.CheckedTextView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.squareup.picasso.Picasso;

import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URL;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executors;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.exifinterface.media.ExifInterface;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import androidx.preference.PreferenceManager;
import co.tinode.tindroid.account.ContactsManager;
import co.tinode.tindroid.account.Utils;
import co.tinode.tindroid.db.BaseDb;
import co.tinode.tindroid.media.VxCard;
import co.tinode.tindroid.widgets.LetterTileDrawable;
import co.tinode.tindroid.widgets.OnlineDrawable;
import co.tinode.tindroid.widgets.RoundImageDrawable;

import co.tinode.tindroid.widgets.UrlLayerDrawable;
import co.tinode.tinodesdk.ComTopic;
import co.tinode.tinodesdk.MeTopic;
import co.tinode.tinodesdk.NotConnectedException;
import co.tinode.tinodesdk.PromisedReply;
import co.tinode.tinodesdk.ServerResponseException;
import co.tinode.tinodesdk.Tinode;
import co.tinode.tinodesdk.Topic;
import co.tinode.tinodesdk.model.Acs;
import co.tinode.tinodesdk.model.Credential;
import co.tinode.tinodesdk.model.PrivateType;
import co.tinode.tinodesdk.model.ServerMessage;

/**
 * Static utilities for UI support.
 */
public class UiUtils {
    private static final String TAG = "UiUtils";

    // Default tag parameters
    private static final int DEFAULT_MIN_TAG_LENGTH = 4;
    private static final int DEFAULT_MAX_TAG_LENGTH = 96;
    private static final int DEFAULT_MAX_TAG_COUNT = 16;

    private static final int COLOR_GREEN_BORDER = 0xFF4CAF50;
    private static final int COLOR_RED_BORDER = 0xFFE57373;
    private static final int COLOR_GRAY_BORDER = 0xFF9E9E9E;
    // private static final int COLOR_BLUE_BORDER = 0xFF2196F3;
    private static final int COLOR_YELLOW_BORDER = 0xFFFFCA28;
    // Logo LayerDrawable IDs
    private static final int LOGO_LAYER_AVATAR = 0;
    private static final int LOGO_LAYER_ONLINE = 1;
    private static final int LOGO_LAYER_TYPING = 2;
    // If StoredMessage activity is visible, this is the current topic in that activity.
    private static String sVisibleTopic = null;

    private static final String PREF_FIRST_RUN = "firstRun";

    public enum MsgAction {
        NONE, REPLY, FORWARD, EDIT
    }

    static void setupToolbar(final Activity activity, final VxCard pub,
                             final String topicName, final boolean online, final Date lastSeen, boolean deleted) {
        if (activity == null || activity.isDestroyed() || activity.isFinishing()) {
            return;
        }

        final Toolbar toolbar = activity.findViewById(R.id.toolbar);
        if (toolbar == null) {
            return;
        }

        activity.runOnUiThread(() -> {
            if (!TextUtils.isEmpty(topicName)) {
                Boolean showOnline = online;
                final String title = pub != null && pub.fn != null ?
                        pub.fn : activity.getString(R.string.placeholder_contact_title);
                toolbar.setTitle(title);
                if (ComTopic.isChannel(topicName)) {
                    showOnline = null;
                    toolbar.setSubtitle(R.string.channel);
                } else if (deleted) {
                    showOnline = null;
                    toolbar.setSubtitle(R.string.deleted);
                } else if (online) {
                    toolbar.setSubtitle(activity.getString(R.string.online_now));
                } else if (lastSeen != null) {
                    toolbar.setSubtitle(relativeDateFormat(activity, lastSeen));
                } else {
                    toolbar.setSubtitle(null);
                }
                constructToolbarLogo(activity, pub, topicName, showOnline, deleted);
            } else {
                toolbar.setTitle(R.string.app_name);
                toolbar.setSubtitle(null);
                toolbar.setLogo(null);
            }
            toolbar.invalidate();
        });
    }

    // Constructs LayerDrawable with the following layers:
    // 0. [Avatar or LetterTileDrawable]
    // 1. [Online indicator]
    // 2. [Typing indicator]
    private static void constructToolbarLogo(final Activity activity, final VxCard pub,
                                             String uid, Boolean online, boolean deleted) {
        final Toolbar toolbar = activity.findViewById(R.id.toolbar);
        if (toolbar == null) {
            return;
        }

        Resources res = activity.getResources();
        ArrayList<Drawable> drawables = new ArrayList<>();
        AnimationDrawable typing = null;
        Bitmap bmp = null;
        String title = null;
        String ref = null;
        if (pub != null) {
            bmp = pub.getBitmap();
            title = pub.fn;
            ref = pub.getPhotoRef();
        }
        if (ref == null) {
            // Local resource.
            drawables.add(avatarDrawable(activity, bmp, title, uid, deleted));
        } else {
            // Remote resource. Create a transparent placeholder layer.
            drawables.add(new ColorDrawable(0x00000000));
        }

        if (online != null) {
            drawables.add(new OnlineDrawable(online));

            typing = (AnimationDrawable) ResourcesCompat.getDrawable(res, R.drawable.typing_indicator, null);
            if (typing != null) {
                typing.setOneShot(false);
                typing.setVisible(false, true);
                typing.setAlpha(0);
                drawables.add(typing);
            }
        }

        UrlLayerDrawable layers = new UrlLayerDrawable(drawables.toArray(new Drawable[]{}));
        layers.setId(0, LOGO_LAYER_AVATAR);

        if (ref != null) {
            // Use thumbnail preview if available, otherwise use default gray disk.
            Drawable placeholder = bmp != null ?
                    (new RoundImageDrawable(res, bmp)) :
                    ResourcesCompat.getDrawable(res, R.drawable.disk, null);
            layers.setUrlByLayerId(res, LOGO_LAYER_AVATAR, ref, placeholder,
                    R.drawable.ic_broken_image_round);
        }

        if (online != null) {
            layers.setId(1, LOGO_LAYER_ONLINE);
            if (typing != null) {
                layers.setId(2, LOGO_LAYER_TYPING);
            }
        }

        toolbar.setLogo(layers);
        Rect b = toolbar.getLogo().getBounds();
        if (!b.isEmpty() && typing != null) {
            typing.setBounds(b.right - b.width() / 4, b.bottom - b.height() / 4, b.right, b.bottom);
        }
    }

    // Run (duration>0) or stop (duration<=0) typing... animation.
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

        Rect b = logo.getBounds();
        if (b.isEmpty()) {
            return null;
        }

        final AnimationDrawable typing = (AnimationDrawable) ((LayerDrawable) logo)
                .findDrawableByLayerId(LOGO_LAYER_TYPING);
        if (typing == null) {
            return null;
        }

        if (duration <= 0) {
            // Stop the animation and return.
            typing.setVisible(false, true);
            typing.setAlpha(0);
            return null;
        }

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

                activity.runOnUiThread(() -> {
                    typing.setVisible(false, true);
                    typing.setAlpha(0);
                });
            }
        }, duration);
        return timer;
    }

    static void toolbarSetOnline(final Activity activity, boolean online, Date lastSeen) {
        final Toolbar toolbar = activity.findViewById(R.id.toolbar);
        if (toolbar == null) {
            return;
        }

        Drawable logo = toolbar.getLogo();
        if (logo instanceof LayerDrawable) {
            Drawable onlineDisk = ((LayerDrawable) logo).findDrawableByLayerId(LOGO_LAYER_ONLINE);
            if (onlineDisk instanceof OnlineDrawable) {
                ((OnlineDrawable) onlineDisk).setOnline(online);
            }
        }
        if (online) {
            toolbar.setSubtitle(null);
        } else if (lastSeen != null) {
            toolbar.setSubtitle(relativeDateFormat(activity, lastSeen));
        }
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
    static void onLoginSuccess(Activity activity, final Button button, final String uid) {
        if (button != null) {
            activity.runOnUiThread(() -> button.setEnabled(true));
        }

        Account acc = Utils.getSavedAccount(AccountManager.get(activity), uid);
        if (acc != null) {
            requestImmediateContactsSync(acc);
            ContentResolver.setSyncAutomatically(acc, Utils.SYNC_AUTHORITY, true);
            TindroidApp.startWatchingContacts(activity, acc);
        }

        Intent intent = new Intent(activity, ChatsActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        activity.startActivity(intent);
        activity.finish();
    }

    static void doLogout(Context context) {
        CallManager.unregisterCallingAccount();
        TindroidApp.stopWatchingContacts();
        Cache.invalidate();

        Intent intent = new Intent(context, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        context.startActivity(intent);
    }

    static synchronized void requestImmediateContactsSync(Account acc) {
        Bundle bundle = new Bundle();
        bundle.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);
        bundle.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true);
        ContentResolver.requestSync(acc, Utils.SYNC_AUTHORITY, bundle);
        ContentResolver.setSyncAutomatically(acc, Utils.SYNC_AUTHORITY, true);
    }

    static boolean isPermissionGranted(Context context, String permission) {
        return ActivityCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED;
    }

    static LinkedList<String> getMissingPermissions(Context context, String[] permissions) {
        LinkedList<String> missing = new LinkedList<>();
        for (String permission: permissions) {
            if (ActivityCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                missing.add(permission);
            }
        }
        return missing;
    }

    static void onContactsPermissionsGranted(Activity activity) {
        // Run in background.
        Executors.newSingleThreadExecutor().execute(() -> {
            Account acc = Utils.getSavedAccount(AccountManager.get(activity), Cache.getTinode().getMyId());
            if (acc == null) {
                return;
            }
            Tinode tinode = Cache.getTinode();
            Collection<ComTopic<VxCard>> topics = tinode.getFilteredTopics(Topic::isP2PType);
            ContactsManager.updateContacts(activity, acc, tinode, topics);
            TindroidApp.startWatchingContacts(activity, acc);
        });
    }

    // Creates or updates the Android account associated with the given UID.
    static void updateAndroidAccount(final Context context, final String uid, final String secret,
                                     final String token, final Date tokenExpires) {
        final AccountManager am = AccountManager.get(context);
        final Account acc = Utils.createAccount(uid);
        // It's OK to call even if the account already exists.
        am.addAccountExplicitly(acc, secret, null);
        am.notifyAccountAuthenticated(acc);
        if (!TextUtils.isEmpty(token)) {
            am.setAuthToken(acc, Utils.TOKEN_TYPE, token);
            am.setUserData(acc, Utils.TOKEN_EXPIRATION_TIME, String.valueOf(tokenExpires.getTime()));
        }
    }

    private static void setConnectedStatus(final Activity activity, final boolean online) {
        // Connected status is disabled for production builds.
        if (!BuildConfig.DEBUG) {
            return;
        }

        if (activity == null || activity.isDestroyed() || activity.isFinishing()) {
            return;
        }

        activity.runOnUiThread(() -> {
            final Toolbar toolbar = activity.findViewById(R.id.toolbar);
            if (toolbar != null) {
                Menu menu = toolbar.getMenu();
                if (menu != null) {
                    menu.setGroupVisible(R.id.offline, !online);
                }
                View line = activity.findViewById(R.id.offline_indicator);
                if (line != null) {
                    line.setVisibility(online ? View.INVISIBLE : View.VISIBLE);
                }
            }
        });
    }

    // Date formatter for messages
    @NonNull
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
        return "unknown";
    }

    // Time formatter for messages.
    @NonNull
    static String timeOnly(Context context, Date date) {
        if (date != null) {
            return DateFormat.getTimeInstance(DateFormat.SHORT).format(date.getTime());
        }
        return context.getString(R.string.unknown);
    }

    // Date format relative to present.
    @NonNull
    private static CharSequence relativeDateFormat(Context context, Date then) {
        if (then == null) {
            return context.getString(R.string.never);
        }
        long thenMillis = then.getTime();
        if (thenMillis == 0) {
            return context.getString(R.string.never);
        }
        long nowMillis = System.currentTimeMillis();
        if (nowMillis - thenMillis < DateUtils.MINUTE_IN_MILLIS) {
            return context.getString(R.string.just_now);
        }

        return DateUtils.getRelativeTimeSpanString(thenMillis, nowMillis,
                DateUtils.MINUTE_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_ALL);
    }

    // Convert milliseconds to '00:00' format.
    @NonNull
    static String millisToTime(int millis) {
        StringBuilder sb = new StringBuilder();
        float duration = millis / 1000f;
        int min = (int) Math.floor(duration / 60f);
        if (min < 10) {
            sb.append("0");
        }
        sb.append(min).append(":");
        int sec = (int) (duration % 60f);
        if (sec < 10) {
            sb.append("0");
        }
        return sb.append(sec).toString();
    }

    // Returns true if two timestamps are on the same day (ignoring the time part) or both are null.
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    static boolean isSameDate(@Nullable Date one, @Nullable Date two) {
        if (one == two) {
            return true;
        }
        if (one == null || two == null) {
            return false;
        }

        return (one.getDate() == two.getDate()) &&
                (one.getMonth() == two.getMonth()) &&
                (one.getYear() == two.getYear());
    }

    static Intent avatarSelectorIntent(@NonNull final Activity activity,
                                           @Nullable ActivityResultLauncher<String[]> missingPermissionsLauncher) {
        if (missingPermissionsLauncher != null) {
            LinkedList<String> request = getMissingPermissions(activity,
                    new String[]{Manifest.permission.CAMERA, Manifest.permission.READ_EXTERNAL_STORAGE});
            if (!request.isEmpty()) {
                missingPermissionsLauncher.launch(request.toArray(new String[]{}));
                return null;
            }
        }

        // Option 1: take a photo.
        List<Intent> cameraIntents = buildIntentList(activity, new Intent(MediaStore.ACTION_IMAGE_CAPTURE));

        // Option 2: pick image from the gallery.
        Intent galleryIntent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        galleryIntent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/jpeg", "image/png", "image/gif"});

        // Pack two intents into a chooser.
        Intent chooserIntent = Intent.createChooser(galleryIntent, activity.getString(R.string.select_image));
        if (!cameraIntents.isEmpty()) {
            chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, cameraIntents.toArray(new Parcelable[]{}));
        }

        return chooserIntent;
    }

    // Given an intent, find all packages which support this intent and build a list with intent
    // for each of the found packages.
    private static List<Intent> buildIntentList(Context context, Intent intent) {
        List<Intent> list = new ArrayList<>();
        List<ResolveInfo> resInfo = context.getPackageManager().queryIntentActivities(intent, PackageManager.MATCH_ALL);
        for (ResolveInfo resolveInfo : resInfo) {
            String packageName = resolveInfo.activityInfo.packageName;
            Intent targetedIntent = new Intent(intent);
            targetedIntent.setPackage(packageName);
            list.add(targetedIntent);
        }
        return list;
    }

    static ActivityResultLauncher<Intent> avatarPickerLauncher(@NonNull Fragment fragment,
                                                               @NonNull AvatarPreviewer previewer) {
        return fragment.registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result == null || result.getResultCode() != Activity.RESULT_OK) {
                return;
            }

            final Intent data = result.getData();
            if (data == null) {
                return;
            }

            final Bundle args = new Bundle();
            Bitmap thumbnail = data.getParcelableExtra("data");
            Uri uri = data.getData();
            if (thumbnail != null) {
                // Thumbnail from the camera.
                // TODO: maybe take the full-size picture and scale it down instead of using the thumbnail.
                args.putParcelable(AttachmentHandler.ARG_SRC_BITMAP, thumbnail);
            } else if (uri != null){
                // Image from the gallery.
                args.putParcelable(AttachmentHandler.ARG_LOCAL_URI, data.getData());
            }

            // Show avatar preview.
            if (!args.isEmpty()) {
                previewer.showAvatarPreview(args);
            }
        });
    }

    interface AvatarPreviewer {
        void showAvatarPreview(Bundle args);
    }

    /**
     * Ensure that the bitmap is square and no larger than the given max size.
     * @param bmp       bitmap to scale
     * @param size   maximum linear size of the bitmap.
     * @return scaled bitmap or original, it it does not need ot be cropped or scaled.
     */
    @NonNull
    public static Bitmap scaleSquareBitmap(@NonNull Bitmap bmp, int size) {
        // Sanity check
        size = Math.min(size, Const.MAX_BITMAP_SIZE);

        int width = bmp.getWidth();
        int height = bmp.getHeight();

        // Does it need to be scaled down?
        if (width > size && height > size) {
            // Scale down.
            if (width > height) /* landscape */ {
                width = width * size / height;
                height = size;
            } else /* portrait or square */ {
                height = height * size / width;
                width = size;
            }
            // Scale down.
            bmp = Bitmap.createScaledBitmap(bmp, width, height, true);
        }
        size = Math.min(width, height);

        if (width != height) {
            // Bitmap is not square. Chop the square from the middle.
            bmp = Bitmap.createBitmap(bmp, (width - size) / 2, (height - size) / 2,
                    size, size);
        }

        return bmp;
    }

    /**
     * Scale bitmap down to be under certain liner dimensions.
     *
     * @param bmp       bitmap to scale.
     * @param maxWidth  maximum allowed bitmap width.
     * @param maxHeight maximum allowed bitmap height.
     * @param upscale enable increasing size of the image (up to 10x).
     * @return scaled bitmap or original, it it does not need to be scaled.
     */
    @NonNull
    public static Bitmap scaleBitmap(@NonNull Bitmap bmp, final int maxWidth, final int maxHeight,
                                     final boolean upscale) {
        int width = bmp.getWidth();
        int height = bmp.getHeight();

        // Calculate scaling factor.
        float factor = Math.max((float) width / maxWidth, upscale ? 0.1f : 1.0f);
        factor = Math.max((float) height / maxHeight, factor);

        // Scale down.
        if (upscale || factor > 1.0) {
            height /= factor;
            width /= factor;
            return Bitmap.createScaledBitmap(bmp, width, height, true);
        }
        return bmp;
    }

    @NonNull
    static Bitmap rotateBitmap(@NonNull Bitmap bmp, int orientation) {
        Matrix matrix = new Matrix();
        switch (orientation) {
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                matrix.setScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                matrix.setRotate(180);
                break;
            case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                matrix.setRotate(180);
                matrix.postScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_TRANSPOSE:
                matrix.setRotate(90);
                matrix.postScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_ROTATE_90:
                matrix.setRotate(90);
                break;
            case ExifInterface.ORIENTATION_TRANSVERSE:
                matrix.setRotate(-90);
                matrix.postScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                matrix.setRotate(-90);
                break;
            case ExifInterface.ORIENTATION_NORMAL:
            default:
                return bmp;
        }

        try {
            Bitmap rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), matrix, true);
            bmp.recycle();
            return rotated;
        } catch (OutOfMemoryError ex) {
            Log.e(TAG, "Out of memory while rotating bitmap");
            return bmp;
        }
    }

    static void acceptAvatar(final Activity activity, final ImageView avatarContainer, final Bitmap avatar) {
        if (activity == null || avatarContainer == null) {
            return;
        }

        if (avatar == null) {
            Toast.makeText(activity, activity.getString(R.string.image_is_unavailable), Toast.LENGTH_SHORT).show();
            return;
        }

        avatarContainer.setImageDrawable(new RoundImageDrawable(avatarContainer.getResources(),
                scaleSquareBitmap(avatar, Const.MAX_AVATAR_SIZE)));
    }

    // Construct avatar from VxCard and set it to the provided ImageView.
    static void setAvatar(ImageView avatarView, VxCard pub, String address, boolean disabled) {
        Bitmap avatar = null;
        String ref = null;
        String fullName = null;
        if (pub != null) {
            avatar = pub.getBitmap();
            fullName = pub.fn;
            ref = pub.getPhotoRef();
        }

        Drawable local = avatarDrawable(avatarView.getContext(), avatar, fullName, address, disabled);
        if (ref != null) {
            Picasso
                    .get()
                    .load(ref)
                    .resize(Const.MAX_AVATAR_SIZE, Const.MAX_AVATAR_SIZE)
                    .placeholder(local)
                    .error(R.drawable.ic_broken_image_round)
                    .into(avatarView);
        } else {
            avatarView.setImageDrawable(local);
        }

        if (disabled) {
            // Make avatar grayscale
            ColorMatrix matrix = new ColorMatrix();
            matrix.setSaturation(0);
            avatarView.setColorFilter(new ColorMatrixColorFilter(matrix));
        } else {
            avatarView.setColorFilter(null);
        }
    }

    // Construct avatar drawable: use bitmap if it is not null,
    // otherwise use name & address to create a LetterTileDrawable.
    public static Drawable avatarDrawable(Context context, Bitmap bmp, String name, String address, boolean disabled) {
        if (bmp != null) {
            Drawable drawable = new RoundImageDrawable(context.getResources(), bmp);
            if (disabled) {
                // Make avatar grayscale
                ColorMatrix matrix = new ColorMatrix();
                matrix.setSaturation(0);
                drawable.setColorFilter(new ColorMatrixColorFilter(matrix));
            }
            return drawable;
        } else {
            LetterTileDrawable drawable = new LetterTileDrawable(context);
            drawable.setContactTypeAndColor(
                    Topic.isP2PType(address) ?
                            LetterTileDrawable.ContactType.PERSON :
                            LetterTileDrawable.ContactType.GROUP, disabled)
                    .setLetterAndColor(name, address, disabled)
                    .setIsCircular(true);
            return drawable;
        }
    }

    @NonNull
    static ByteArrayInputStream bitmapToStream(@NonNull Bitmap bmp, String mimeType) {
        return new ByteArrayInputStream(bitmapToBytes(bmp, mimeType));
    }

    @NonNull
    public static byte[] bitmapToBytes(@NonNull Bitmap bmp, String mimeType) {
        Bitmap.CompressFormat fmt;
        if ("image/jpeg".equals(mimeType)) {
            fmt = Bitmap.CompressFormat.JPEG;
        } else {
            fmt = Bitmap.CompressFormat.PNG;
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        bmp.compress(fmt, 70, bos);
        byte[] bits = bos.toByteArray();
        try {
            bos.close();
        } catch (IOException ignored) {
        }

        return bits;
    }

    /**
     * Convert drawable to bitmap.
     *
     * @param drawable vector drawable to convert to bitmap
     * @return bitmap extracted from the drawable.
     */
    public static Bitmap bitmapFromDrawable(Drawable drawable) {
        if (drawable instanceof BitmapDrawable) {
            return ((BitmapDrawable) drawable).getBitmap();
        }

        Bitmap bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(),
                drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);

        return bitmap;
    }

    // Create avatar bitmap: try to use ref first, then in-band bits, the letter tile, then placeholder.
    // Do NOT run on UI thread: it will throw.
    public static Bitmap avatarBitmap(Context context, VxCard pub, Topic.TopicType tp, String id, int size) {
        Bitmap bitmap = null;
        String fullName = null;
        if (pub != null) {
            fullName = pub.fn;
            String ref = pub.getPhotoRef();
            if (ref != null) {
                try {
                    bitmap = Picasso.get()
                            .load(ref)
                            .resize(size, size).get();
                } catch (IOException ex) {
                    Log.w(TAG, "Failed to load avatar", ex);
                }
            } else {
                bitmap = pub.getBitmap();
            }
        }

        if (bitmap != null) {
            bitmap = Bitmap.createScaledBitmap(bitmap, size, size, false);
        } else {
            bitmap = new LetterTileDrawable(context)
                    .setContactTypeAndColor(tp == Topic.TopicType.GRP ?
                            LetterTileDrawable.ContactType.GROUP :
                            LetterTileDrawable.ContactType.PERSON, false)
                    .setLetterAndColor(fullName, id, false)
                    .getSquareBitmap(size);
        }

        return bitmap;
    }

    // Creates LayerDrawable of the right size with gray background and 'fg' in the middle.
    // Used in chat bubbled to generate placeholder and error images for Picasso.
    public static Drawable getPlaceholder(@NonNull Context ctx, @Nullable Drawable fg, @Nullable Drawable bkg,
                                          int width, int height) {
        Drawable filter;
        if (bkg == null) {
            // Uniformly gray background.
            bkg = ResourcesCompat.getDrawable(ctx.getResources(), R.drawable.placeholder_image_bkg, null);

            // Transparent filter.
            filter = new ColorDrawable(0x00000000);
        } else {
            // Translucent filter.
            filter = new ColorDrawable(0xCCCCCCCC);
        }


        // Move foreground to the center of the drawable.
        if (fg == null) {
            // Transparent drawable.
            fg = new ColorDrawable(0x00000000);
        } else {
            int fgWidth = fg.getIntrinsicWidth();
            int fgHeight = fg.getIntrinsicHeight();
            int dx = Math.max((width - fgWidth) / 2, 0);
            int dy = Math.max((height - fgHeight) / 2, 0);
            fg = new InsetDrawable(fg, dx, dy, dx, dy);
        }

        final LayerDrawable result = new LayerDrawable(new Drawable[]{bkg, filter, fg});
        //noinspection ConstantConditions
        bkg.setBounds(0, 0, width, height);
        result.setBounds(0, 0, width, height);

        return result;
    }

    public static void setMessageStatusIcon(ImageView holder, int status, int read, int recv) {
        if (status <= BaseDb.Status.SENDING.value) {
            holder.setImageResource(R.drawable.ic_schedule);
        } else if (status == BaseDb.Status.FAILED.value) {
            holder.setImageResource(R.drawable.ic_warning);
        } else {
            if (read > 0) {
                holder.setImageResource(R.drawable.ic_done_all2);
            } else if (recv > 0) {
                holder.setImageResource(R.drawable.ic_done_all);
            } else {
                holder.setImageResource(R.drawable.ic_done);
            }
        }
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

    static AccessModeLabel[] accessModeLabels(final Acs acs, final BaseDb.Status status) {
        ArrayList<AccessModeLabel> result = new ArrayList<>(2);
        if (acs != null) {
            if (acs.isModeDefined()) {
                if (!acs.isJoiner() || (!acs.isWriter() && !acs.isReader())) {
                    result.add(new AccessModeLabel(R.string.modeBlocked, COLOR_RED_BORDER));
                } else if (acs.isOwner()) {
                    result.add(new AccessModeLabel(R.string.modeOwner, COLOR_GREEN_BORDER));
                } else if (acs.isApprover()) {
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
                }
            }
        }
        if (status == BaseDb.Status.QUEUED) {
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
        @SuppressLint("InflateParams")
        final LinearLayout editor = (LinearLayout) inflater.inflate(R.layout.dialog_edit_permissions, null);
        builder
                .setView(editor)
                .setTitle(R.string.edit_permissions);

        View.OnClickListener checkListener = view -> {
            boolean checked = !((CheckedTextView) view).isChecked();
            ((CheckedTextView) view).setChecked(checked);
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

        builder.setPositiveButton(android.R.string.ok, (dialog, which) -> {
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

            PromisedReply<ServerMessage> reply = null;
            switch (what) {
                case Const.ACTION_UPDATE_SELF_SUB:
                    //noinspection unchecked
                    reply = topic.updateMode(null, newAcsStr.toString());
                    break;
                case Const.ACTION_UPDATE_SUB:
                    //noinspection unchecked
                    reply = topic.updateMode(uid, newAcsStr.toString());
                    break;
                case Const.ACTION_UPDATE_AUTH:
                    //noinspection unchecked
                    reply = topic.updateDefAcs(newAcsStr.toString(), null);
                    break;
                case Const.ACTION_UPDATE_ANON:
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

                                activity.runOnUiThread(() -> {
                                    Log.w(TAG, "Failed", err);
                                    Toast.makeText(activity, R.string.action_failed, Toast.LENGTH_SHORT).show();
                                });
                                return null;
                            }
                        });
            }
        });
        builder.setNegativeButton(android.R.string.cancel, null);
        builder.show();
    }

    /**
     * Send request to server with a new avatar after optionally uploading the avatar if required.
     * @param topic topic to update
     * @param bmp new avatar
     * @param <T> type of the topic
     * @return result of the request to the server.
     */
    @SuppressWarnings("UnusedReturnValue")
    static <T extends Topic<VxCard, ?, ?, ?>>
    PromisedReply<ServerMessage> updateAvatar(final T topic, Bitmap bmp) {
        final VxCard pub;
        if (topic.getPub() != null) {
            pub = topic.getPub().copy();
        } else {
            pub = new VxCard();
        }

        return AttachmentHandler.uploadAvatar(pub, bmp, topic.getName())
                .thenApply(new PromisedReply.SuccessListener<ServerMessage>() {
            @Override
            public PromisedReply<ServerMessage> onSuccess(ServerMessage result) {
                String[] attachments = null;
                if (pub.isPhotoRef()) {
                    attachments = pub.getPhotoRefs();
                }
                return topic.setDescription(pub, null, attachments);
            }
        });
    }

    static <T extends Topic<VxCard, PrivateType, ?, ?>>
    PromisedReply<ServerMessage> updateTopicDesc(T topic, String title, String subtitle, String description) {
        VxCard oldPub = topic.getPub();
        VxCard pub = null;
        if (!TextUtils.isEmpty(title)) {
            if (title.length() > Const.MAX_TITLE_LENGTH) {
                title = title.substring(0, Const.MAX_TITLE_LENGTH);
            }
            if (oldPub != null && !stringsEqual(title, oldPub.fn)) {
                pub = new VxCard();
                pub.fn = title;
            }
        }

        if (description != null) {
            if (description.length() > Const.MAX_DESCRIPTION_LENGTH) {
                description = description.substring(0, Const.MAX_DESCRIPTION_LENGTH);
            }
            String oldNote = oldPub != null ? oldPub.note : null;
            if (!stringsEqual(description, oldNote)) {
                if (pub == null) {
                    pub = new VxCard();
                }
                pub.note = description.equals("") ? Tinode.NULL_VALUE : description;
            }
        }

        PrivateType priv = null;
        if (subtitle != null) {
            if (subtitle.length() > Const.MAX_TITLE_LENGTH) {
                subtitle = subtitle.substring(0, Const.MAX_TITLE_LENGTH);
            }
            PrivateType oldPriv = topic.getPriv();
            String oldComment = oldPriv != null ? oldPriv.getComment() : null;
            if (!stringsEqual(subtitle, oldComment)) {
                priv = new PrivateType();
                priv.setComment(subtitle);
            }
        }

        if (pub != null || priv != null) {
            return topic.setDescription(pub, priv, null);
        }
        return new PromisedReply<>((ServerMessage) null);
    }

    static boolean attachMeTopic(final Activity activity, final MeEventListener l) {
        Tinode tinode = Cache.getTinode();
        if (!tinode.isAuthenticated()) {
            // If connection is not ready, wait for completion. This method will be called again
            // from the onLogin callback;
            Cache.getTinode().reconnectNow(true, false, false);
            return false;
        }

        // If connection exists attachMeTopic returns resolved promise.
        Cache.attachMeTopic(l).thenCatch(new PromisedReply.FailureListener<ServerMessage>() {
            @Override
            public PromisedReply<ServerMessage> onFailure(Exception err) {
                Log.w(TAG, "Error subscribing to 'me' topic", err);
                l.onSubscriptionError(err);
                if (err instanceof ServerResponseException) {
                    ServerResponseException sre = (ServerResponseException) err;
                    int errCode = sre.getCode();
                    // 401: attempt to subscribe to 'me' happened before login, do not log out.
                    // 403: Does not apply to 'me' subscription.
                    if (errCode == 404) {
                        doLogout(activity);
                        activity.finish();
                    } else if (errCode == 502 && "cluster unreachable".equals(sre.getMessage())) {
                        // Must reset connection.
                        Cache.getTinode().reconnectNow(false, true, false);
                    }
                }
                return null;
            }
        });

        return true;
    }

    // Parse comma separated list of possible quoted string into an array.
    static String[] parseTags(final String tagList) {
        if (TextUtils.isEmpty(tagList)) {
            return null;
        }

        ArrayList<String> tags = new ArrayList<>();
        int start = 0;
        final Tinode tinode = Cache.getTinode();
        final long maxTagCount = tinode.getServerLimit(Tinode.MAX_TAG_COUNT, DEFAULT_MAX_TAG_COUNT);
        final long maxTagLength = tinode.getServerLimit(Tinode.MAX_TAG_LENGTH, DEFAULT_MAX_TAG_LENGTH);
        final long minTagLength = tinode.getServerLimit(Tinode.MIN_TAG_LENGTH, DEFAULT_MIN_TAG_LENGTH);

        final int length = tagList.length();
        boolean quoted = false;
        for (int idx = 0; idx < length && tags.size() < maxTagCount; idx++) {
            if (tagList.charAt(idx) == '\"') {
                // Toggle 'inside of quotes' state.
                quoted = !quoted;
            }

            String tag;
            if (tagList.charAt(idx) == ',' && !quoted) {
                tag = tagList.substring(start, idx);
                start = idx + 1;
            } else if (idx == length - 1) {
                // Last char
                tag = tagList.substring(start);
            } else {
                continue;
            }

            tag = tag.trim();
            // Remove possible quotes.
            if (tag.length() > 1 && tag.charAt(0) == '\"' && tag.charAt(tag.length() - 1) == '\"') {
                tag = tag.substring(1, tag.length() - 1).trim();
            }
            if (tag.length() >= minTagLength && tag.length() <= maxTagLength) {
                tags.add(tag);
            }
        }

        if (tags.size() == 0) {
            return null;
        }

        return tags.toArray(new String[]{});
    }

    // Find path to content: DocumentProvider, DownloadsProvider, MediaProvider, MediaStore, File.
    static String getContentPath(@NonNull Context context, @NonNull Uri uri) {
        // DocumentProvider
        if (DocumentsContract.isDocumentUri(context, uri)) {
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
                        break;
                    }
                    case "com.android.providers.downloads.documents": {
                        // DownloadsProvider
                        if (docId.startsWith("raw:")) {
                            // "raw:/storage/emulated/0/Download/app-debug.apk". Just return the path without 'raw:'.
                            return docId.substring(4);
                        }

                        long id;
                        try {
                            id = Long.parseLong(docId);
                        } catch (NumberFormatException e) {
                            Log.w(TAG, "Failed to parse document ID: " + docId);
                            return null;
                        }

                        // Possible locations of downloads directory.
                        String[] contentUriPrefixes = new String[]{
                                "content://downloads/public_downloads",
                                "content://downloads/my_downloads",
                                "content://downloads/all_downloads"
                        };

                        for (String uriPrefix : contentUriPrefixes) {
                            Uri contentUri = ContentUris.withAppendedId(Uri.parse(uriPrefix), id);
                            String path = getResolverData(context, contentUri, null, null);
                            if (path != null) {
                                return path;
                            }
                        }
                        return null;

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
                        if (contentUri != null) {
                            final String selection = "_id=?";
                            final String[] selectionArgs = new String[]{split[1]};
                            return getResolverData(context, contentUri, selection, selectionArgs);
                        } else {
                            Log.i(TAG, "Unknown MediaProvider type " + type);
                        }
                    }
                    default:
                        Log.i(TAG, "Unknown content authority " + uri.getAuthority());
                }
            } else {
                Log.i(TAG, "URI has no content authority " + uri);
            }
        } else if ("content".equalsIgnoreCase(uri.getScheme())) {
            // MediaStore (and general)
            // Return the remote address
            if ("com.google.android.apps.photos.content".equals(uri.getAuthority())) {
                return uri.getLastPathSegment();
            }
            return getResolverData(context, uri, null, null);
        } else if ("file".equalsIgnoreCase(uri.getScheme())) {
            // File
            return uri.getPath();
        }
        return null;
    }

    private static String getResolverData(Context context, @NonNull Uri uri, String selection, String[] selectionArgs) {
        final String column = MediaStore.Files.FileColumns.DATA;
        final String[] projection = {column};
        try {
            try (Cursor cursor = context.getContentResolver().query(uri, projection,
                    selection, selectionArgs, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    final int index = cursor.getColumnIndex(column);
                    return index >= 0 ? cursor.getString(index) : null;
                }
            }
        } catch (SecurityException | IllegalArgumentException ex) {
            Log.w(TAG, "Failed to read resolver data", ex);
        }
        return null;
    }

    public static String bytesToHumanSize(long bytes) {
        if (bytes <= 0) {
            // 0x202F - narrow non-breaking space.
            return "0\u202FBytes";
        }

        String[] sizes = new String[]{"Bytes", "KB", "MB", "GB", "TB"};
        int bucket = (63 - Long.numberOfLeadingZeros(bytes)) / 10;
        double count = bytes / Math.pow(1024, bucket);
        int roundTo = bucket > 0 ? (count < 3 ? 2 : (count < 30 ? 1 : 0)) : 0;
        NumberFormat fmt = DecimalFormat.getInstance();
        fmt.setMaximumFractionDigits(roundTo);
        return fmt.format(count) + "\u202F" + sizes[bucket];
    }

    @Nullable
    static Fragment getVisibleFragment(@NonNull FragmentManager fm) {
        List<Fragment> fragments = fm.getFragments();
        for (Fragment f : fragments) {
            if (f.isVisible()) {
                return f;
            }
        }
        return null;
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

    static Credential parseCredential(String cred) {
        if (Patterns.PHONE.matcher(cred).matches()) {
            // Looks like a phone number.
            return new Credential(Credential.METH_PHONE, cred);
        }

        // Not a phone number. Try parsing as email.
        if (Patterns.EMAIL_ADDRESS.matcher(cred).matches()) {
            return new Credential(Credential.METH_EMAIL, cred);
        }

        return null;
    }

    public static int parseSeqReference(String ref) {
        if (TextUtils.isEmpty(ref)) {
            return 0;
        }

        try {
            if (ref.length() > 1 && ref.charAt(0) == ':') {
                return Integer.parseInt(ref.substring(1));
            }
            return Integer.parseInt(ref);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    // The same as TextUtils.equals except null == "".
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    static boolean stringsEqual(CharSequence a, CharSequence b) {
        if (a == b) {
            return true;
        }

        if (a != null && b != null) {
            int length = a.length();
            if (length != b.length()) {
                return false;
            }
            if (a instanceof String && b instanceof String) {
                return a.equals(b);
            }
            for (int i = 0; i < length; i++) {
                if (a.charAt(i) != b.charAt(i)) {
                    return false;
                }
            }
            return true;
        }

        if (a == null) {
            return b.length() == 0;
        }
        return a.length() == 0;
    }

    static public int getIntVal(String name, Map<String, Object> data) {
        Object tmp;
        if ((tmp = data.get(name)) instanceof Number) {
            return ((Number) tmp).intValue();
        }
        return 0;
    }

    static public String getStringVal(String name, Map<String, Object> data, String def) {
        Object tmp;
        if ((tmp = data.get(name)) instanceof CharSequence) {
            return tmp.toString();
        }
        return def;
    }

    static public Uri getUriVal(String name, Map<String, Object> data) {
        String str = getStringVal(name, data, null);
        if (str == null) {
            return null;
        }
        URL url = Cache.getTinode().toAbsoluteURL(str);
        return url != null ? Uri.parse(url.toString()) : null;
    }

    static public @Nullable byte[] getByteArray(String name, @NotNull Map<String, Object> data) {
        Object val = data.get(name);
        return val instanceof String ?
                Base64.decode((String) val, Base64.DEFAULT) :
                val instanceof byte[] ? (byte[]) val : null;
    }

    static public @NotNull List<String> getRequiredCredMethods(@NotNull Tinode tinode, @NotNull String forAuthLevel) {
        // "auth:email,tel;anon:none"
        Object credObj = tinode.getServerParam("reqCred");
        ArrayList<String> methods = new ArrayList<>();
        if (credObj instanceof Map) {
            Object methodsObj = ((Map) credObj).get(forAuthLevel);
            if (methodsObj instanceof List) {
                for (Object method : (List) methodsObj) {
                    if (method instanceof String) {
                        methods.add((String) method);
                    }
                }
            }
        }
        return methods;
    }

    static void fillAboutTinode(View view, String serverUrl, BrandingConfig branding) {
        ((TextView) view.findViewById(R.id.app_version)).setText(TindroidApp.getAppVersion());
        ((TextView) view.findViewById(R.id.app_build)).setText(String.format(Locale.US, "%d",
                TindroidApp.getAppBuild()));
        ((TextView) view.findViewById(R.id.app_server)).setText(serverUrl);
        if (branding != null) {
            Bitmap logo = BrandingConfig.getLargeIcon(view.getContext());
            if (logo != null) {
                ((ImageView) view.findViewById(R.id.imageLogo)).setImageBitmap(logo);
            }
            if (!TextUtils.isEmpty(branding.service_name)) {
                ((TextView) view.findViewById(R.id.appTitle)).setText(branding.service_name);
            }
            if (!TextUtils.isEmpty(branding.tos_uri)) {
                String homePage = Uri.parse(branding.tos_uri).getAuthority();
                if (!TextUtils.isEmpty(homePage)) {
                    ((TextView) view.findViewById(R.id.appHomePage)).setText(homePage);
                }
            }

            View byTinode = view.findViewById(R.id.byTinode);
            byTinode.setVisibility(View.VISIBLE);
            UiUtils.clickToBrowseURL(byTinode, R.string.tinode_url);
        }
    }
    // Click on a view to open the given URL.
    static void clickToBrowseURL(View view, String url) {
        Uri uri =  Uri.parse(url);
        if (uri == null) {
            return;
        }
        view.setOnClickListener(arg -> {
            try {
                view.getContext().startActivity(new Intent(Intent.ACTION_VIEW, uri));
            } catch (ActivityNotFoundException ignored) {
                Log.w(TAG, "No application can open the URL");
            }
        });
    }

    static void clickToBrowseURL(@NonNull View view, @StringRes int url) {
        clickToBrowseURL(view, view.getResources().getString(R.string.tinode_url));
    }

    static boolean isAppFirstRun(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getBoolean(PREF_FIRST_RUN, true);
    }

    static void doneAppFirstRun(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.edit().putBoolean(PREF_FIRST_RUN, false).apply();
    }

    interface ProgressIndicator {
        void toggleProgressIndicator(boolean on);
    }

    static class MeEventListener extends MeTopic.MeListener<VxCard> {
        // Called on failed subscription request.
        public void onSubscriptionError(Exception ex) {
        }
    }

    static class EventListener implements Tinode.EventListener {
        private final Activity mActivity;
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
                Log.d(TAG, "onDisconnect error: " + code);
            }
            setConnected(false);
        }

        private void setConnected(final boolean connected) {
            if (mActivity != null && (mConnected == null || connected != mConnected)) {
                mConnected = connected;
                setConnectedStatus(mActivity, connected);
            } else {
                mConnected = null;
            }
        }
    }

    static class AccessModeLabel {
        public final int color;
        final int nameId;

        AccessModeLabel(int nameId, int color) {
            this.nameId = nameId;
            this.color = color;
        }
    }

    static class ToastFailureListener extends PromisedReply.FailureListener<ServerMessage> {
        private final Activity mActivity;

        ToastFailureListener(Activity activity) {
            mActivity = activity;
        }

        @Override
        public PromisedReply<ServerMessage> onFailure(final Exception err) {
            if (mActivity == null || mActivity.isFinishing() || mActivity.isDestroyed()) {
                return null;
            }
            Log.d(TAG, mActivity.getLocalClassName() + ": promise rejected", err);
            mActivity.runOnUiThread(() -> {
                if (err instanceof NotConnectedException) {
                    Toast.makeText(mActivity, R.string.no_connection, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(mActivity, R.string.action_failed, Toast.LENGTH_SHORT).show();
                }
            });
            return null;
        }
    }
}
