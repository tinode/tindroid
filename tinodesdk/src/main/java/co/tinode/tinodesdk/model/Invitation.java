package co.tinode.tinodesdk.model;

/**
 * Created by gsokolov on 2/10/16.
 */
public class Invitation<T> {

    // Topic that user wants to subscribe to or is invited to
    public String topic;
    // User being subscribed
    public String user;
    // Type of this invite - InvJoin, InvAppr
    public String act;
    // Current state of the access mode
    public Acs acs;
    // Free-form payload
    public T info;
}
