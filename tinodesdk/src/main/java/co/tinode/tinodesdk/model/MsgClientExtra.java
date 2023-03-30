package co.tinode.tinodesdk.model;

// MsgClientExtra is not a stand-alone message but extra data which augments the main payload.
public class MsgClientExtra {
    public MsgClientExtra() {
        attachments = null;
    }

    public MsgClientExtra(String[] attachments) {
        this.attachments = attachments;
    }

    // Array of out-of-band attachments which have to be exempted from GC.
    public final String[] attachments;
}
