package co.tinode.tinodesdk.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.io.Serializable;
import java.util.Map;

/**
 * Payload for setting meta params, a combination of MetaSetDesc, MetaSetSub, tags, credential.
 * <p>
 * Must use custom serializer to handle assigned NULL values, which should be converted to Tinode.NULL_VALUE.
 */
public class MsgSetMeta<Pu,Pr> implements Serializable {
    static final int NULL_DESC = 0x1;
    static final int NULL_SUB = 0x2;
    static final int NULL_TAGS = 0x4;
    static final int NULL_CRED = 0x8;
    static final int NULL_AUX = 0x10;
    // Keep track of NULL assignments to fields.
    @JsonIgnore
    int nulls = 0;

    public MetaSetDesc<Pu,Pr> desc = null;
    public MetaSetSub sub = null;
    public String[] tags = null;
    public Credential cred = null;
    public Map<String,Object> aux = null;

    public MsgSetMeta() {}

    public boolean isDescSet() {
        return desc != null || (nulls & NULL_DESC) != 0;
    }

    public boolean isSubSet() {
        return sub != null || (nulls & NULL_SUB) != 0;
    }

    public boolean isTagsSet() {
        return tags != null || (nulls & NULL_TAGS) != 0;

    }
    public boolean isAuxSet() {
        return aux != null || (nulls & NULL_AUX) != 0;
    }
    public boolean isCredSet() {
        return cred != null || (nulls & NULL_CRED) != 0;
    }

    public boolean isEmpty() {
        return desc == null &&
            sub == null &&
            tags == null &&
            cred == null &&
            aux == null &&
            (nulls & (NULL_DESC | NULL_SUB | NULL_TAGS | NULL_CRED | NULL_AUX)) == 0;
    }

    public static class Builder<Pu,Pr> {
        private final MsgSetMeta<Pu,Pr> msm;

        public Builder() {
            msm = new MsgSetMeta<>();
        }

        public Builder<Pu,Pr> with(MetaSetDesc<Pu,Pr> desc) {
            msm.desc = desc;
            if (desc == null) {
                msm.nulls |= NULL_DESC;
            }
            return this;
        }

        public Builder<Pu,Pr> with(MetaSetSub sub) {
            msm.sub = sub;
            if (sub == null) {
                msm.nulls |= NULL_SUB;
            }
            return this;
        }

        public Builder<Pu,Pr> with(String[] tags) {
            msm.tags = tags;
            if (tags == null || tags.length == 0) {
                msm.nulls |= NULL_TAGS;
            }
            return this;
        }

        public Builder<Pu,Pr> with(Credential cred) {
            msm.cred = cred;
            if (cred == null) {
                msm.nulls |= NULL_CRED;
            }
            return this;
        }

        public Builder<Pu,Pr> with(Map<String,Object> aux) {
            msm.aux = aux;
            if (aux == null || aux.isEmpty()) {
                msm.nulls |= NULL_AUX;
            }
            return this;
        }

        public MsgSetMeta<Pu,Pr> build() {
            return msm;
        }

        public boolean isEmpty() {
            return msm.isEmpty();
        }
    }
}
