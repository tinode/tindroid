package co.tinode.tinodesdk.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.io.Serializable;
import java.util.Map;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_DEFAULT;

/**
 * Metadata update packet: description, subscription, tags, credentials.
 * <p>
 * 	topic metadata, new topic &amp; new subscriptions only
 *  Desc *MsgSetDesc `json:"desc,omitempty"`
 * <p>
 *  Subscription parameters
 *  Sub *MsgSetSub `json:"sub,omitempty"`
 */
@JsonInclude(NON_DEFAULT)
public class MsgClientSet<Pu,Pr> implements Serializable {
    // Keep track of NULL assignments to fields.
    @JsonIgnore
    int nulls = 0;

    public String id;
    public String topic;

    public MetaSetDesc<Pu,Pr> desc;
    public MetaSetSub sub;
    public String[] tags;
    public Credential cred;
    public Map<String, Object> aux;

    public MsgClientSet() {}

    public MsgClientSet(String id, String topic, MsgSetMeta<Pu,Pr> meta) {
        this(id, topic, meta.desc, meta.sub, meta.tags, meta.cred, meta.aux);
        nulls = meta.nulls;
    }

    protected MsgClientSet(String id, String topic) {
        this.id = id;
        this.topic = topic;
    }

    protected MsgClientSet(String id, String topic, MetaSetDesc<Pu, Pr> desc,
                           MetaSetSub sub, String[] tags, Credential cred,
                           Map<String, Object> aux) {
        this.id = id;
        this.topic = topic;
        this.desc = desc;
        this.sub = sub;
        this.tags = tags;
        this.cred = cred;
        this.aux = aux;
    }

    public static class Builder<Pu,Pr> {
        private final MsgClientSet<Pu,Pr> msm;

        public Builder(String id, String topic) {
            msm = new MsgClientSet<>(id, topic);
        }

        public void with(MetaSetDesc<Pu,Pr> desc) {
            msm.desc = desc;
            if (desc == null) {
                msm.nulls |= MsgSetMeta.NULL_DESC;
            }
        }

        public void with(MetaSetSub sub) {
            msm.sub = sub;
            if (sub == null) {
                msm.nulls |= MsgSetMeta.NULL_SUB;
            }
        }

        public void with(String[] tags) {
            msm.tags = tags;
            if (tags == null || tags.length == 0) {
                msm.nulls |= MsgSetMeta.NULL_TAGS;
            }
        }

        public void with(Credential cred) {
            msm.cred = cred;
            if (cred == null) {
                msm.nulls |= MsgSetMeta.NULL_CRED;
            }
        }

        public void with(Map<String,Object> aux) {
            msm.aux = aux;
            if (aux == null || aux.isEmpty()) {
                msm.nulls |= MsgSetMeta.NULL_AUX;
            }
        }

        public MsgClientSet<Pu,Pr> build() {
            return msm;
        }
    }
}
