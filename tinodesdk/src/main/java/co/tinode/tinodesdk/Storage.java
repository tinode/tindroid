package co.tinode.tinodesdk;

import java.util.Collection;
import java.util.Date;

import co.tinode.tinodesdk.model.Description;
import co.tinode.tinodesdk.model.MsgServerData;
import co.tinode.tinodesdk.model.MsgSetMeta;
import co.tinode.tinodesdk.model.Subscription;

/**
 * Interface for implementing persistence.
 */
public interface Storage {
    String getMyUid();
    boolean setMyUid(String uid);

    // Fetch all topics
    Topic[] topicGetAll();
    // Add new topic
    long topicAdd(Topic topic);
    /** Incoming change to topic description: synchronize already mutated topic from memory to DB */
    boolean topicUpdate(Topic topic);
    /** Delete topic */
    boolean topicDelete(Topic topic);
    /** Local user reported messages as read */
    boolean setRead(Topic topic, int read);
    /** Local user reported messages as received */
    boolean setRecv(Topic topic, int recv);

    /** New subscription in a generic topic */
    <Pu,Pr> long subAdd(Topic topic, Subscription<Pu,Pr> sub);
    /** Update subscription in a generic topic */
    <Pu,Pr> boolean subUpdate(Topic topic, Subscription<Pu,Pr> sub);

    /** Get a list o topic subscriptions from DB. */
    Collection<Subscription> getSubscriptions(Topic topic);

    // Message received
    <T> long msgReceived(Subscription sub, MsgServerData<T> msg);
    // Message sent
    <T> long msgSend(String topicName, T data);
    /** Message delivered to the server and received a real seq ID */
    boolean msgDelivered(long id, Date timestamp, int seq);
    /** Mark messages for deletion */
    int msgMarkToDelete(Topic topic, int before);
    /** Delete messages */
    int msgDelete(Topic topic, int before);
    /** Set recv value for a given subscriber */
    boolean msgRecvByRemote(Subscription sub, int recv);
    /** Set read value for a given subscriber */
    boolean msgReadByRemote(Subscription sub, int read);
}
