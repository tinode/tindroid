package co.tinode.tinodesdk;

import java.util.Collection;
import java.util.Date;

import co.tinode.tinodesdk.model.Description;
import co.tinode.tinodesdk.model.Invitation;
import co.tinode.tinodesdk.model.MsgServerData;
import co.tinode.tinodesdk.model.MsgSetMeta;
import co.tinode.tinodesdk.model.Subscription;

/**
 * Interface for implementing persistence.
 */
public interface Storage {
    String getMyUid();
    void setMyUid(String uid);

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
    <Pu,Pr> long subAdd(Topic topic, Subscription<Pu,Pr> sub);
    /** Update subscription in a generic topic */
    <Pu,Pr> boolean subUpdate(Topic topic, Subscription<Pu,Pr> sub);
    /** Add a new subscriber to topic. The new subscriber is being added locally. */
    <Pu,Pr> long subNew(Topic topic, Subscription<Pu,Pr> sub);


    /** Get a list o topic subscriptions from DB. */
    Collection<Subscription> getSubscriptions(Topic topic);

    // Message received
    <T> long msgReceived(Topic topic, Subscription sub, MsgServerData<T> msg);
    // Invitation message received
    <Pu,T> long inviteReceived(Topic me, MsgServerData<Invitation<Pu,T>> msg);

    // Message sent
    <T> long msgSend(Topic topic, T data);
    /** Message delivered to the server and received a real seq ID */
    boolean msgDelivered(long id, Date timestamp, int seq);
    /** Mark messages for deletion */
    boolean msgMarkToDelete(Topic topic, int before);
    /** Mark messages for deletion by seq ID list */
    boolean msgMarkToDelete(Topic topic, int[] list);
    /** Delete messages */
    boolean msgDelete(Topic topic, int before);
    /** Delete messages */
    boolean msgDelete(Topic topic, int[] list);
    /** Set recv value for a given subscriber */
    boolean msgRecvByRemote(Subscription sub, int recv);
    /** Set read value for a given subscriber */
    boolean msgReadByRemote(Subscription sub, int read);

    /**
     * Min and max values.
     */
    class Range {
        public int min;
        public int max;

        public Range(int from, int to) {
            min = from;
            max = to;
        }
    }
}
