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
    // Delete topic
    boolean topicDelete(String name);

    /**
     * Outgoing update request
     */
    boolean topicUpdate(Topic topic, Date timestamp, MsgSetMeta meta);
    /**
     * Incoming change to topic description: update or create new
     */
    boolean topicUpsert(Topic topic, Date timestamp, Description desc);
    // Incoming change to 'me' subscriptions
    <Pu,Pr> boolean topicUpsert(String name, Date timestamp, Subscription<Pu,Pr>[] subs);
    // Incoming change to subscriptions to a generic topic
    <Pu,Pr> boolean userUpsert(String name, Date timestamp, Subscription<Pu,Pr>[] subs);
    // Read subscriptions
    Collection<Subscription> getSubscriptions(Topic topic);

    // Message received
    <T> long msgReceived(Subscription sub, MsgServerData<T> msg);
    // Message sent
    <T> long msgSend(String topicName, T data);
    // Message delivered to server
    boolean msgDelivered(long id, Date timestamp, int seq);
    // Mark messages for deletion
    int msgMarkToDelete(Topic topic, int before);
    // Delete messages
    int msgDelete(Topic topic, int before);
    // Set recv value
    int setRecv(Subscription sub, int recv);
    // Set read value
    int setRead(Subscription sub, int read);
}
