package co.tinode.tinodesdk;

/**
 * Exception generated in response to a packet containing an error code.
 */
public class ServerResponseException extends Exception {
    private int code;

    public ServerResponseException(int code, String text) {
        super(text);
        this.code = code;
    }
}
