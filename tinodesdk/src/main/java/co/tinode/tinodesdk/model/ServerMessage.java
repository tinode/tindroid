package co.tinode.tinodesdk.model;

import java.io.Serializable;

/**
 * Combined server message
 */
public class ServerMessage<DP,DR,SP,SR> implements Serializable {
    public static final int STATUS_CONTINUE               = 100; // RFC 7231, 6.2.1
    public static final int STATUS_SWITCHING_PROTOCOLS    = 101; // RFC 7231, 6.2.2
    public static final int STATUS_PROCESSING             = 102; // RFC 2518, 10.1

    public static final int STATUS_OK                     = 200; // RFC 7231, 6.3.1
    public static final int STATUS_CREATED                = 201; // RFC 7231, 6.3.2
    public static final int STATUS_ACCEPTED               = 202; // RFC 7231, 6.3.3
    public static final int STATUS_NON_AUTHORITATIVE_INFO = 203; // RFC 7231, 6.3.4
    public static final int STATUS_NO_CONTENT             = 204; // RFC 7231, 6.3.5
    public static final int STATUS_RESET_CONTENT          = 205; // RFC 7231, 6.3.6
    public static final int STATUS_PARTIAL_CONTENT        = 206; // RFC 7233, 4.1
    public static final int STATUS_MULTI_STATUS           = 207; // RFC 4918, 11.1
    public static final int STATUS_ALREADY_REPORTED       = 208; // RFC 5842, 7.1

    public static final int STATUS_MULTIPLE_CHOICES       = 300; // RFC 7231, 6.4.1
    public static final int STATUS_MOVED_PERMANENTLY      = 301; // RFC 7231, 6.4.2
    public static final int STATUS_FOUND                  = 302; // RFC 7231, 6.4.3
    public static final int STATUS_SEE_OTHER              = 303; // RFC 7231, 6.4.4
    public static final int STATUS_NOT_MODIFIED           = 304; // RFC 7232, 4.1
    public static final int STATUS_USE_PROXY              = 305; // RFC 7231, 6.4.5

    public static final int STATUS_BAD_REQUEST            = 400; // RFC 7231, 6.5.1
    public static final int STATUS_UNAUTHORIZED           = 401; // RFC 7235, 3.1
    public static final int STATUS_FORBIDDEN              = 403; // RFC 7231, 6.5.3
    public static final int STATUS_NOT_FOUND              = 404; // RFC 7231, 6.5.4
    public static final int STATUS_METHOD_NOT_ALLOWED     = 405; // RFC 7231, 6.5.5
    public static final int STATUS_NOT_ACCEPTABLE         = 406; // RFC 7231, 6.5.6
    public static final int STATUS_REQUEST_TIMEOUT        = 408; // RFC 7231, 6.5.7
    public static final int STATUS_CONFLICT               = 409; // RFC 7231, 6.5.8
    public static final int STATUS_GONE                   = 410; // RFC 7231, 6.5.9

    public static int STATUS_INTERNAL_SERVER_ERROR  = 500; // RFC 7231, 6.6.1
    public static int STATUS_NOT_IMPLEMENTED        = 501; // RFC 7231, 6.6.2
    public static int STATUS_BAD_GATEWAY            = 502; // RFC 7231, 6.6.3
    public static int STATUS_SERVICE_UNAVAILABLE    = 503; // RFC 7231, 6.6.4
    public static int STATUS_GATEWAY_TIMEOUT        = 504; // RFC 7231, 6.6.5
    public static int STATUS_HTTP_VERSION_NOT_SUPPORTED = 505; // RFC 7231, 6.6.6

    public MsgServerData data;
    public MsgServerMeta<DP,DR,SP,SR> meta;
    public MsgServerCtrl ctrl;
    public MsgServerPres pres;
    public MsgServerInfo info;

    public ServerMessage() {}
    public ServerMessage(MsgServerData data) {
        this.data = data;
    }
    public ServerMessage(MsgServerMeta<DP,DR,SP,SR> meta) {
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
