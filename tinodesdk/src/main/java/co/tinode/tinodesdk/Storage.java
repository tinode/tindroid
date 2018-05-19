package co.tinode.tinodesdk;

import java.io.Closeable;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;

import co.tinode.tinodesdk.model.Drafty;
import co.tinode.tinodesdk.model.MsgServerData;
import co.tinode.tinodesdk.model.Subscription;

/**
 * Interface for implementing persistence.
 */
public interface Storage {
    String getMyUid();
    void setMyUid(String uid);

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
    <S> long subAdd(Topic topic, Subscription<S> sub);
    /** Update subscription in a generic topic */
    <S> boolean subUpdate(Topic topic, Subscription<S> sub);
    /** Add a new subscriber to topic. The new subscriber is being added locally. */
    <S> long subNew(Topic topic, Subscription<S> sub);
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

    // Message received
    long msgReceived(Topic topic, Subscription sub, MsgServerData msg);

    /** Message sent. Returns database ID of the message suitable for
     * use in msgDelivered
     * @param topic topic which sent the message
     * @param data message data to save
     * @return database ID of the message suitable for use in
     *  {@link #msgDelivered(Topic topic, long id, Date timestamp, int seq)}
     */
    long msgSend(Topic topic, Drafty data);
    /** Message delivered to the server and received a real seq ID */
    boolean msgDelivered(Topic topic, long id, Date timestamp, int seq);
    /** Mark messages for deletion by range */
    boolean msgMarkToDelete(Topic topic, int fromId, int toId);
    /** Mark messages for deletion by seq ID list */
    boolean msgMarkToDelete(Topic topic, int[] list);
    /** Delete messages */
    boolean msgDelete(Topic topic, int delId, int fromId, int toId);
    /** Delete messages */
    boolean msgDelete(Topic topic, int delId, int[] list);
    /** Set recv value for a given subscriber */
    boolean msgRecvByRemote(Subscription sub, int recv);
    /** Set read value for a given subscriber */
    boolean msgReadByRemote(Subscription sub, int read);
    /** Get a list of unsent messages */
    <R extends Iterator<Message> & Closeable> R getUnsentMessages(Topic topic);

    interface Message {
        /** Get current message payload */
        Object getContent();
        /** Get current message unique ID */
        long getId();
        /** Get current message header */
        Map<String,String> getHeader();
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
