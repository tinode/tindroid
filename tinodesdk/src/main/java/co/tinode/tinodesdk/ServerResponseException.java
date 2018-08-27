package co.tinode.tinodesdk;

/**
 * Exception generated in response to a packet containing an error code.
 */
public class ServerResponseException extends Exception {
    private int code;
    private String reason;

    ServerResponseException(int code, String text, String reason) {
        super(text);
        this.code = code;
        this.reason = reason;
    }

    @Override
    public String getMessage() {
        return super.getMessage() + " (" + code + ")";
    }

    public int getCode() {
        return code;
    }

    public String getReason() {
        return reason;
    }
}
