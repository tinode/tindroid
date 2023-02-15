package co.tinode.tinodesdk.model;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.NullValueProvider;
import com.fasterxml.jackson.databind.deser.std.PrimitiveArrayDeserializers;
import com.fasterxml.jackson.databind.type.LogicalType;

import java.io.IOException;
import java.util.Arrays;

// This class is needed only because of this bug:
// https://github.com/FasterXML/jackson-databind/issues/3784
// Once the bug is fixed, this class can be safely removed.
public class Base64Deserializer extends PrimitiveArrayDeserializers<byte[]> {
    // This is used by Jackson through reflection! Do not remove.
    public Base64Deserializer() {
        super(byte[].class);
    }

    protected Base64Deserializer(Base64Deserializer base, NullValueProvider nuller, Boolean unwrapSingle) {
        super(base, nuller, unwrapSingle);
    }

    @Override
    protected PrimitiveArrayDeserializers<?> withResolved(NullValueProvider nuller, Boolean unwrapSingle) {
        return new Base64Deserializer(this, nuller, unwrapSingle);
    }

    @Override
    protected byte[] _constructEmpty() {
        return new byte[0];
    }

    @Override
    public LogicalType logicalType() {
        return LogicalType.Binary;
    }

    @Override
    protected byte[] handleSingleElementUnwrapped(JsonParser p, DeserializationContext ctxt) {
        return new byte[0];
    }

    @Override
    public byte[] deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonToken t = p.currentToken();
        // Handling only base64-encoded string.
        if (t == JsonToken.VALUE_STRING) {
            try {
                return p.getBinaryValue(ctxt.getBase64Variant());
            } catch (JsonProcessingException e) {
                String msg = e.getOriginalMessage();
                if (msg.contains("base64")) {
                    return (byte[]) ctxt.handleWeirdStringValue(byte[].class, p.getText(), msg);
                }
            }
        }
        return null;
    }

    @Override
    protected byte[] _concat(byte[] oldValue, byte[] newValue) {
        int len1 = oldValue.length;
        int len2 = newValue.length;
        byte[] result = Arrays.copyOf(oldValue, len1+len2);
        System.arraycopy(newValue, 0, result, len1, len2);
        return result;
    }
}
