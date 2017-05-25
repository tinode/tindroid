package co.tinode.tinodesdk.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Topic intiation parameters
 */
public class MetaSetDesc<Pu,Pr> {
    public Defacs defacs;
    @JsonProperty("public")
    public Pu pub;
    @JsonProperty("private")
    public Pr priv;

    public MetaSetDesc() {}

    public MetaSetDesc(Pu pub, Pr priv) {
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
