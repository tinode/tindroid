package co.tinode.tinodesdk;

import com.fasterxml.jackson.databind.JavaType;

import co.tinode.tinodesdk.model.Invitation;

/**
 * Created by gsokolov on 2/10/16.
 */
public class MeTopic<Pu,Pr,T> extends Topic<Pu,Pr,Invitation<T>> {

    public MeTopic(Tinode tinode, JavaType typeOfInviteInfo, Listener<Invitation<T>> l) {
        super(tinode,
                Tinode.TOPIC_ME,
                Tinode.getTypeFactory().constructParametricType(Invitation.class, typeOfInviteInfo),
                l);
    }

    public MeTopic(Tinode tinode, Class<?> typeOfInviteInfo, Listener<Invitation<T>> l) {
        this(tinode, Tinode.getTypeFactory().constructType(typeOfInviteInfo), l);
    }
}
