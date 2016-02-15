package co.tinode.tindroid;

import co.tinode.tinodesdk.Tinode;

/**
 * Created by gsokolov on 2/11/16.
 */
public class InmemoryCache {
    public static Tinode sTinode;

    public static String sHost = "10.0.2.2:6060"; // local
    //public static String sHost = "api.tinode.co"; // remote

    static {
        sTinode = new Tinode("Tindroid", sHost, "AQEAAAABAAD_rAp4DJh05a1HAwFT3A6K");
        // Public, Private, Content
        sTinode.setDefaultTypes(VCard.class, String.class, String.class);
    }

    public static Tinode getTinode() {
        return sTinode;
    }
}
