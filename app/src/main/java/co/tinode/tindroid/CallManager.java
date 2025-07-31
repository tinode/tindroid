package co.tinode.tindroid;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Person;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Icon;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telecom.VideoProfile;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.ColorRes;
import androidx.annotation.StringRes;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import co.tinode.tindroid.media.VxCard;
import co.tinode.tindroid.services.CallConnection;
import co.tinode.tindroid.services.CallConnectionService;

import co.tinode.tinodesdk.ComTopic;
import co.tinode.tinodesdk.MeTopic;
import co.tinode.tinodesdk.Tinode;
import co.tinode.tinodesdk.Topic;

import static android.content.Context.TELECOM_SERVICE;

public class CallManager {
    private static final String TAG = "CallManager";

    public static final String NOTIFICATION_TAG_INCOMING_CALL = "incoming_call";

    private static CallManager sSharedInstance;

    private final PhoneAccountHandle mPhoneAccountHandle;

    private CallManager(Context context) {
        TelecomManager telecomManager = (TelecomManager) context.getSystemService(TELECOM_SERVICE);

        final Tinode tinode = Cache.getTinode();
        final String myID = tinode.getMyId();
        if (TextUtils.isEmpty(myID)) {
            throw new IllegalStateException("Tinode ID is not set");
        }

        String accLabel = context.getString(R.string.current_user);
        Icon icon = null;
        MeTopic<VxCard> me = tinode.getMeTopic();
        if (me != null) {
            VxCard card = (VxCard) tinode.getMeTopic().getPub();
            if (card != null) {
                accLabel = !TextUtils.isEmpty(card.fn) ? card.fn : accLabel;
                Bitmap avatar = card.getBitmap();
                if (avatar != null) {
                    int size = avatar.getByteCount();
                    if (size > 32_768) {
                        // If the avatar is too large, scale it down, otherwise PhoneAccount.builder throws.
                        avatar = UiUtils.scaleSquareBitmap(avatar, 128);
                    }
                    icon = Icon.createWithAdaptiveBitmap(avatar);
                }
            }
        }

        // Register current user's phone account.
        mPhoneAccountHandle = new PhoneAccountHandle(new ComponentName(context, CallConnectionService.class), myID);
        int capabilities = PhoneAccount.CAPABILITY_VIDEO_CALLING;
        capabilities = capabilities | PhoneAccount.CAPABILITY_SELF_MANAGED |
                PhoneAccount.CAPABILITY_SUPPORTS_VIDEO_CALLING;

        PhoneAccount.Builder builder = PhoneAccount.builder(mPhoneAccountHandle, accLabel)
                .setAddress(Uri.fromParts("tinode", myID, null))
                .setSubscriptionAddress(Uri.fromParts("tinode", myID, null))
                .addSupportedUriScheme("tinode")
                .setCapabilities(capabilities)
                .setShortDescription(accLabel)
                .setIcon(icon);
        telecomManager.registerPhoneAccount(builder.build());
    }

    @SuppressLint("UnsafeOptInUsageError")
    private static CallManager getShared() {
        if (sSharedInstance != null) {
            return sSharedInstance;
        }
        sSharedInstance = new CallManager(TindroidApp.getAppContext());
        return sSharedInstance;
    }

    // FIXME: this has to be called on logout.
    public static void unregisterCallingAccount() {
        try {
            CallManager shared = CallManager.getShared();
            @SuppressLint("UnsafeOptInUsageError")
            TelecomManager telecomManager = (TelecomManager) TindroidApp.getAppContext().getSystemService(TELECOM_SERVICE);
            telecomManager.unregisterPhoneAccount(shared.mPhoneAccountHandle);
        } catch (IllegalStateException | SecurityException | UnsupportedOperationException ignored) {
        }
    }

    @SuppressLint("UnsafeOptInUsageError")
    public static void placeOutgoingCall(Activity activity, String callee, boolean audioOnly) {
        TelecomManager telecomManager = (TelecomManager) TindroidApp.getAppContext().getSystemService(TELECOM_SERVICE);
        if (shouldBypassTelecom(activity, telecomManager, true)) {
            // Self-managed phone accounts are not supported, bypassing Telecom.
            showOutgoingCallUi(activity, callee, audioOnly, null);
            return;
        }

        CallManager shared;
        try {
            shared = CallManager.getShared();
        } catch (UnsupportedOperationException ex) {
            Toast.makeText(TindroidApp.getAppContext(), R.string.calling_not_supported, Toast.LENGTH_SHORT).show();
            Log.w(TAG, "Unable to place call", ex);
            return;
        }

        Bundle callParams = new Bundle();
        callParams.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, shared.mPhoneAccountHandle);
        if (!audioOnly) {
            callParams.putInt(TelecomManager.EXTRA_START_CALL_WITH_VIDEO_STATE, VideoProfile.STATE_BIDIRECTIONAL);
            callParams.putBoolean(TelecomManager.EXTRA_START_CALL_WITH_SPEAKERPHONE, true);
        }

        Bundle extras = new Bundle();
        extras.putString(Const.INTENT_EXTRA_TOPIC, callee);
        extras.putBoolean(Const.INTENT_EXTRA_CALL_AUDIO_ONLY, audioOnly);
        callParams.putParcelable(TelecomManager.EXTRA_OUTGOING_CALL_EXTRAS, extras);
        try {
            telecomManager.placeCall(Uri.fromParts("tinode", callee, null), callParams);
        } catch (SecurityException ex) {
            Toast.makeText(TindroidApp.getAppContext(), R.string.unable_to_place_call, Toast.LENGTH_SHORT).show();
            Log.w(TAG, "Unable to place call", ex);
        }
    }

    // Dismiss call notification.
    public static void dismissIncomingCall(Context context, String topicName, int seq) {
        CallInProgress call = Cache.getCallInProgress();
        if (call == null || !call.equals(topicName, seq)) {
            return;
        }

        LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(context);
        final Intent intent = new Intent(context, HangUpBroadcastReceiver.class);
        intent.setAction(Const.INTENT_ACTION_CALL_CLOSE);
        intent.putExtra(Const.INTENT_EXTRA_TOPIC, topicName);
        intent.putExtra(Const.INTENT_EXTRA_SEQ, seq);
        lbm.sendBroadcast(intent);
    }

    public static void acceptIncomingCall(Context context, String caller, int seq, boolean audioOnly) {
        CallInProgress cip = Cache.getCallInProgress();
        if (cip != null) {
            if (cip.equals(caller, seq)) {
                // The call is already accepted.
                Log.w(TAG, "Call already accepted: topic = " + caller + ", seq = " + seq);
                return;
            }
            // Hanging up: another call in progress
            final ComTopic topic = (ComTopic) Cache.getTinode().getTopic(caller);
            if (topic != null) {
                topic.videoCallHangUp(seq);
            }
            return;
        }

        final ComTopic topic = (ComTopic) Cache.getTinode().getTopic(caller);
        if (topic == null) {
            Log.w(TAG, "Call from un unknown topic " + caller);
            return;
        }

        Bundle extras = new Bundle();
        extras.putString(Const.INTENT_EXTRA_TOPIC, caller);
        extras.putInt(Const.INTENT_EXTRA_SEQ, seq);
        extras.putBoolean(Const.INTENT_EXTRA_CALL_AUDIO_ONLY, audioOnly);

        CallManager shared = CallManager.getShared();
        TelecomManager telecomManager = (TelecomManager) context.getSystemService(TELECOM_SERVICE);

        if (shouldBypassTelecom(context, telecomManager, false)) {
            // Bypass Telecom when self-managed calls are not supported.
            Cache.prepareNewCall(caller, seq, null);
            showIncomingCallUi(context, caller, extras);
            topic.videoCallRinging(seq);
            return;
        }

        Uri uri = Uri.fromParts("tinode", caller, null);
        Bundle callParams = new Bundle();
        callParams.putParcelable(TelecomManager.EXTRA_INCOMING_CALL_ADDRESS, uri);
        callParams.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, shared.mPhoneAccountHandle);
        callParams.putBoolean(TelecomManager.EXTRA_START_CALL_WITH_SPEAKERPHONE, !audioOnly);
        callParams.putInt(TelecomManager.EXTRA_INCOMING_VIDEO_STATE, audioOnly ?
                VideoProfile.STATE_AUDIO_ONLY : VideoProfile.STATE_BIDIRECTIONAL);

        callParams.putBundle(TelecomManager.EXTRA_INCOMING_CALL_EXTRAS, extras);

        try {
            telecomManager.addNewIncomingCall(shared.mPhoneAccountHandle, callParams);
            topic.videoCallRinging(seq);
        } catch (SecurityException ex) {
            Cache.prepareNewCall(caller, seq, null);
            showIncomingCallUi(context, caller, extras);
            topic.videoCallRinging(seq);
        } catch (Exception ex) {
            Log.w(TAG, "Failed to accept incoming call", ex);
        }
    }

    public static void showOutgoingCallUi(Context context, String topicName,
                                          boolean audioOnly, CallConnection conn) {
        Cache.prepareNewCall(topicName, 0, conn);

        Intent intent = new Intent(context, CallActivity.class);
        intent.setAction(CallActivity.INTENT_ACTION_CALL_START);
        intent.putExtra(Const.INTENT_EXTRA_TOPIC, topicName);
        intent.putExtra(Const.INTENT_EXTRA_CALL_AUDIO_ONLY, audioOnly);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        context.startActivity(intent);
    }

    public static void showIncomingCallUi(Context context, String topicName, Bundle args) {
        final ComTopic topic = (ComTopic) Cache.getTinode().getTopic(topicName);
        if (topic == null) {
            Log.w(TAG, "Call from un unknown topic " + topicName);
            return;
        }

        final int width = (int) context.getResources().getDimension(android.R.dimen.notification_large_icon_width);
        final VxCard pub = (VxCard) topic.getPub();
        final String userName = pub != null && !TextUtils.isEmpty(pub.fn) ? pub.fn :
                context.getString(R.string.unknown);
        // This is the UI thread handler.
        final Handler uiHandler = new Handler(Looper.getMainLooper());

        new Thread(() -> {
            // This call must be off UI thread.
            final Bitmap avatar = UiUtils.avatarBitmap(context, pub,
                    Topic.getTopicTypeByName(topicName), topicName, width);
            // This must run on UI thread.
            uiHandler.post(() -> {
                NotificationManager nm = context.getSystemService(NotificationManager.class);

                Notification.Builder builder = new Notification.Builder(context);

                builder.setOngoing(true)
                        .setVisibility(Notification.VISIBILITY_PUBLIC)
                        .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE));

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    builder.setFlag(Notification.FLAG_INSISTENT, true);
                }
                builder.setChannelId(Const.CALL_NOTIFICATION_CHAN_ID);


                int seq = args.getInt(Const.INTENT_EXTRA_SEQ);
                boolean audioOnly = args.getBoolean(Const.INTENT_EXTRA_CALL_AUDIO_ONLY);

                Cache.setCallActive(topicName, seq);

                PendingIntent askUserIntent = askUserIntent(context, topicName, seq, audioOnly);
                // Set notification content intent to take user to fullscreen UI if user taps on the
                // notification body.
                builder.setContentIntent(askUserIntent);
                // Set full screen intent to trigger display of the fullscreen UI when the notification
                // manager deems it appropriate.
                builder.setFullScreenIntent(askUserIntent, true)
                        .setLargeIcon(Icon.createWithBitmap(avatar))
                        .setContentTitle(userName)
                        .setSmallIcon(R.drawable.ic_icon_push)
                        .setContentText(context.getString(audioOnly ? R.string.tinode_audio_call :
                                R.string.tinode_video_call))
                        .setUsesChronometer(true)
                        .setCategory(Notification.CATEGORY_CALL);

                // This will be ignored on O+ and handled by the channel
                builder.setPriority(Notification.PRIORITY_MAX);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Person caller = new Person.Builder()
                            .setIcon(Icon.createWithBitmap(avatar))
                            .setKey(topicName)
                            .setName(userName)
                            .build();
                    builder.setStyle(Notification.CallStyle.forIncomingCall(caller,
                            declineIntent(context, topicName, seq), answerIntent(context, topicName, seq, audioOnly)));
                } else {
                    builder.addAction(new Notification.Action.Builder(Icon.createWithResource(context, R.drawable.ic_call_end),
                            getActionText(context, R.string.decline_call, R.color.colorNegativeAction), declineIntent(context, topicName, seq))
                            .build());

                    builder.addAction(new Notification.Action.Builder(Icon.createWithResource(context, R.drawable.ic_call_white),
                            getActionText(context, R.string.answer_call, R.color.colorPositiveAction), answerIntent(context, topicName, seq, audioOnly))
                            .build());
                }

                Notification notification = builder.build();
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                    notification.flags |= Notification.FLAG_INSISTENT;
                }
                nm.notify(NOTIFICATION_TAG_INCOMING_CALL, 0, notification);
            });
        }).start();
    }

    private static Spannable getActionText(Context context, @StringRes int stringRes, @ColorRes int colorRes) {
        Spannable spannable = new SpannableString(context.getText(stringRes));
        spannable.setSpan(
                new ForegroundColorSpan(context.getColor(colorRes)), 0, spannable.length(), 0);

        return spannable;
    }

    private static PendingIntent askUserIntent(Context context, String topicName, int seq, boolean audioOnly) {
        Intent intent = new Intent(CallActivity.INTENT_ACTION_CALL_INCOMING, null);
        intent.setFlags(Intent.FLAG_ACTIVITY_NO_USER_ACTION
                | Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra(Const.INTENT_EXTRA_TOPIC, topicName)
                .putExtra(Const.INTENT_EXTRA_SEQ, seq)
                .putExtra(Const.INTENT_EXTRA_CALL_AUDIO_ONLY, audioOnly);
        intent.setClass(context, CallActivity.class);
        return PendingIntent.getActivity(context, 101, intent,
                PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    public static Intent answerCallIntent(Context context, String topicName, int seq, boolean audioOnly) {
        Intent intent = new Intent(CallActivity.INTENT_ACTION_CALL_INCOMING, null);
        intent.setFlags(Intent.FLAG_ACTIVITY_NO_USER_ACTION
                | Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra(Const.INTENT_EXTRA_TOPIC, topicName)
                .putExtra(Const.INTENT_EXTRA_SEQ, seq)
                .putExtra(Const.INTENT_EXTRA_CALL_ACCEPTED, true)
                .putExtra(Const.INTENT_EXTRA_CALL_AUDIO_ONLY, audioOnly);
        intent.setClass(context, CallActivity.class);
        return intent;
    }

    private static PendingIntent answerIntent(Context context, String topicName, int seq, boolean audioOnly) {
        return PendingIntent.getActivity(context, 102,
                answerCallIntent(context, topicName, seq, audioOnly),
                PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent declineIntent(Context context, String topicName, int seq) {
        final Intent intent = new Intent(context, HangUpBroadcastReceiver.class);
        intent.setAction(Const.INTENT_ACTION_CALL_CLOSE);
        intent.putExtra(Const.INTENT_EXTRA_TOPIC, topicName);
        intent.putExtra(Const.INTENT_EXTRA_SEQ, seq);
        return PendingIntent.getBroadcast(context, 103, intent,
                PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static boolean shouldBypassTelecom(Context context, TelecomManager tm, boolean outgoing) {
        if (!UiUtils.isPermissionGranted(context, Manifest.permission.MANAGE_OWN_CALLS)) {
            Log.w(TAG, "No permission MANAGE_OWN_CALLS");
            return true;
        }

        if (outgoing) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                return true;
            }
            boolean disabled = !tm.isOutgoingCallPermitted(getShared().mPhoneAccountHandle);
            if (disabled) {
                Log.w(TAG, "Account cannot place outgoing calls");
            }
            return disabled;
        }

        boolean disabled = !tm.isIncomingCallPermitted(getShared().mPhoneAccountHandle);
        if (disabled) {
            Log.i(TAG, "Account cannot accept incoming calls");
        }
        return disabled;
    }
}
