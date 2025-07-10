package co.tinode.tindroid;

import android.annotation.SuppressLint;
import android.os.Build;
import android.telecom.CallAudioState;
import android.util.Log;

import com.google.firebase.messaging.FirebaseMessaging;

import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import co.tinode.tindroid.db.BaseDb;
import co.tinode.tindroid.media.VxCard;
import co.tinode.tindroid.services.CallConnection;
import co.tinode.tinodesdk.ComTopic;
import co.tinode.tinodesdk.FndTopic;
import co.tinode.tinodesdk.MeTopic;
import co.tinode.tinodesdk.PromisedReply;
import co.tinode.tinodesdk.Storage;
import co.tinode.tinodesdk.Tinode;
import co.tinode.tinodesdk.model.MsgServerData;
import co.tinode.tinodesdk.model.MsgServerInfo;
import co.tinode.tinodesdk.model.PrivateType;
import co.tinode.tinodesdk.model.ServerMessage;

/**
 * Shared resources.
 */
public class Cache {
    private static final String TAG = "Cache";

    private static final String API_KEY = "AQEAAAABAAD_rAp4DJh05a1HAwFT3A6K";

    private static final Cache sInstance = new Cache();

    private Tinode mTinode = null;

    // Currently active topic.
    private String mTopicSelected = null;

    // Current video call.
    private CallInProgress mCallInProgress = null;

    @SuppressLint("UnsafeOptInUsageError")
    public static synchronized Tinode getTinode() {
        if (sInstance.mTinode == null) {
            sInstance.mTinode = new Tinode("Tindroid/" + TindroidApp.getAppVersion(), API_KEY,
                    BaseDb.getInstance().getStore(), null);
            sInstance.mTinode.setOsString(Build.VERSION.RELEASE);

            // Default types for parsing Public, Private fields of messages
            sInstance.mTinode.setDefaultTypeOfMetaPacket(VxCard.class, PrivateType.class);
            sInstance.mTinode.setMeTypeOfMetaPacket(VxCard.class);
            sInstance.mTinode.setFndTypeOfMetaPacket(VxCard.class);

            // Set device language
            sInstance.mTinode.setLanguage(Locale.getDefault().toString());

            // Event handlers for video calls.
            sInstance.mTinode.addListener(new Tinode.EventListener() {
                @Override
                public void onDataMessage(MsgServerData data) {
                    if (Cache.getTinode().isMe(data.from)) {
                        return;
                    }
                    String webrtc = data.getStringHeader("webrtc");
                    MsgServerData.WebRTC callState = MsgServerData.parseWebRTC(webrtc);

                    ComTopic topic = (ComTopic) Cache.getTinode().getTopic(data.topic);
                    if (topic == null) {
                        return;
                    }

                    int effectiveSeq = UiUtils.parseSeqReference(data.getStringHeader("replace"));
                    if (effectiveSeq <= 0) {
                        effectiveSeq = data.seq;
                    }
                    // Check if we have a later version of the message (which means the call
                    // has been not yet either accepted or finished).
                    Storage.Message msg = topic.getMessage(effectiveSeq);
                    if (msg != null) {
                        webrtc = msg.getStringHeader("webrtc");
                        if (webrtc != null && MsgServerData.parseWebRTC(webrtc) != callState) {
                            return;
                        }
                    }

                    switch (callState) {
                        case STARTED:
                            CallManager.acceptIncomingCall(TindroidApp.getAppContext(),
                                    data.topic, data.seq, data.getBooleanHeader(Tinode.CALL_AUDIO_ONLY));
                            break;
                        case ACCEPTED:
                        case DECLINED:
                        case MISSED:
                        case DISCONNECTED:
                            CallInProgress call = Cache.getCallInProgress();
                            if (call != null && !call.isOutgoingCall()) {
                                CallManager.dismissIncomingCall(TindroidApp.getAppContext(), data.topic, data.seq);
                            }
                            break;
                        default:
                            break;
                    }

                }

                @Override
                public void onInfoMessage(MsgServerInfo info) {
                    if (MsgServerInfo.parseWhat(info.what) != MsgServerInfo.What.CALL) {
                        return;
                    }

                    CallInProgress call = Cache.getCallInProgress();
                    if (call == null || !call.equals(info.src, info.seq) || !Tinode.TOPIC_ME.equals(info.topic)) {
                        return;
                    }

                    // Dismiss call notification.
                    // Hang-up event received or current user accepted the call from another device.
                    if (MsgServerInfo.parseEvent(info.event) == MsgServerInfo.Event.HANG_UP ||
                            (Cache.getTinode().isMe(info.from) &&
                                    MsgServerInfo.parseEvent(info.event) == MsgServerInfo.Event.ACCEPT)) {
                        CallManager.dismissIncomingCall(TindroidApp.getAppContext(), info.src, info.seq);
                    }
                }
            });

            // Keep in app to prevent garbage collection.
            TindroidApp.retainCache(sInstance);
        }

        FirebaseMessaging fbId = FirebaseMessaging.getInstance();
        //noinspection ConstantConditions: Google lies about getInstance not returning null.
        if (fbId != null) {
            fbId.getToken().addOnSuccessListener(token -> {
                if (sInstance.mTinode != null) {
                    sInstance.mTinode.setDeviceToken(token);
                }
            });
        }
        return sInstance.mTinode;
    }

    // Invalidate existing cache.
    static void invalidate() {
        endCallInProgress();
        setSelectedTopicName(null);
        if (sInstance.mTinode != null) {
            sInstance.mTinode.logout();
            sInstance.mTinode = null;
        }
        FirebaseMessaging.getInstance().deleteToken();
    }

    public static CallInProgress getCallInProgress() {
        return sInstance.mCallInProgress;
    }

    public static void prepareNewCall(@NonNull String topic, int seq, @Nullable CallConnection conn) {
        if (sInstance.mCallInProgress == null) {
            sInstance.mCallInProgress = new CallInProgress(topic, seq, conn);
        } else if (!sInstance.mCallInProgress.equals(topic, seq)) {
            Log.e(TAG, "Inconsistent prepareNewCall\n\tExisting: " +
                    sInstance.mCallInProgress + "\n\tNew: " + topic + ":" + seq);
        }
    }

    public static void setCallActive(String topic, int seqId) {
        if (sInstance.mCallInProgress!= null) {
            sInstance.mCallInProgress.setCallActive(topic, seqId);
        } else {
            Log.e(TAG, "Attempt to mark call active with no configured call");
        }
    }

    public static void setCallConnected() {
        if (sInstance.mCallInProgress != null) {
            sInstance.mCallInProgress.setCallConnected();
        } else {
            Log.e(TAG, "Attempt to mark call connected with no configured call");
        }
    }

    public static void endCallInProgress() {
        if (sInstance.mCallInProgress != null) {
            sInstance.mCallInProgress.endCall();
            sInstance.mCallInProgress = null;
        }
    }

    public static boolean setCallAudioRoute(int route) {
        if (sInstance.mCallInProgress != null) {
            return sInstance.mCallInProgress.setAudioRoute(route);
        }
        return false;
    }

    public static int getCallAudioRoute() {
        if (sInstance.mCallInProgress != null) {
            return sInstance.mCallInProgress.getAudioRoute();
        }
        return CallAudioState.ROUTE_EARPIECE;
    }

    public static boolean isCallUseful() {
        return sInstance.mCallInProgress != null && sInstance.mCallInProgress.isConnectionUseful();
    }

    public static String getSelectedTopicName() {
        return sInstance.mTopicSelected;
    }

    // Save the new topic name. If the old topic is not null, unsubscribe.
    public static void setSelectedTopicName(String topicName) {
        String oldTopic = sInstance.mTopicSelected;
        sInstance.mTopicSelected = topicName;
        if (sInstance.mTinode != null && oldTopic != null && !oldTopic.equals(topicName)) {
            ComTopic topic = (ComTopic) sInstance.mTinode.getTopic(oldTopic);
            if (topic != null) {
                topic.leave();
            }
        }
    }

    // Connect to 'me' topic.
    @SuppressWarnings("unchecked")
    public static PromisedReply<ServerMessage> attachMeTopic(MeTopic.MeListener l) {
        final MeTopic<VxCard> me = getTinode().getOrCreateMeTopic();
        if (l != null) {
            me.setListener(l);
        }

        if (!me.isAttached()) {
            return me.subscribe(null, me
                    .getMetaGetBuilder()
                    .withCred()
                    .withDesc()
                    .withSub()
                    .withTags()
                    .build());
        } else {
            return new PromisedReply<>((ServerMessage) null);
        }
    }

    static PromisedReply<ServerMessage> attachFndTopic(FndTopic.FndListener<VxCard> l) {
        final FndTopic<VxCard> fnd = getTinode().getOrCreateFndTopic();
        if (l != null) {
            fnd.setListener(l);
        }

        if (!fnd.isAttached()) {
            // Don't request anything here.
            return fnd.subscribe(null, null);
        } else {
            return new PromisedReply<>((ServerMessage) null);
        }
    }
}
