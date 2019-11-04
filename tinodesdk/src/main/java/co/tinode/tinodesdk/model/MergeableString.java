package co.tinode.tinodesdk.model;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import java.io.IOException;

// "Extends" String to implement co.tinode.tinodesdk.model.Mergeable.
// The class is implemented as a wrapper around a String because String is declared final.
// For this reason, we also need a custom JSON serializer.
@JsonSerialize(using= MergeableStringSerializer.class)
public class MergeableString implements Mergeable {
    public String data;

    public MergeableString(String data) {
        this.data = data;
    }
    @Override
    public int merge(Mergeable another) {
        if (!(another instanceof MergeableString)) {
            return 0;
        }
        data = ((MergeableString) another).data;
        return 1;
    }
}

class MergeableStringSerializer extends StdSerializer<MergeableString> {
    private static final long serialVersionUID = 1L;

    public MergeableStringSerializer() {
        this(MergeableString.class);
    }

    protected MergeableStringSerializer(Class<MergeableString> t) {
        super(t);
    }

    @Override
    public void serialize(MergeableString ms, JsonGenerator gen, SerializerProvider provider)
            throws IOException {
        gen.writeString(ms.data);
    }
}