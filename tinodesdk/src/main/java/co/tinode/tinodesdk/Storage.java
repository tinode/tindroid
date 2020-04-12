package co.tinode.tinodesdk;

import java.io.Closeable;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;

import co.tinode.tinodesdk.model.Drafty;
import co.tinode.tinodesdk.model.MsgRange;
import co.tinode.tinodesdk.model.MsgServerData;
import co.tinode.tinodesdk.model.Subscription;

/**
 * Interface for implementing persistence.
 */
public interface Storage {
    String getMyUid();
    // Update UID and clear unvalidated credentials.
    void setMyUid(String uid);
    // Server requested credential validation.
    void setMyUid(String uid, String[] credRequired);

    // Delete given account.
    void deleteAccount(String uid);

    String getDeviceToken();
    void saveDeviceToken(String token);

    void logout();

    // Server time minus local time
    void setTimeAdjustment(long adjustment);

    boolean isReady();

    // Fetch all topics
    Topic[] topicGetAll(Tinode tinode);
    // Fetch one topic by name
    Topic topicGet(Tinode tinode, String name);
    // Add new topic
    @SuppressWarnings("UnusedReturnValue")
    long topicAdd(Topic topic);
    /** Incoming change to topic description: the already mutated topic in memory is synchronized to DB */
    @SuppressWarnings("UnusedReturnValue")
    boolean topicUpdate(Topic topic);
    /** Delete topic */
    @SuppressWarnings("UnusedReturnValue")
    boolean topicDelete(Topic topic);

    /** Get seq IDs of the stored messages as a MsgRange, inclusive-exclusive [low, hi) */
    MsgRange getCachedMessagesRange(Topic topic);
    /**
     * Get the maximum seq ID range of the messages missing in cache, inclusive-exclusive [low, hi).
     * Returns null if all messages are present or no messages are found.
     */
    MsgRange getNextMissingRange(Topic topic);
    /** Local user reported messages as read */
    @SuppressWarnings("UnusedReturnValue")
    boolean setRead(Topic topic, int read);
    /** Local user reported messages as received */
    @SuppressWarnings("UnusedReturnValue")
    boolean setRecv(Topic topic, int recv);

    /** Add subscription in a generic topic. The subscription is received from the server. */
    @SuppressWarnings("UnusedReturnValue")
    long subAdd(Topic topic, Subscription sub);
    /** Update subscription in a generic topic */
    @SuppressWarnings("UnusedReturnValue")
    boolean subUpdate(Topic topic, Subscription sub);
    /** Add a new subscriber to topic. The new subscriber is being added locally. */
    @SuppressWarnings("UnusedReturnValue")
    long subNew(Topic topic, Subscription sub);
    /** Delete existing subscription */
    @SuppressWarnings("UnusedReturnValue")
    boolean subDelete(Topic topic, Subscription sub);

    /** Get a list o topic subscriptions from DB. */
    Collection<Subscription> getSubscriptions(Topic topic);

    /** Read user description */
    User userGet(String uid);
    /** Insert new user */
    @SuppressWarnings("UnusedReturnValue")
    long userAdd(User user);
    /** Update existing user */
    @SuppressWarnings("UnusedReturnValue")
    boolean userUpdate(User user);

    /**
     * Message received from the server.
     */
    long msgReceived(Topic topic, Subscription sub, MsgServerData msg);

    /**
     * Save message to DB as "sending".
     *
     * @param topic topic which sent the message
     * @param data message data to save
     * @param head message headers
     * @return database ID of the message suitable for use in
     *  {@link #msgDelivered(Topic topic, long id, Date timestamp, int seq)}
     */
    long msgSend(Topic topic, Drafty data, Map<String, Object> head);

    /**
     * Save message to database as a draft. Draft will not be sent to server until it status changes.
     *
     * @param topic topic which sent the message
     * @param data message data to save
     * @param head message headers
     * @return database ID of the message suitable for use in
     *  {@link #msgDelivered(Topic topic, long id, Date timestamp, int seq)}
     */
    long msgDraft(Topic topic, Drafty data, Map<String, Object> head);

    /**
     * Update message draft content without
     *
     * @param topic topic which sent the message
     * @param dbMessageId database ID of the message.
     * @param data updated content of the message. Must not be null.
     * @return true on success, false otherwise
     */
    @SuppressWarnings("UnusedReturnValue")
    boolean msgDraftUpdate(Topic topic, long dbMessageId, Drafty data);

    /**
     * Message is ready to be sent to the server.
     *
     * @param topic topic which sent the message
     * @param dbMessageId database ID of the message.
     * @param data updated content of the message. If null only status is updated.
     * @return true on success, false otherwise
     */
    @SuppressWarnings("UnusedReturnValue")
    boolean msgReady(Topic topic, long dbMessageId, Drafty data);

    /**
     * Message is being sent to the server.
     * @param topic topic which sent the message
     * @param dbMessageId database ID of the message.
     * @param sync true when the sync started, false when it's finished unsuccessfully.
     * @return true on success, false otherwise
     *
     */
    @SuppressWarnings("UnusedReturnValue")
    boolean msgSyncing(Topic topic, long dbMessageId, boolean sync);

    /**
     * Delete message by database id.
     */
    @SuppressWarnings("UnusedReturnValue")
    boolean msgDiscard(Topic topic, long dbMessageId);

    /**
     * Message delivered to the server and received a real seq ID.
     *
     * @param topic topic which sent the message.
     * @param dbMessageId database ID of the message.
     * @param timestamp server timestamp.
     * @param seq server-issued message seqId.
     * @return true on success, false otherwise     *
     */
    boolean msgDelivered(Topic topic, long dbMessageId, Date timestamp, int seq);
    /** Mark messages for deletion by range */
    @SuppressWarnings("UnusedReturnValue")
    boolean msgMarkToDelete(Topic topic, int fromId, int toId, boolean markAsHard);
    /** Mark messages for deletion by seq ID list */
    @SuppressWarnings("UnusedReturnValue")
    boolean msgMarkToDelete(Topic topic, MsgRange[] ranges, boolean markAsHard);
    /** Delete messages */
    @SuppressWarnings("UnusedReturnValue")
    boolean msgDelete(Topic topic, int delId, int fromId, int toId);
    /** Delete messages */
    @SuppressWarnings("UnusedReturnValue")
    boolean msgDelete(Topic topic, int delId, MsgRange[] ranges);
    /** Set recv value for a given subscriber */
    @SuppressWarnings("UnusedReturnValue")
    boolean msgRecvByRemote(Subscription sub, int recv);
    /** Set read value for a given subscriber */
    @SuppressWarnings("UnusedReturnValue")
    boolean msgReadByRemote(Subscription sub, int read);

    /** Retrieve a single message by database id */
    <T extends Message> T getMessageById(Topic topic, long dbMessageId);

    /** Get a list of unsent messages */
    <T extends Iterator<Message> & Closeable> T getQueuedMessages(Topic topic);
    /**
     * Get a list of pending delete message ranges.
     * @param topic topic where the messages were deleted.
     * @param hard set to <b>true</b> to fetch hard-deleted messages, soft-deleted otherwise.
     */
    MsgRange[] getQueuedMessageDeletes(Topic topic, boolean hard);

    interface Message {
        Map<String, Object> getHead();
        /** Get current message payload */
        Drafty getContent();
        /** Get current message unique ID (database ID) */
        long getId();

        /** Get Tinode seq Id of the message (different from database ID */
        int getSeqId();

        boolean isDraft();
        boolean isReady();
        boolean isDeleted();
        boolean isDeleted(boolean hard);
        boolean isSynced();
    }
}
