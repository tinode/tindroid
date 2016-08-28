package co.tinode.tinodesdk.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Topic intiation parameters
 */

public class SetDesc<Pu,Pr> {
    public Defacs defacs;
    @JsonProperty("public")
    public Pu pub;
    @JsonProperty("private")
    public Pr priv;

    public SetDesc() {}

    public SetDesc(Pu pub, Pr priv) {
        this.pub = pub;
        this.priv = priv;
    }
}
