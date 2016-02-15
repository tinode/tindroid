package co.tinode.tinodesdk;

import com.fasterxml.jackson.databind.JavaType;

import co.tinode.tinodesdk.model.Invitation;
import co.tinode.tinodesdk.model.Subscription;

/**
 * Created by gsokolov on 2/10/16.
 */
public class MeTopic<Pu,Pr,T> extends Topic<Pu,Pr,Invitation<T>> {

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

            if (mListener != null) {
                mListener.onMetaSub(sub);
            }
        }

        if (mListener != null) {
            mListener.onSubsUpdated();
        }

    }
}
