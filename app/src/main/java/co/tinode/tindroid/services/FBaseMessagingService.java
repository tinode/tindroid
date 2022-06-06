package co.tinode.tindroid.services;

import android.annotation.SuppressLint;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.RingtoneManager;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.squareup.picasso.Picasso;

import java.io.IOException;
import java.net.URL;
import java.util.Map;

import androidx.annotation.NonNull;
import androidx.annotation.StyleableRes;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import co.tinode.tindroid.Cache;
import co.tinode.tindroid.ChatsActivity;
import co.tinode.tindroid.MessageActivity;
import co.tinode.tindroid.R;
import co.tinode.tindroid.UiUtils;
import co.tinode.tindroid.account.Utils;
import co.tinode.tindroid.format.FontFormatter;
import co.tinode.tindroid.media.VxCard;
import co.tinode.tindroid.widgets.LetterTileDrawable;
import co.tinode.tindroid.widgets.RoundImageDrawable;
import co.tinode.tinodesdk.ComTopic;
import co.tinode.tinodesdk.Tinode;
import co.tinode.tinodesdk.Topic;
import co.tinode.tinodesdk.User;
import co.tinode.tinodesdk.model.Drafty;

/**
 * Receive and handle (e.g. show) a push notification message.
 */
public class FBaseMessagingService extends FirebaseMessagingService {

    private static final String TAG = "FBaseMessagingService";

    // Width and height of the large icon (avatar).
    private static final int AVATAR_SIZE = 128;
    // Max length of the message.
    private static final int MAX_MESSAGE_LENGTH = 80;

    private static Bitmap makeLargeIcon(Context context, VxCard pub, Topic.TopicType tp, String id) {
        Bitmap bitmap = null;
        String fullName = null;
        if (pub != null) {
            fullName = pub.fn;
            URL ref = Cache.getTinode().toAbsoluteURL(pub.getPhotoRef());
            if (ref != null) {
                try {
                    bitmap = Picasso.get()
                            .load(ref.toString())
                            .resize(AVATAR_SIZE, AVATAR_SIZE).get();
                } catch (IOException ex) {
                    Log.w(TAG, "Failed to load avatar", ex);
                }
            } else {
                bitmap = pub.getBitmap();
            }
        }

        if (bitmap != null) {
            bitmap = Bitmap.createScaledBitmap(bitmap, AVATAR_SIZE, AVATAR_SIZE, false);
        } else {
            bitmap = new LetterTileDrawable(context)
                    .setContactTypeAndColor(tp == Topic.TopicType.GRP ?
                            LetterTileDrawable.ContactType.GROUP :
                            LetterTileDrawable.ContactType.PERSON, false)
                    .setLetterAndColor(fullName, id, false)
                    .getBitmap(AVATAR_SIZE, AVATAR_SIZE);
        }

        Resources res = context.getResources();
        return new RoundImageDrawable(res, bitmap).getRoundedBitmap();
    }

    private static int unwrapInteger(Integer value, int defaultValue) {
        return value != null ? value : defaultValue;
    }

    @SuppressWarnings("SameParameterValue")
    private static int resourceId(Resources res, String name, int defaultId, String resourceType, String packageName) {
        int id = res.getIdentifier(name, resourceType, packageName);
        return id != 0 ? id : defaultId;
    }

    private static int unwrapColor(String strColor, int defaultColor) {
        int color = defaultColor;
        if (strColor != null) {
            try {
                color = Color.parseColor(strColor);
            } catch (IllegalAccessError ignored) {
            }
        }
        return color;
    }

    // Localized text from resource name.
    private static String locText(Resources res, String locKey, String[] locArgs, String defaultText, String packageName) {
        String result = defaultText;
        if (locKey != null) {
            int id = res.getIdentifier(locKey, "string", packageName);
            if (id != 0) {
                if (locArgs != null) {
                    result = res.getString(id, (Object[]) locArgs);
                } else {
                    result = res.getString(id);
                }
            }
        }
        return result;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void onMessageReceived(RemoteMessage remoteMessage) {
        // There are two types of messages data messages and notification messages. Data messages are handled
        // here in onMessageReceived whether the app is in the foreground or background. Data messages are the type
        // traditionally used with GCM. Notification messages are only received here in onMessageReceived when the app
        // is in the foreground. When the app is in the background an automatically generated notification is displayed.
        // When the user taps on the notification they are returned to the app. Messages containing both notification
        // and data payloads are treated as notification messages. The Firebase console always sends notification
        // messages. For more see: https://firebase.google.com/docs/cloud-messaging/concept-options

        // Not getting messages here? See why this may be: https://goo.gl/39bRNJ
        Log.d(TAG, "From: " + remoteMessage.getFrom());

        // New message notification (msg):
        // - P2P
        //   Title: <sender name> || 'Unknown'
        //   Icon: <sender avatar> || (*)
        //   Body: <message content> || 'New message'
        // - GRP
        //   Title: <topic name> || 'Unknown'
        //   Icon: <sender avatar> || (*)
        //   Body: <sender name>: <message content> || 'New message'
        //
        // Subscription notification (sub):
        // New subscription:
        // - P2P
        //   Title: 'New chat'
        //   Icon: <sender avatar> || (*)
        //   Body: <sender name> || 'Unknown'
        // - GRP
        //   Title: 'New chat' ('by ' <sender name> || None)
        //   Icon: <group avatar> || (*)
        //   Body: <group name> || 'Unknown'
        // Deleted subscription:
        //   Always silent.
        //
        // Message read by the current user from another device (read):
        //   Always silent.
        //

        String topicName;

        final Tinode tinode = Cache.getTinode();
        NotificationCompat.Builder builder;

        // Check if message contains a data payload.
        if (remoteMessage.getData().size() > 0) {
            Map<String, String> data = remoteMessage.getData();

            // Update data state, maybe fetch missing data.
            String token = Utils.getLoginToken(getApplicationContext());
            tinode.oobNotification(data, token);

            if (Boolean.parseBoolean(data.get("silent"))) {
                // Silent notification: nothing to show.
                return;
            }

            topicName = data.get("topic");
            if (topicName == null) {
                Log.w(TAG, "NULL topic in a push notification");
                return;
            }

            String visibleTopic = UiUtils.getVisibleTopic();
            if (visibleTopic != null && visibleTopic.equals(topicName)) {
                // No need to do anything if we are in the topic already.
                Log.d(TAG, "Topic is visible, no need to show a notification");
                return;
            }

            Topic.TopicType tp = Topic.getTopicTypeByName(topicName);
            if (tp != Topic.TopicType.P2P && tp != Topic.TopicType.GRP) {
                Log.w(TAG, "Unexpected topic type=" + tp);
                return;
            }

            // Check notification type: message, subscription.
            String what = data.get("what");

            // Try to resolve sender using locally stored contacts.
            String senderId = data.get("xfrom");
            String senderName = null;
            Bitmap senderIcon = null;
            if (senderId != null) {
                User<VxCard> sender = tinode.getUser(senderId);
                // Assign sender's name and avatar.
                if (sender != null && sender.pub != null) {
                    senderName = sender.pub.fn;
                    senderIcon = makeLargeIcon(this, sender.pub, Topic.TopicType.P2P, senderId);
                }
            }

            if (senderName == null) {
                senderName = getResources().getString(R.string.sender_unknown);
            }
            if (senderIcon == null) {
                senderIcon = makeLargeIcon(this, null, Topic.TopicType.P2P, senderId);
            }

            String title = null;
            CharSequence body = null;
            Bitmap avatar = null;
            if (TextUtils.isEmpty(what) || "msg".equals(what)) {
                // Message notification.

                String seqStr = data.get("seq");
                try {
                    int seq = seqStr != null ? Integer.parseInt(seqStr) : 0;
                    String webrtc = data.get("webrtc");
                    if (webrtc != null && seq > 0) {
                        // It's a video call.
                        switch (webrtc) {
                            case "started":
                                if (!tinode.isMe(senderId)) {
                                    // Incoming call.
                                    UiUtils.handleIncomingVideoCall(this, "invite", topicName, senderId, seq);
                                }
                                break;
                            case "accepted":
                            case "missed":
                            case "declined":
                            case "disconnected":
                                String origSeqStr = data.get("replace");
                                int origSeq = origSeqStr != null ? Integer.parseInt(origSeqStr) : 0;
                                if (origSeq > 0) {
                                    // Dismiss the call UI.
                                    UiUtils.handleIncomingVideoCall(this, "dismiss", topicName, senderId, origSeq);
                                }
                                break;
                            default:
                                break;
                        }
                        return;
                    }
                } catch (NumberFormatException ignored) {
                    Log.w(TAG, "Invalid seq value '" + seqStr + "'");
                }

                avatar = senderIcon;

                // Try to retrieve rich message content.
                String richContent = data.get("rc");
                if (!TextUtils.isEmpty(richContent)) {
                    try {
                        Drafty draftyBody = Tinode.jsonDeserialize(richContent, Drafty.class.getCanonicalName());
                        if (draftyBody != null) {
                            @SuppressLint("ResourceType") @StyleableRes int[] attrs = {android.R.attr.textSize};
                            TypedArray ta = obtainStyledAttributes(androidx.appcompat.R.style.TextAppearance_Compat_Notification, attrs);
                            float fontSize = ta.getDimension(0, 14f);
                            ta.recycle();
                            body = draftyBody.shorten(MAX_MESSAGE_LENGTH, true)
                                    .format(new FontFormatter(this, fontSize));
                        } else {
                            // The content is plain text.
                            body = richContent;
                        }
                    } catch (ClassCastException ex) {
                        Log.w(TAG, "Failed to de-serialize payload", ex);
                    }
                }

                // If rich content is not available, use plain text content.
                if (TextUtils.isEmpty(body)) {
                    body = data.get("content");
                    if (TextUtils.isEmpty(body)) {
                        body = getResources().getString(R.string.new_message);
                    }
                }

                if (tp == Topic.TopicType.P2P) {
                    // P2P message
                    title = senderName;
                } else {
                    // Group message
                    ComTopic<VxCard> topic = (ComTopic<VxCard>) tinode.getTopic(topicName);
                    if (topic == null) {
                        // We already tried to attach to topic and get its description. If it's not available
                        // just give up.
                        Log.w(TAG, "Message received for an unknown topic: " + topicName);
                        return;
                    }

                    if (topic.getPub() != null) {
                        title = topic.getPub().fn;
                        if (TextUtils.isEmpty(title)) {
                            title = getResources().getString(R.string.placeholder_topic_title);
                        }
                        body = senderName + ": " + body;
                    }
                }
            } else if ("sub".equals(what)) {
                // New subscription notification.

                // Check if this is a known topic.
                ComTopic<VxCard> topic = (ComTopic<VxCard>) tinode.getTopic(topicName);
                if (topic != null) {
                    Log.d(TAG, "Duplicate invitation ignored: " + topicName);
                    return;
                }

                // Legitimate subscription to a new topic.
                title = getResources().getString(R.string.new_chat);
                if (tp == Topic.TopicType.P2P) {
                    // P2P message
                    body = senderName;
                    avatar = senderIcon;

                } else {
                    // Group message
                    topic = (ComTopic<VxCard>) tinode.getTopic(topicName);
                    if (topic == null) {
                        Log.w(TAG, "Failed to get topic description: " + topicName);
                        return;
                    }

                    VxCard pub = topic.getPub();
                    if (pub == null) {
                        body = getResources().getString(R.string.sender_unknown);
                        avatar = makeLargeIcon(this, null, tp, topicName);
                    } else {
                        body = pub.fn;
                        avatar = makeLargeIcon(this, pub, tp, topicName);
                    }
                }
            }

            builder = composeNotification(title, body, avatar);

        } else if (remoteMessage.getNotification() != null) {
            RemoteMessage.Notification remote = remoteMessage.getNotification();
            Log.d(TAG, "RemoteMessage Body: " + remote.getBody());

            topicName = remote.getTag();
            builder = composeNotification(remote);
        } else {
            // Everything is null.
            return;
        }

        showNotification(builder, topicName);
    }

    private void showNotification(NotificationCompat.Builder builder, String topicName) {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) {
            Log.e(TAG, "NotificationManager is not available");
            return;
        }

        // Workaround for an FCM bug or poor documentation.
        int requestCode = 0;

        Intent intent;
        if (TextUtils.isEmpty(topicName)) {
            // Communication on an unknown topic
            intent = new Intent(this, ChatsActivity.class);
        } else {
            requestCode = topicName.hashCode();
            // Communication on a known topic
            intent = new Intent(this, MessageActivity.class);
            intent.putExtra("topic", topicName);
        }

        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, requestCode, intent,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);

        // MessageActivity will cancel all notifications by tag, which is just topic name.
        // All notifications receive the same id 0 because id is not used.
        nm.notify(topicName, 0, builder.setContentIntent(pendingIntent).build());
    }

    /**
     * Create and show a simple notification containing the received FCM message.
     *
     * @param title  message title.
     * @param body   message body.
     * @param avatar sender's avatar.
     */
    private NotificationCompat.Builder composeNotification(String title, CharSequence body, Bitmap avatar) {
        @SuppressWarnings("deprecation") NotificationCompat.Builder notificationBuilder =
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                        new NotificationCompat.Builder(this, "new_message") :
                        new NotificationCompat.Builder(this);

        return notificationBuilder
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setSmallIcon(R.drawable.ic_icon_push)
                .setLargeIcon(avatar)
                .setColor(ContextCompat.getColor(this, R.color.colorNotificationBackground))
                .setContentTitle(title)
                .setContentText(body)
                .setAutoCancel(true)
                .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION));
    }

    private NotificationCompat.Builder composeNotification(@NonNull RemoteMessage.Notification remote) {
        @SuppressWarnings("deprecation") NotificationCompat.Builder notificationBuilder =
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
                        new NotificationCompat.Builder(this, "new_message") :
                        new NotificationCompat.Builder(this);

        final Resources res = getResources();
        final String packageName = getPackageName();

        return notificationBuilder
                .setPriority(unwrapInteger(remote.getNotificationPriority(), NotificationCompat.PRIORITY_HIGH))
                .setVisibility(unwrapInteger(remote.getVisibility(), NotificationCompat.VISIBILITY_PRIVATE))
                .setSmallIcon(resourceId(res, remote.getIcon(), R.drawable.ic_icon_push, "drawable", packageName))
                .setColor(unwrapColor(remote.getColor(), ContextCompat.getColor(this, R.color.colorNotificationBackground)))
                .setContentTitle(locText(res, remote.getTitleLocalizationKey(), remote.getTitleLocalizationArgs(),
                        remote.getTitle(), packageName))
                .setContentText(locText(res, remote.getBodyLocalizationKey(), remote.getBodyLocalizationArgs(),
                        remote.getBody(), packageName))
                .setAutoCancel(true)
                // TODO: use remote.getSound() instead of default.
                .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION));
    }

    @Override
    public void onNewToken(@NonNull final String refreshedToken) {
        super.onNewToken(refreshedToken);
        Log.d(TAG, "Refreshed token: " + refreshedToken);

        // Send token to the server.
        LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(this);
        Intent intent = new Intent("FCM_REFRESH_TOKEN");
        intent.putExtra("token", refreshedToken);
        lbm.sendBroadcast(intent);

        // The token is currently retrieved in co.tinode.tindroid.Cache.
    }
}
