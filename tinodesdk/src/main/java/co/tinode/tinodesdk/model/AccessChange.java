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

    public static AccessChange asWant(String want) {
        return new AccessChange(want, null);
    }
    public static AccessChange asGiven(String given) {
        return new AccessChange(null, given);
    }
}
