package co.tinode.tinodesdk;

import co.tinode.tinodesdk.model.Description;
import co.tinode.tinodesdk.model.MsgServerMeta;
import co.tinode.tinodesdk.model.PrivateType;
import co.tinode.tinodesdk.model.Subscription;

/* Communication topic: a P2P or Group.
 */
public class ComTopic<DP> extends Topic<DP,PrivateType,DP,PrivateType> {

    public ComTopic(Tinode tinode, Subscription<DP,PrivateType> sub) {
        super(tinode, sub);
    }

    public ComTopic(Tinode tinode, String name, Description<DP,PrivateType> desc) {
        super(tinode, name, desc);
    }

    public ComTopic(Tinode tinode, String name, Listener<DP,PrivateType,DP,PrivateType> l) {
        super(tinode, name, l);
    }

    public ComTopic(Tinode tinode, Listener l) {
        super(tinode, l);
    }

    public void setPriv(String comment) {
        PrivateType p = super.getPriv();
        if (p == null) {
            p = new PrivateType();
        }
        p.setComment(comment);
        super.setPriv(p);
    }

    public String getComment() {
        PrivateType p = super.getPriv();
        return p != null ? p.getComment() : null;
    }

    public static class ComListener<DP> extends Listener<DP,PrivateType,DP,PrivateType> {
        /** {meta} message received */
        public void onMeta(MsgServerMeta<DP,PrivateType,DP,PrivateType> meta) {}
        /** {meta what="sub"} message received, and this is one of the subs */
        public void onMetaSub(Subscription<DP,PrivateType> sub) {}
        /** {meta what="desc"} message received */
        public void onMetaDesc(Description<DP,PrivateType> desc) {}
        /** Called by MeTopic when topic descriptor as contact is updated */
        public void onContUpdate(Subscription<DP,PrivateType> sub) {}
    }
}
