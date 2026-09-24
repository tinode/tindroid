package co.tinode.tindroid;

import android.annotation.SuppressLint;
import android.os.Build;
import android.os.Bundle;
import android.telecom.CallAudioState;
import android.util.Log;

import com.google.firebase.messaging.FirebaseMessaging;

import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

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

    // Current video call. Volatile for visibility across threads.
    private volatile CallInProgress mCallInProgress = null;

    private boolean mFCMTokenRequested = false;

    private final Map<String, Bundle> mDataBundles = new HashMap<>();

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

            initFCM();
        }

        return sInstance.mTinode;
    }

    public static void initFCM() {
        if (!sInstance.mFCMTokenRequested) {
            FirebaseMessaging fbId = FirebaseMessaging.getInstance();
            //noinspection ConstantConditions: Google lies about getInstance not returning null.
            if (fbId != null) {
                sInstance.mFCMTokenRequested = true;
                fbId.register().addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        // Retry to fetch token later.
                        sInstance.mFCMTokenRequested = false;
                        Log.w(TAG, "FCM registration failed", task.getException());
                    }
                });
            } else {
                Log.w(TAG, "FirebaseMessaging not available");
            }
        }
    }

    // Invalidate existing cache.
    static void invalidate() {
        endCallInProgress();
        setSelectedTopicName(null);
        clearDataBundles();
        if (sInstance.mTinode != null) {
            sInstance.mTinode.logout();
            sInstance.mTinode = null;
        }
        sInstance.mFCMTokenRequested = false;
        FirebaseMessaging.getInstance().unregister();
    }

    public static CallInProgress getCallInProgress() {
        synchronized (sInstance) {
            return sInstance.mCallInProgress;
        }
    }

    /**
     * Atomically claims the single call slot. Repeated setup for the same call is allowed so a
     * fallback notification path can be reconciled with a later Telecom connection.
     *
     * @return {@code true} if the call was claimed or is the same existing call; {@code false}
     * if another call owns the slot or a second Telecom connection was supplied.
     */
    public static boolean prepareNewCall(@NonNull String topic, int seq, @Nullable CallConnection conn) {
        synchronized (sInstance) {
            if (sInstance.mCallInProgress == null) {
                sInstance.mCallInProgress = new CallInProgress(topic, seq, conn);
                return true;
            }

            if (sInstance.mCallInProgress.equals(topic, seq)) {
                return sInstance.mCallInProgress.attachConnection(conn);
            }

            Log.w(TAG, "Ignoring new call while another call is in progress. Existing=" +
                    sInstance.mCallInProgress + " New=" + topic + ":" + seq);
            return false;
        }
    }

    public static void setCallActive(String topic, int seqId) {
        synchronized (sInstance) {
            if (sInstance.mCallInProgress != null) {
                try {
                    sInstance.mCallInProgress.setCallActive(topic, seqId);
                } catch (IllegalArgumentException iae) {
                    // Preserve fatal behavior but add context about the cache state.
                    throw new IllegalArgumentException("setCallActive failed. Existing=" +
                            sInstance.mCallInProgress +
                            " New=" + topic + ":" + seqId + "; err=" + iae.getMessage());
                }
            } else {
                throw new IllegalStateException("setCallActive without prepareNewCall, New="
                        + topic + ":" + seqId);
            }
        }
    }

    public static void setCallConnected() {
        synchronized (sInstance) {
            if (sInstance.mCallInProgress != null) {
                sInstance.mCallInProgress.setCallConnected();
            } else {
                Log.e(TAG, "Attempt to mark call connected with no configured call");
            }
        }
    }

    public static void endCallInProgress() {
        synchronized (sInstance) {
            if (sInstance.mCallInProgress != null) {
                sInstance.mCallInProgress.endCall();
                sInstance.mCallInProgress = null;
            }
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

    // Save the new topic name.
    public static void setSelectedTopicName(String topicName) {
        sInstance.mTopicSelected = topicName;
    }

    public static String putDataBundle(Bundle bundle, String id) {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        sInstance.mDataBundles.put(id, bundle);
        return id;
    }

    public static Bundle getDataBundle(String id, boolean consume) {
        if (id == null) {
            return null;
        }
        return consume ? sInstance.mDataBundles.remove(id) : sInstance.mDataBundles.get(id);
    }

    public static void clearDataBundles() {
        sInstance.mDataBundles.clear();
    }

    // Connect to 'me' topic.
    @SuppressWarnings("unchecked")
    public static PromisedReply<ServerMessage> attachMeTopic(@Nullable MeTopic.MeListener l) {
        final MeTopic<VxCard> me = getTinode().getOrCreateMeTopic();
        me.addListener(l);

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

    static PromisedReply<ServerMessage> attachFndTopic(@NotNull FndTopic.FndListener<VxCard> l) {
        final FndTopic<VxCard> fnd = getTinode().getOrCreateFndTopic();
        fnd.addListener(l);

        if (!fnd.isAttached()) {
            // Don't request anything here.
            return fnd.subscribe(null, null);
        } else {
            return new PromisedReply<>((ServerMessage) null);
        }
    }
}
