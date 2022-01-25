package co.tinode.tinodesdk.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_DEFAULT;

/**
 * Topic initiation parameters
 */
@JsonInclude(NON_DEFAULT)
public class MetaSetDesc<P,R> implements Serializable {
    public Defacs defacs;
    @JsonProperty("public")
    public P pub;
    @JsonProperty("private")
    public R priv;

    @JsonIgnore
    public String[] attachments;

    public MetaSetDesc() {}

    public MetaSetDesc(P pub, R priv, Defacs da) {
        this.defacs = da;
        this.pub = pub;
        this.priv = priv;
    }

    public MetaSetDesc(P pub, R priv) {
        this(pub, priv, null);
    }

    public MetaSetDesc(Defacs da) {
        this(null, null, da);
    }
}
