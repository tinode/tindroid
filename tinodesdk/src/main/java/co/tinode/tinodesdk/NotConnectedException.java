package co.tinode.tinodesdk;

/**
 * Exception generated in response to a packet containing an error code.
 */
public class NotConnectedException extends IllegalStateException {

    public NotConnectedException() {
    }

    public NotConnectedException(String s) {
        super(s);
    }

    public NotConnectedException(String message, Throwable cause) {
        super(message, cause);
    }

    public NotConnectedException(Throwable cause) {
        super(cause);
    }
}
