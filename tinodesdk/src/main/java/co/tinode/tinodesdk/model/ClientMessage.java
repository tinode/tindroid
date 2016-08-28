package co.tinode.tinodesdk.model;

/**
 * Created by gene on 31/01/16.
 */

public class ClientMessage<Pu,Pr,T> {
    public MsgClientHi hi;
    public MsgClientAcc<Pu,Pr> acc;
    public MsgClientLogin login;
    public MsgClientPub<T> pub;
    public MsgClientSub sub;
    public MsgClientLeave leave;
    public MsgClientNote note;

    public ClientMessage() {
    }
    public ClientMessage(MsgClientHi hi) {
        this.hi = hi;
    }
    public ClientMessage(MsgClientAcc<Pu,Pr> acc) {
        this.acc = acc;
    }
    public ClientMessage(MsgClientLogin login) {
        this.login = login;
    }
    public ClientMessage(MsgClientPub<T> pub) {
        this.pub = pub;
    }
    public ClientMessage(MsgClientSub sub) {
        this.sub = sub;
    }
    public ClientMessage(MsgClientLeave leave) {
        this.leave = leave;
    }
    public ClientMessage(MsgClientNote note) {
        this.note = note;
    }
}