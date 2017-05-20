package co.tinode.tinodesdk;

/**
 * Exception generated in response to a packet containing an error code.
 */
public class ServerResponseException extends Exception {
    private int code;

    ServerResponseException(int code, String text) {
        super(text);
        this.code = code;
    }

    @Override
    public String getMessage() {
        return super.getMessage() + " (" + code + ")";
    }

    public int getCode() {
        return code;
    }
}
