package co.tinode.tinodesdk.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.io.Serializable;

/**
 * Payload for setting meta params, a combination of MetaSetDesc, MetaSetSub, tags, credential.
 *
 * Must use custom serializer to handle assigned NULL values, which should be converted to Tinode.NULL_VALUE.
 */
public class MsgSetMeta<Pu,Pr> implements Serializable {
    static final int NULL_DESC = 0;
    static final int NULL_SUB = 1;
    static final int NULL_TAGS = 2;
    static final int NULL_CRED = 3;

    // Keep track of NULL assignments to fields.
    @JsonIgnore
    final boolean[] nulls = new boolean[]{false, false, false, false};

    public MetaSetDesc<Pu,Pr> desc = null;
    public MetaSetSub sub = null;
    public String[] tags = null;
    public Credential cred = null;

    public MsgSetMeta() {}

    public boolean isDescSet() {
        return desc != null || nulls[NULL_DESC];
    }

    public boolean isSubSet() {
        return sub != null || nulls[NULL_SUB];
    }

    public boolean isTagsSet() {
        return tags != null || nulls[NULL_TAGS];

    }
    public boolean isCredSet() {
        return cred != null || nulls[NULL_CRED];
    }

    public static class Builder<Pu,Pr> {
        private final MsgSetMeta<Pu,Pr> msm;

        public Builder() {
            msm = new MsgSetMeta<>();
        }

        public Builder<Pu,Pr> with(MetaSetDesc<Pu,Pr> desc) {
            msm.desc = desc;
            msm.nulls[NULL_DESC] = desc == null;
            return this;
        }

        public Builder<Pu,Pr> with(MetaSetSub sub) {
            msm.sub = sub;
            msm.nulls[NULL_SUB] = sub == null;
            return this;
        }

        public Builder<Pu,Pr> with(String[] tags) {
            msm.tags = tags;
            msm.nulls[NULL_TAGS] = tags == null || tags.length == 0;
            return this;
        }

        public Builder<Pu,Pr> with(Credential cred) {
            msm.cred = cred;
            msm.nulls[NULL_CRED] = cred == null;
            return this;
        }

        public MsgSetMeta<Pu,Pr> build() {
            return msm;
        }
    }
}
