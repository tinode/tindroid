package co.tinode.tinodesdk;

import com.fasterxml.jackson.databind.JavaType;

import co.tinode.tinodesdk.model.Invitation;
import co.tinode.tinodesdk.model.LastSeen;
import co.tinode.tinodesdk.model.MsgServerPres;
import co.tinode.tinodesdk.model.Subscription;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * MeTopic handles invites and manages contact list
 */
public class MeTopic<Pu,Pr,T> extends Topic<Pu,Pr,Invitation<T>> {

    protected Map<String,String> mP2PMap = new HashMap<>();

    public MeTopic(Tinode tinode, Listener<Pu,Pr,Invitation<T>> l) {
        super(tinode, Tinode.TOPIC_ME, l);
    }

    @Override
    public void setTypes(JavaType typeOfPu, JavaType typeOfPr, JavaType typeOfInviteInfo) {
        super.setTypes(typeOfPu, typeOfPr,
                Tinode.getTypeFactory().constructParametricType(Invitation.class, typeOfInviteInfo));
    }

    @Override
    public void setTypes(Class<?> typeOfPu, Class<?> typeOfPr, Class<?> typeOfInviteInfo) {
        this.setTypes(Tinode.getTypeFactory().constructType(typeOfPu),
                Tinode.getTypeFactory().constructType(typeOfPr),
                Tinode.getTypeFactory().constructType(typeOfInviteInfo));
    }
    
    @Override
    protected void processMetaSubs(Subscription<Pu,Pr>[] subs) {
        for (Subscription<Pu,Pr> sub : subs) {
            // Cache user in the topic as well.
            Subscription<Pu,Pr> cached = mSubs.get(sub.topic);
            if (cached != null) {
                cached.merge(sub);
            } else {
                mSubs.put(sub.topic, sub);
            }

            // TODO(gene): Save the object to global cache.

            if (sub.with != null && !sub.with.equals("")) {
                mP2PMap.put(sub.with, sub.topic);
            }

            if (mListener != null) {
                mListener.onMetaSub(sub);
            }
        }

        if (mListener != null) {
            mListener.onSubsUpdated();
        }

    }

    @Override
    protected void routePres(MsgServerPres pres) {
        // p2p topics are not getting what=on/off/upd updates,
        // such updates are sent with pres.topic set to user ID
        String contactName = mP2PMap.get(pres.src);
        contactName = contactName == null ? pres.src : contactName;
        Subscription sub = mSubs.get(contactName);
        if (sub != null) {
            switch(pres.what) {
                case "on": // topic came online
                    sub.online = true;
                    break;

                case "off": // topic went offline
                    sub.online = false;
                    if (sub.seen == null) {
                        sub.seen = new LastSeen();
                    }
                    sub.seen.when = new Date();
                    break;

                case "msg": // new message received
                    sub.seq = pres.seq;
                    break;

                case "upd": // desc updated
                    // TODO(gene): request updated description
                    break;

                case "ua": // user agent changed
                    sub.seen = new LastSeen(new Date(),pres.ua);
                    break;

                case "recv": // user's other session marked some messges as received
                    sub.recv = Math.max(sub.recv, pres.seq);
                    break;

                case "read": // user's other session marked some messages as read
                    sub.read = Math.max(sub.read, pres.seq);
                    break;

                case "del": // messages or topic deleted in other session
                    // TODO(gene): add handling for del
            }

            if (mListener != null) {
                mListener.onContactUpdate(pres.what, sub);
            }
        }

        if (mListener != null) {
            mListener.onPres(pres);
        }
    }
}
