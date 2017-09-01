package co.tinode.tinodesdk.model;

import java.io.Serializable;

/**
 * Client message:
 *
 * 	Hi    *MsgClientHi    `json:"hi"`
 * Acc   *MsgClientAcc   `json:"acc"`
 * Login *MsgClientLogin `json:"login"`
 * Sub   *MsgClientSub   `json:"sub"`
 * Leave *MsgClientLeave `json:"leave"`
 * Pub   *MsgClientPub   `json:"pub"`
 * Get   *MsgClientGet   `json:"get"`
 * Set   *MsgClientSet   `json:"set"`
 * Del   *MsgClientDel   `json:"del"`
 * Note  *MsgClientNote  `json:"note"`
 */

public class ClientMessage<Pu,Pr> implements Serializable {
    public MsgClientHi hi;
    public MsgClientAcc<Pu,Pr> acc;
    public MsgClientLogin login;
    public MsgClientSub sub;
    public MsgClientLeave leave;
    public MsgClientPub pub;
    public MsgClientGet get;
    public MsgClientSet set;
    public MsgClientDel del;
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
    public ClientMessage(MsgClientSub sub) {
        this.sub = sub;
    }
    public ClientMessage(MsgClientLeave leave) {
        this.leave = leave;
    }
    public ClientMessage(MsgClientPub pub) {
        this.pub = pub;
    }
    public ClientMessage(MsgClientGet get) {
        this.get = get;
    }
    public ClientMessage(MsgClientSet set) {
        this.set = set;
    }
    public ClientMessage(MsgClientDel del) {
        this.del = del;
    }
    public ClientMessage(MsgClientNote note) {
        this.note = note;
    }
}