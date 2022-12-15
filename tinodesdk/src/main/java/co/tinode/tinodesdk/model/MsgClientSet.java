package co.tinode.tinodesdk.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.io.Serializable;
import java.util.Arrays;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_DEFAULT;

/**
 * Metadata update packet: description, subscription, tags, credentials.
 *
 * 	topic metadata, new topic &amp; new subscriptions only
 *  Desc *MsgSetDesc `json:"desc,omitempty"`
 *
 *  Subscription parameters
 *  Sub *MsgSetSub `json:"sub,omitempty"`
 */
@JsonInclude(NON_DEFAULT)
public class MsgClientSet<Pu,Pr> implements Serializable {
    // Keep track of NULL assignments to fields.
    @JsonIgnore
    final boolean[] nulls = new boolean[]{false, false, false, false};

    public String id;
    public String topic;

    public MetaSetDesc<Pu,Pr> desc;
    public MetaSetSub sub;
    public String[] tags;
    public Credential cred;

    public MsgClientSet() {}

    public MsgClientSet(String id, String topic, MsgSetMeta<Pu,Pr> meta) {
        this(id, topic, meta.desc, meta.sub, meta.tags, meta.cred);
        System.arraycopy(meta.nulls, 0, nulls, 0, meta.nulls.length);
    }

    protected MsgClientSet(String id, String topic) {
        this.id = id;
        this.topic = topic;
    }

    protected MsgClientSet(String id, String topic, MetaSetDesc<Pu, Pr> desc,
                        MetaSetSub sub, String[] tags, Credential cred) {
        this.id = id;
        this.topic = topic;
        this.desc = desc;
        this.sub = sub;
        this.tags = tags;
        this.cred = cred;
    }

    public static class Builder<Pu,Pr> {
        private final MsgClientSet<Pu,Pr> msm;

        public Builder(String id, String topic) {
            msm = new MsgClientSet<>(id, topic);
        }

        public void with(MetaSetDesc<Pu,Pr> desc) {
            msm.desc = desc;
            msm.nulls[MsgSetMeta.NULL_DESC] = desc == null;
        }

        public void with(MetaSetSub sub) {
            msm.sub = sub;
            msm.nulls[MsgSetMeta.NULL_SUB] = sub == null;
        }

        public void with(String[] tags) {
            msm.tags = tags;
            msm.nulls[MsgSetMeta.NULL_TAGS] = tags == null || tags.length == 0;
        }

        public void with(Credential cred) {
            msm.cred = cred;
            msm.nulls[MsgSetMeta.NULL_CRED] = cred == null;
        }

        public MsgClientSet<Pu,Pr> build() {
            return msm;
        }
    }
}
