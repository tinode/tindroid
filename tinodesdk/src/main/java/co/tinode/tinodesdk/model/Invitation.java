package co.tinode.tinodesdk.model;

import java.io.Serializable;

/**
 * Container for a generic invitation.
 */
public class Invitation<T> implements Serializable {

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

    public Invitation() {}
}
