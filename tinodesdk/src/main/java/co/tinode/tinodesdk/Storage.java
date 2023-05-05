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
    void setMyUid(String uid, String hostURI);
    // Server-requested validation credentials for the currently active account.
    void updateCredentials(String[] credRequired);

    // Delete given account.
    void deleteAccount(String uid);

    String getServerURI();

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
    boolean topicDelete(Topic topic, boolean hard);

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
    Message msgReceived(Topic topic, Subscription sub, MsgServerData msg);

    /**
     * Save message to DB as "sending".
     *
     * @param topic topic which sent the message
     * @param data message data to save
     * @param head message headers
     * @return database ID of the message suitable for use in
     *  {@link #msgDelivered(Topic topic, long id, Date timestamp, int seq)}
     */
    Message msgSend(Topic topic, Drafty data, Map<String, Object> head);

    /**
     * Save message to database as a draft. Draft will not be sent to server until it status changes.
     *
     * @param topic topic which sent the message
     * @param data message data to save
     * @param head message headers
     * @return database ID of the message suitable for use in
     *  {@link #msgDelivered(Topic topic, long id, Date timestamp, int seq)}
     */
    Message msgDraft(Topic topic, Drafty data, Map<String, Object> head);

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
     * Failed to form or send message.
     *
     * @param topic topic which sent the message
     * @param dbMessageId database ID of the message.
     * @return true on success, false otherwise
     */
    @SuppressWarnings("UnusedReturnValue")
    boolean msgFailed(Topic topic, long dbMessageId);

    /**
     * Delete all failed messages in the given topis.
     *
     * @param topic topic which sent the message
     * @return true on success, false otherwise
     */
    @SuppressWarnings("UnusedReturnValue")
    boolean msgPruneFailed(Topic topic);

    /**
     * Remove message by database id.
     */
    @SuppressWarnings("UnusedReturnValue")
    boolean msgDiscard(Topic topic, long dbMessageId);

    /**
     * Remove message by seq ID.
     */
    @SuppressWarnings("UnusedReturnValue")
    boolean msgDiscardSeq(Topic topic, int seq);

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

    /**
     * Returns message ranges present in DB.
     *
     * @param topic topic to query.
     * @param ranges message ranges to test for presence in the local cache.
     * @return those message ranges which are present in the local cache.
     */
    MsgRange[] msgIsCached(Topic topic, MsgRange[] ranges);

    /** Get seq IDs of the stored messages as a MsgRange, inclusive-exclusive [low, hi) */
    MsgRange getCachedMessagesRange(Topic topic);
    /**
     * Get the ranges of the messages missing in cache, inclusive-exclusive [low, hi).
     * Returns empty array if all messages are present or no messages are found.
     */
    MsgRange[] getMissingRanges(Topic topic, int startFrom, int pageSize, boolean newer);
    /** Local user reported messages as read */
    @SuppressWarnings("UnusedReturnValue")
    boolean setRead(Topic topic, int read);
    /** Local user reported messages as received */
    @SuppressWarnings("UnusedReturnValue")
    boolean setRecv(Topic topic, int recv);

    /** Retrieve a single message by database id */
    <T extends Message> T getMessageById(long dbMessageId);

    /**
     * Retrieve a single message preview by database id.
     */
    <T extends Message> T getMessagePreviewById(long dbMessageId);

    /**
     * Get seq IDs of up to limit versions of the edited message with the given ID.
     * @param topic topic which sent the message.
     * @param seq ID of the edited message to get versions of.
     * @param limit the count of latest versions to get or all if limit is zero.
     * @return array of seq ID of edits ordered from newest to oldest.
     */
    int[] getAllMsgVersions(Topic topic, int seq, int limit);

    /**
     * Get the latest message in each topic. Caller must close the result after use.
     */
    <T extends Iterator<Message> & Closeable> T getLatestMessagePreviews();

    /** Get a list of unsent messages. Close the result after use. */
    <T extends Iterator<Message> & Closeable> T getQueuedMessages(Topic topic);

    /**
     * Get a list of pending delete message ranges.
     * @param topic topic where the messages were deleted.
     * @param hard set to <b>true</b> to fetch hard-deleted messages, soft-deleted otherwise.
     */
    MsgRange[] getQueuedMessageDeletes(Topic topic, boolean hard);

    /**
     * Retrieve a single message by topic and seq ID.
     */
    <T extends Message> T getMessageBySeq(Topic topic, int seq);

    interface Message {
        String getTopic();

        /** Get message headers */
        Map<String, Object> getHead();
        Object getHeader(String key);
        String getStringHeader(String key);
        Integer getIntHeader(String key);

        /** Get message payload */
        Drafty getContent();
        /** Set message payload */
        void setContent(Drafty content);

        /** Get current message unique ID (database ID) */
        long getDbId();

        /** Get Tinode seq Id of the message (different from database ID */
        int getSeqId();

        /** Get delivery status */
        int getStatus();

        boolean isMine();
        boolean isPending();
        boolean isReady();
        boolean isDeleted();
        boolean isDeleted(boolean hard);
        boolean isSynced();
    }
}
