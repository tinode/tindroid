package co.tinode.tinodesdk;

import java.util.Date;

import co.tinode.tinodesdk.model.LastSeen;

/**
 * Topic subscription.
 */
public class TopicSub<Pu,Pr> {
    // Topic name, only when querying 'me'
    public String topic;
    // User uid, only when querying non-'me'
    public String user;
    // Timestamp of last update
    public Date updated;
    // If the user is currently online
    public boolean online;
    // cumulative access mode (mode.Want & mode.Given)
    public String mode;
    // ID of the message reported by the client as read
    public int read;
    // ID of the message reported by the client as received
    public int recv;

    // Topic's public data for 'me', user's public otherwise
    public Pu pub;
    // User's private
    public Pr priv;

    // All following makes sense only in context of getting user's 'me' subscriptions
    // TODO(gene): move to a subclass
    // ID of the last {data} message in a topic
    public int seq;
    // Messages are deleted up to this ID
    public int clear;

    // P2P topics only
    // ID of the other user
    public String with;
    // Other user's last online timestamp & user agent
    public LastSeen seen;
}
