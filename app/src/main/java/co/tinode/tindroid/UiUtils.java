package co.tinode.tindroid;

import android.Manifest;
import android.accounts.Account;
import android.accounts.AccountManager;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Parcelable;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.text.format.DateUtils;
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
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.squareup.picasso.Picasso;

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

import co.tinode.tinsdk.ComTopic;
import co.tinode.tinsdk.MeTopic;
import co.tinode.tinsdk.NotConnectedException;
import co.tinode.tinsdk.PromisedReply;
import co.tinode.tinsdk.ServerResponseException;
import co.tinode.tinsdk.Tinode;
import co.tinode.tinsdk.Topic;
import co.tinode.tinsdk.model.Acs;
import co.tinode.tinsdk.model.Credential;
import co.tinode.tinsdk.model.PrivateType;
import co.tinode.tinsdk.model.ServerMessage;
import co.tinode.tinui.BuildConfig;
import co.tinode.tinui.ImageUtils;
import co.tinode.tinui.account.ContactsManager;
import co.tinode.tinui.account.Utils;
import co.tinode.tinui.db.BaseDb;
import co.tinode.tinui.media.VxCard;
import co.tinode.tinui.widgets.LetterTileDrawable;
import co.tinode.tinui.widgets.OnlineDrawable;
import co.tinode.tinui.widgets.RoundImageDrawable;
import co.tinode.tinui.widgets.UrlLayerDrawable;

/**
 * Static utilities for UI support.
 */
public class UiUtils {
    private static final String TAG = "UiUtils";

    static final int ACTION_UPDATE_SELF_SUB = 0;
    static final int ACTION_UPDATE_SUB = 1;
    static final int ACTION_UPDATE_AUTH = 2;
    static final int ACTION_UPDATE_ANON = 3;

    static final String PREF_TYPING_NOTIF = "pref_typingNotif";
    static final String PREF_READ_RCPT = "pref_readReceipts";

    // Maximum length of user name or topic title.
    static final int MAX_TITLE_LENGTH = 60;
    // Maximum length of topic description.
    static final int MAX_DESCRIPTION_LENGTH = 360;
    // Length of quoted text.
    public static final int QUOTED_REPLY_LENGTH = 64;

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

    static void setupToolbar(final Activity activity, final VxCard pub,
                             final String topicName, final boolean online, final Date lastSeen) {
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
                } else if (online) {
                    toolbar.setSubtitle(activity.getString(R.string.online_now));
                } else if (lastSeen != null) {
                    toolbar.setSubtitle(relativeDateFormat(activity, lastSeen));
                } else {
                    toolbar.setSubtitle(null);
                }
                constructToolbarLogo(activity, pub, topicName, showOnline);
            } else {
                toolbar.setTitle(R.string.app_name);
                toolbar.setSubtitle(null);
                toolbar.setLogo(null);
            }
            toolbar.invalidate();
        });
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

    // Constructs LayerDrawable with the following layers:
    // 0. [Avatar or LetterTileDrawable]
    // 1. [Online indicator]
    // 2. [Typing indicator]
    private static void constructToolbarLogo(final Activity activity, final VxCard pub, String uid, Boolean online) {
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
            drawables.add(avatarDrawable(activity, bmp, title, uid));
        } else {
            // Remote resource. Create a transparent placeholder layer.
            drawables.add(new ColorDrawable(0x00000000));
        }

        if (online != null) {
            drawables.add(new OnlineDrawable(online));

            typing = (AnimationDrawable) ResourcesCompat.getDrawable(res, R.drawable.tinui_typing_indicator, null);
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
                    ResourcesCompat.getDrawable(res, R.drawable.tinui_disk, null);
            layers.setUrlByLayerId(res, LOGO_LAYER_AVATAR, Cache.getTinode().toAbsoluteURL(ref).toString(),
                placeholder, R.drawable.tinui_ic_broken_image_round);
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
        if (!(logo instanceof LayerDrawable)) {
            return;
        }

        ((OnlineDrawable) ((LayerDrawable) logo).findDrawableByLayerId(LOGO_LAYER_ONLINE)).setOnline(online);
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
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
                ActivityCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED;
    }

    static LinkedList<String> getMissingPermissions(Context context, String[] permissions) {
        LinkedList<String> missing = new LinkedList<>();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return missing;
        }
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
            Collection<ComTopic<VxCard>> topics = Cache.getTinode().getFilteredTopics(Topic::isP2PType);
            ContactsManager.updateContacts(activity, acc, topics);
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.notifyAccountAuthenticated(acc);
        }
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
                } else {
                    Log.i(TAG, "Toolbar menu is null");
                }
                View line = activity.findViewById(R.id.offline_indicator);
                if (line != null) {
                    line.setVisibility(online ? View.INVISIBLE : View.VISIBLE);
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
        return "unknown";
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

        // Option 1: pick image from the gallery.
        Intent galleryIntent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        galleryIntent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/jpeg", "image/png", "image/gif"});

        // Option 2: take a photo.
        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        // Make sure camera is available.
        if (cameraIntent.resolveActivity(activity.getPackageManager()) == null) {
            cameraIntent = null;
        }

        // Pack two intents into a chooser.
        Intent chooserIntent = Intent.createChooser(galleryIntent, activity.getString(R.string.select_image));
        if (cameraIntent != null) {
            chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Parcelable[]{cameraIntent});
        }

        return chooserIntent;
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
            Bitmap photo = data.getParcelableExtra("data");
            Uri uri = data.getData();
            if (photo != null) {
                // Image from the camera.
                args.putParcelable(AttachmentHandler.ARG_SRC_BITMAP, photo);
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

    static void acceptAvatar(final Activity activity, final ImageView avatarContainer, final Bitmap avatar) {
        if (activity == null || avatarContainer == null) {
            return;
        }

        if (avatar == null) {
            Toast.makeText(activity, activity.getString(R.string.image_is_unavailable), Toast.LENGTH_SHORT).show();
            return;
        }

        avatarContainer.setImageDrawable(new RoundImageDrawable(avatarContainer.getResources(),
                ImageUtils.scaleSquareBitmap(avatar, ImageUtils.MAX_AVATAR_SIZE)));
    }

    // Construct avatar from VxCard and set it to the provided ImageView.
    static void setAvatar(ImageView avatarView, VxCard pub, String address) {
        Bitmap avatar = null;
        URL ref = null;
        String fullName = null;
        if (pub != null) {
            avatar = pub.getBitmap();
            fullName = pub.fn;
            ref = Cache.getTinode().toAbsoluteURL(pub.getPhotoRef());
        }

        Drawable local = UiUtils.avatarDrawable(avatarView.getContext(), avatar, fullName, address);
        if (ref != null) {
            Picasso
                    .get()
                    .load(ref.toString())
                    .resize(ImageUtils.MAX_AVATAR_SIZE, ImageUtils.MAX_AVATAR_SIZE)
                    .placeholder(local)
                    .error(R.drawable.tinui_ic_broken_image_round)
                    .into(avatarView);
        } else {
            avatarView.setImageDrawable(local);
        }
    }

    // Construct avatar drawable: use bitmap if it is not null,
    // otherwise use name & address to create a LetterTileDrawable.
    static Drawable avatarDrawable(Context context, Bitmap bmp, String name, String address) {
        if (bmp != null) {
            return new RoundImageDrawable(context.getResources(), bmp);
        } else {
            LetterTileDrawable drawable = new LetterTileDrawable(context);
            drawable.setContactTypeAndColor(
                    Topic.isP2PType(address) ?
                            LetterTileDrawable.ContactType.PERSON :
                            LetterTileDrawable.ContactType.GROUP)
                    .setLetterAndColor(name, address)
                    .setIsCircular(true);
            return drawable;
        }
    }

    public static void setMessageStatusIcon(ImageView holder, int status, int read, int recv) {
        if (status <= BaseDb.Status.SENDING.value) {
            holder.setImageResource(R.drawable.tinui_ic_schedule);
        } else if (status == BaseDb.Status.FAILED.value) {
            holder.setImageResource(R.drawable.tinui_ic_warning);
        } else {
            if (read > 0) {
                holder.setImageResource(R.drawable.tinui_ic_done_all2);
            } else if (recv > 0) {
                holder.setImageResource(R.drawable.tinui_ic_done_all);
            } else {
                holder.setImageResource(R.drawable.tinui_ic_done);
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
        final LinearLayout editor = (LinearLayout) inflater.inflate(R.layout.tindroid_dialog_edit_permissions, null);
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

            CheckedTextView check = (CheckedTextView) inflater.inflate(R.layout.tindroid_edit_one_permission,
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
    static <T extends Topic<VxCard, ?, ?, ?>>
    PromisedReply<ServerMessage> updateAvatar(final T topic, Bitmap bmp) {
        final VxCard pub;
        if (topic.getPub() != null) {
            pub = topic.getPub().copy();
        } else {
            pub = new VxCard();
        }

        return AttachmentHandler.uploadAvatar(pub, bmp).thenApply(new PromisedReply.SuccessListener<ServerMessage>() {
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
            if (title.length() > MAX_TITLE_LENGTH) {
                title = title.substring(0, MAX_TITLE_LENGTH);
            }
            if (oldPub != null && !stringsEqual(title, oldPub.fn)) {
                pub = new VxCard();
                pub.fn = title;
            }
        }

        if (description != null) {
            if (description.length() > MAX_DESCRIPTION_LENGTH) {
                description = description.substring(0, MAX_DESCRIPTION_LENGTH);
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
            if (subtitle.length() > MAX_TITLE_LENGTH) {
                subtitle = subtitle.substring(0, MAX_TITLE_LENGTH);
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
                    if (errCode == 401 || errCode == 403 || errCode == 404) {
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
    static String getContentPath(Context context, Uri uri) {
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
                        final String selection = "_id=?";
                        final String[] selectionArgs = new String[]{split[1]};
                        return getResolverData(context, contentUri, selection, selectionArgs);
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
            return getResolverData(context, uri, null, null);
        } else if ("file".equalsIgnoreCase(uri.getScheme())) {
            // File
            return uri.getPath();
        }
        return null;
    }

    private static String getResolverData(Context context, Uri uri, String selection, String[] selectionArgs) {
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

    static String bytesToHumanSize(long bytes) {
        if (bytes <= 0) {
            return "0 Bytes";
        }

        String[] sizes = new String[]{"Bytes", "KB", "MB", "GB", "TB"};
        int bucket = (63 - Long.numberOfLeadingZeros(bytes)) / 10;
        double count = bytes / Math.pow(1024, bucket);
        int roundTo = bucket > 0 ? (count < 3 ? 2 : (count < 30 ? 1 : 0)) : 0;
        NumberFormat fmt = DecimalFormat.getInstance();
        fmt.setMaximumFractionDigits(roundTo);
        return fmt.format(count) + " " + sizes[bucket];
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

    interface ProgressIndicator {
        void toggleProgressIndicator(boolean on);
    }

    static class MeEventListener extends MeTopic.MeListener<VxCard> {
        // Called on failed subscription request.
        public void onSubscriptionError(Exception ex) {
        }
    }

    static class EventListener extends Tinode.EventListener {
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
            if (mActivity != null &&
                    (mConnected == null || connected != mConnected)) {
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
