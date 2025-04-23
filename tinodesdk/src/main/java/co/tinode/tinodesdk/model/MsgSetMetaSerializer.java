package co.tinode.tinodesdk.model;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import java.io.IOException;

import co.tinode.tinodesdk.Tinode;

/**
 * Custom serializer for MsgSetMeta to serialize assigned NULL fields as Tinode.NULL_VALUE string.
 */
public class MsgSetMetaSerializer extends StdSerializer<MsgSetMeta<?,?>> {
    public MsgSetMetaSerializer() {
        super(MsgSetMeta.class, false);
    }

    @Override
    public void serialize(MsgSetMeta<?,?> value, JsonGenerator gen, SerializerProvider provider) throws IOException {
        gen.writeStartObject();
        if (value.desc != null) {
            gen.writeObjectField("desc", value.desc);
        } else if ((value.nulls & MsgSetMeta.NULL_DESC) != 0) {
            gen.writeStringField("desc", Tinode.NULL_VALUE);
        }

        if (value.sub != null) {
            gen.writeObjectField("sub", value.sub);
        } else if ((value.nulls & MsgSetMeta.NULL_SUB) != 0) {
            gen.writeStringField("sub", Tinode.NULL_VALUE);
        }

        if (value.tags != null && value.tags.length != 0) {
            gen.writeFieldName("tags");
            gen.writeArray(value.tags, 0, value.tags.length);
        } else if ((value.nulls & MsgSetMeta.NULL_TAGS) != 0) {
            gen.writeFieldName("tags");
            gen.writeArray(new String[]{Tinode.NULL_VALUE}, 0, 1);
        }

        if (value.cred != null) {
            gen.writeObjectField("cred", value.cred);
        } else if ((value.nulls & MsgSetMeta.NULL_CRED) != 0) {
            gen.writeStringField("cred", Tinode.NULL_VALUE);
        }

        if (value.aux != null && !value.aux.isEmpty()) {
            gen.writeObjectField("aux", value.aux);
        } else if ((value.nulls & MsgSetMeta.NULL_AUX) != 0) {
            gen.writeStringField("aux", Tinode.NULL_VALUE);
        }

        gen.writeEndObject();
    }
}
