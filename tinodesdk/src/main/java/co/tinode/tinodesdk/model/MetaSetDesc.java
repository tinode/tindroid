package co.tinode.tinodesdk.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Topic intiation parameters
 */
public class MetaSetDesc<P,R> {
    public Defacs defacs;
    @JsonProperty("public")
    public P pub;
    @JsonProperty("private")
    public R priv;

    public MetaSetDesc() {}

    public MetaSetDesc(P pub, R priv) {
        this.pub = pub;
        this.priv = priv;
    }

    public MetaSetDesc(Defacs da) {
        this.defacs = da;
    }

    public MetaSetDesc(String auth, String anon) {
        this.defacs = new Defacs(auth, anon);
    }
}
