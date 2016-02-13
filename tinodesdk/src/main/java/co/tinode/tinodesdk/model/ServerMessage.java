package co.tinode.tinodesdk.model;

/**
 * Created by gsokolov on 2/2/16.
 */
public class ServerMessage<T,Pu,Pr> {
    public MsgServerData<T> data;
    public MsgServerMeta<Pu,Pr> meta;
    public MsgServerCtrl ctrl;
    public MsgServerPres pres;
    public MsgServerInfo info;

    public ServerMessage() {}
    public ServerMessage(MsgServerData<T> data) {
        this.data = data;
    }
    public ServerMessage(MsgServerMeta<Pu,Pr> meta) {
        this.meta = meta;
    }
    public ServerMessage(MsgServerCtrl ctrl) {
        this.ctrl = ctrl;
    }
    public ServerMessage(MsgServerPres pres) {
        this.pres = pres;
    }
    public ServerMessage(MsgServerInfo info) {
        this.info = info;
    }

    public boolean isValid() {
        int count = 0;
        if (data != null) count ++;
        if (meta != null) count ++;
        if (ctrl != null) count ++;
        if (pres != null) count ++;
        if (info != null) count ++;

        return count == 1;
    }

}
