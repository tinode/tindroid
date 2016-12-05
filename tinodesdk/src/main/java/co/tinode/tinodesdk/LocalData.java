package co.tinode.tinodesdk;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Empty interface to indicate a set of value which are not synced with the server.
 * Used for persistent, such as local DB ids.
 */
public interface LocalData {

    interface Payload {
    }

    @JsonIgnore
    void setLocal(Payload value);

    @JsonIgnore
    Payload getLocal();
}
