package co.tinode.tinodesdk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;

import co.tinode.tinodesdk.model.Description;
import co.tinode.tinodesdk.model.Drafty;
import co.tinode.tinodesdk.model.MetaSetDesc;
import co.tinode.tinodesdk.model.MsgServerData;
import co.tinode.tinodesdk.model.MsgServerMeta;
import co.tinode.tinodesdk.model.MsgSetMeta;
import co.tinode.tinodesdk.model.PrivateType;
import co.tinode.tinodesdk.model.ServerMessage;
import co.tinode.tinodesdk.model.Subscription;
import co.tinode.tinodesdk.model.TheCard;

/**
 * Communication topic: a P2P or Group.
 */
public class ComTopic<DP extends TheCard> extends Topic<DP,PrivateType,DP,PrivateType> {
    public ComTopic(Tinode tinode, Subscription<DP,PrivateType> sub) {
        super(tinode, sub);
    }

    public ComTopic(Tinode tinode, String name, Description<DP,PrivateType> desc) {
        super(tinode, name, desc);
    }

    public ComTopic(Tinode tinode, String name, Listener<DP,PrivateType,DP,PrivateType> l) {
        super(tinode, name, l);
    }

    public ComTopic(Tinode tinode, Listener l, boolean isChannel) {
        //noinspection unchecked
        super(tinode, l, isChannel);
    }

    /**
     * Subscribe to topic.
     */
    public PromisedReply<ServerMessage> subscribe() {
        if (isNew()) {
            MetaSetDesc<DP, PrivateType> desc = new MetaSetDesc<>(mDesc.pub, mDesc.priv);
            if (mDesc.pub != null) {
                desc.attachments = mDesc.pub.getPhotoRefs();
            }
            return subscribe(new MsgSetMeta.Builder<DP, PrivateType>().with(desc).with(mTags).build(), null);
        }
        return super.subscribe();
    }

    public void setComment(String comment) {
        PrivateType p = super.getPriv();
        if (p == null) {
            p = new PrivateType();
        }
        p.setComment(comment);
        super.setPriv(p);
    }

    /**
     * Read comment from the Private field.
     * @return comment or null if comment or Private is not set.
     */
    public String getComment() {
        PrivateType p = super.getPriv();
        return p != null ? p.getComment() : null;
    }

    /**
     * Set message as pinned or unpinned by adding it to aux.pins array.
     *
     * @param seq - seq ID of the message to pin or un-pin.
     * @param pin - true to pin the message, false to un-pin.
     *
     * @return Promise to be resolved/rejected when the server responds to request.
     */
    public PromisedReply<ServerMessage> pinMessage(int seq, boolean pin) {
        Object val = getAux("pins");
        List<Integer> pinned;
        if (val instanceof List) {
            // Creating a copy, otherwise changes here will affect values saved in topic.
            pinned = new ArrayList<>((List) val);
        } else {
            pinned = new ArrayList<>();
        }

        boolean changed = false;
        if (pin) {
            if (!pinned.contains(seq)) {
                changed = true;
                if (pinned.size() == Tinode.MAX_PINNED_COUNT) {
                    pinned.remove(0);
                }
                pinned.add(seq);
            }
        } else {
            changed = pinned.removeIf(pseq -> pseq == seq);
        }

        if (changed) {
            Map<String, Object> aux = new HashMap<>();
            aux.put("pins", !pinned.isEmpty() ? pinned : Tinode.NULL_VALUE);
            return setMeta(new MsgSetMeta.Builder<DP, PrivateType>().with(aux).build());
        }

        return new PromisedReply<>((ServerMessage) null);
    }

    /**
     * Check if the message with a given seqID is pinned.
     * @param seq seqID of the message to check.
     * @return true if the message is pinned, false otherwise.
     */
    public boolean isPinned(int seq) {
        Object val = getAux("pins");
        return val instanceof List && ((List) val).contains(seq);
    }

    /**
     * Get list of pinned seqIDs.
     * @return array of pinned seqIDs or null if there are no pinned messages.
     */
    public int[] getPinned() {
        Object val = getAux("pins");
        if (val instanceof List) {
            int[] pinned = ((List<?>) val).stream()
                    .mapToInt((ToIntFunction<Object>) value ->
                            value instanceof Number ? ((Number) value).intValue() : 0)
                    .filter(value -> value > 0)
                    .toArray();
            return pinned.length > 0 ? pinned : null;
        }
        return null;
    }

    /**
     * Get hash code of the pinned seqIDs.
     * Changes every time the pinned seqIDs change.
     * @return hash code of the pinned seqIDs.
     */
    public int getPinnedHash() {
        Object val = getAux("pins");
        if (val instanceof List) {
            return val.hashCode();
        }
        return 0;
    }

    /**
     * Get count of pinned messages. The count could be wrong of the list of
     * pinned messages contains elements other than Number.
     * @return count of pinned messages.
     */
    public int pinnedCount() {
        Object val = getAux("pins");
        if (val instanceof List) {
            return ((List) val).size();
        }
        return 0;
    }

    /**
     * Checks if the topic is archived. Not all topics support archiving.
     * @return true if the topic is archived, false otherwise.
     */
    @Override
    public boolean isArchived() {
        PrivateType p = super.getPriv();
        Boolean arch = (p != null ? p.isArchived() : Boolean.FALSE);
        return arch != null ? arch : false;
    }

    /**
     * Checks if the topic is a channel.
     * @return true if the topic is a channel, false otherwise.
     */
    public boolean isChannel() {
        return mName != null && isChannel(mName);
    }

    /**
     * Checks if the topic can be accessed as channel.
     * @return true if the topic is accessible as a channel.
     */
    public boolean hasChannelAccess() {
        return mDesc.chan;
    }

    /**
     * Sets flag that the topic is accessible as a channel.
     * @param access <code>true</code> to indicate that the topic is accessible as a channel.
     */
    public void setHasChannelAccess(boolean access) {
        mDesc.chan = access;
    }

    /**
     * In P2P topics get peer's subscription.
     *
     * @return peer's subscription.
     */
    public Subscription<DP, PrivateType> getPeer() {
        if (isP2PType()) {
            return super.getSubscription(getName());
        }

        return null;
    }

    /**
     * Archive topic by issuing {@link Topic#setMeta} with priv set to {arch: true/false}.
     *
     * @throws NotSubscribedException if the client is not subscribed to the topic
     * @throws NotConnectedException if there is no connection to the server
     */
    public PromisedReply<ServerMessage> updateArchived(final boolean arch) {
        PrivateType priv = new PrivateType();
        priv.setArchived(arch);
        return setMeta(new MsgSetMeta.Builder<DP, PrivateType>().with(new MetaSetDesc<>(null, priv)).build());
    }

    public static class ComListener<DP> implements Listener<DP,PrivateType,DP,PrivateType> {
        /** {meta} message received */
        public void onMeta(MsgServerMeta<DP,PrivateType,DP,PrivateType> meta) {}
        /** Called by MeTopic when topic descriptor as contact is updated */
        public void onContUpdate(Subscription<DP,PrivateType> sub) {}
    }

    @Override
    protected void routeData(MsgServerData data) {
        if (data.head != null && data.content != null) {
            // Rewrite VC body with info from the headers.
            try {
                String state = (String) data.head.get("webrtc");
                String mime = (String) data.head.get("mime");
                if (state != null && Drafty.MIME_TYPE.equals(mime)) {
                    boolean outgoing = ((!isChannel() && data.from == null) || mTinode.isMe(data.from));
                    Drafty.updateVideoEnt(data.content, data.head, !outgoing);
                }
            } catch (ClassCastException ignored) {}
        }
        super.routeData(data);
    }
}
