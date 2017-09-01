package co.tinode.tinodesdk.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;

/**
 * Container for a generic invitation.
 */
public class Announcement implements Serializable {

    private static final String INVITE = "inv";
    private static final String APPROVE = "appr";
    private static final String UPDATED = "upd";
    private static final String DELETED = "del";

    // Topic that user wants to subscribe to or is invited to
    public String topic;
    // User being subscribed
    public String user;
    // Type of this invite -
    public String act;
    // Current state of the access mode
    public Acs acs;
    // Mime-type of the payload. Null means "text/plain".
    public String mime;


    public Announcement() {}
}
