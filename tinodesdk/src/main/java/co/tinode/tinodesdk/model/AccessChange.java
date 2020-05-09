package co.tinode.tinodesdk.model;

import java.io.Serializable;

public class AccessChange implements Serializable {
    public String want;
    public String given;

    public AccessChange() {
    }

    public AccessChange(String want, String given) {
        this.want = want;
        this.given = given;
    }

    @SuppressWarnings("NullableProblems")
    @Override
    public String toString() {
        return "{\"given\":" + (given != null ? " \"" + given + "\"" : " null") +
                ", \"want\":" + (want != null ? " \"" + want + "\"" : " null}");
    }
}
