package co.tinode.tinodesdk;

import java.io.Closeable;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import co.tinode.tinodesdk.model.Drafty;
import co.tinode.tinodesdk.model.MsgServerData;
import co.tinode.tinodesdk.model.Subscription;

/**
 * Interface for implementing persistence.
 */
public interface Storage {
    String getMyUid();
    void setMyUid(String uid);

    String getDeviceToken();
    void updateDeviceToken(String token);

    void logout();

    // Server time minus local time
    void setTimeAdjustment(long adjustment);

    boolean isReady();

    // Fetch all topics
    Topic[] topicGetAll(Tinode tinode);
    // Add new topic
    long topicAdd(Topic topic);
    /** Incoming change to topic description: the already mutated topic in memory is synchronized to DB */
    boolean topicUpdate(Topic topic);
    /** Delete topic */
    boolean topicDelete(Topic topic);

    /** Get seq IDs of the stored messages as a Range */
    Range getCachedMessagesRange(Topic topic);
    /** Local user reported messages as read */
    boolean setRead(Topic topic, int read);
    /** Local user reported messages as received */
    boolean setRecv(Topic topic, int recv);

    /** Add subscription in a generic topic. The subscription is received from the server. */
    long subAdd(Topic topic, Subscription sub);
    /** Update subscription in a generic topic */
    boolean subUpdate(Topic topic, Subscription sub);
    /** Add a new subscriber to topic. The new subscriber is being added locally. */
    long subNew(Topic topic, Subscription sub);
    /** Delete existing subscription */
    boolean subDelete(Topic topic, Subscription sub);

    /** Get a list o topic subscriptions from DB. */
    Collection<Subscription> getSubscriptions(Topic topic);

    /** Read user description */
    User userGet(String uid);
    /** Insert new user */
    long userAdd(User user);
    /** Update existing user */
    boolean userUpdate(User user);

    /**
     * Message received from the server.
     */
    long msgReceived(Topic topic, Subscription sub, MsgServerData msg);

    /**
     * Save message to DB as queued or synced.
     *
     * @param topic topic which sent the message
     * @param data message data to save
     * @return database ID of the message suitable for use in
     *  {@link #msgDelivered(Topic topic, long id, Date timestamp, int seq)}
     */
    long msgSend(Topic topic, Drafty data);

    /**
     * Save message to database as a draft. Draft will not be sent to server until it status changes.
     *
     * @param topic topic which sent the message
     * @param data message data to save
     * @return database ID of the message suitable for use in
     *  {@link #msgDelivered(Topic topic, long id, Date timestamp, int seq)}
     */
    long msgDraft(Topic topic, Drafty data);

    /**
     * Update message draft content without
     *
     * @param topic topic which sent the message
     * @param dbMessageId database ID of the message.
     * @param data updated content of the message. Must not be null.
     * @return true on success, false otherwise
     */
    boolean msgDraftUpdate(Topic topic, long dbMessageId, Drafty data);

    /**
     * Message is ready to be sent to the server.
     *
     * @param topic topic which sent the message
     * @param dbMessageId database ID of the message.
     * @param data updated content of the message. If null only status is updated.
     * @return true on success, false otherwise
     */
    boolean msgReady(Topic topic, long dbMessageId, Drafty data);

    /**
     * Delete message by database id.
     */
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
    boolean msgMarkToDelete(Topic topic, int fromId, int toId, boolean markAsHard);
    /** Mark messages for deletion by seq ID list */
    boolean msgMarkToDelete(Topic topic, List<Integer> list, boolean markAsHard);
    /** Delete messages */
    boolean msgDelete(Topic topic, int delId, int fromId, int toId);
    /** Delete messages */
    boolean msgDelete(Topic topic, int delId, List<Integer> list);
    /** Set recv value for a given subscriber */
    boolean msgRecvByRemote(Subscription sub, int recv);
    /** Set read value for a given subscriber */
    boolean msgReadByRemote(Subscription sub, int read);

    /** Retrieve a single message by database id */
    <T extends Message> T getMessageById(Topic topic, long dbMessageId);

    /** Get a list of unsent messages */
    <T extends Iterator<Message> & Closeable> T getQueuedMessages(Topic topic);
    /**
     * Get a list of pending delete message seq Ids.
     * @param topic topic where the messages were deleted.
     * @param hard set to <b>true</b> to fetch hard-deleted messages, soft-deleted otherwise.
     */
    List<Integer> getQueuedMessageDeletes(Topic topic, boolean hard);

    interface Message {
        /** Get current message payload */
        Object getContent();
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

    /**
     * Min and max values.
     */
    class Range {
        int min;
        int max;

        public Range(int from, int to) {
            min = from;
            max = to;
        }
    }
}
