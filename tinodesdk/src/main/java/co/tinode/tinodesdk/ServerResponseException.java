package co.tinode.tinodesdk;

/**
 * Created by gsokolov on 2/11/16.
 */
public class ServerResponseException extends Exception {
    private int code;

    public ServerResponseException(int code, String text) {
        super(text);
        this.code = code;
    }
}
