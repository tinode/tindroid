package co.tinode.tinodesdk;

/**
 * Exception generated in response to a packet containing an error code.
 */
public class NotConnectedException extends IllegalStateException {

    public NotConnectedException() {
        this((Throwable) null);
    }

    public NotConnectedException(String s) {
        this(s, null);
    }

    public NotConnectedException(String message, Throwable cause) {
        super(message, cause);
    }

    public NotConnectedException(Throwable cause) {
        this("Not connected", cause);
    }
}
