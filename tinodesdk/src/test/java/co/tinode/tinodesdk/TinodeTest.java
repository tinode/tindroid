package co.tinode.tinodesdk;

import static org.junit.Assert.*;

import com.fasterxml.jackson.core.JsonProcessingException;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import co.tinode.tinodesdk.model.Pair;

/**
 * Unit tests for static methods in Tinode class.
 */
public class TinodeTest {

    /**
     * Test getTypeFactory() returns non-null value
     */
    @Test
    public void testGetTypeFactory() {
        assertNotNull(Tinode.getTypeFactory());
    }

    /**
     * Test getJsonMapper() returns non-null value
     */
    @Test
    public void testGetJsonMapper() {
        assertNotNull(Tinode.getJsonMapper());
    }

    /**
     * Test isNull() with NULL_VALUE string
     */
    @Test
    public void testIsNull_WithNullValue() {
        assertTrue(Tinode.isNull(Tinode.NULL_VALUE));
    }

    /**
     * Test isNull() with regular string
     */
    @Test
    public void testIsNull_WithRegularString() {
        assertFalse(Tinode.isNull("regular_string"));
    }

    /**
     * Test isNull() with other object types
     */
    @Test
    public void testIsNull_WithOtherTypes() {
        assertFalse(Tinode.isNull(123));
        assertFalse(Tinode.isNull(45.67));
        assertFalse(Tinode.isNull(new Object()));
    }

    /**
     * Test tagSplit() with valid tag
     */
    @Test
    public void testTagSplit_ValidTag() {
        Pair<String, String> result = Tinode.tagSplit("email:user@example.com");
        assertNotNull(result);
        assertEquals("email", result.first);
        assertEquals("user@example.com", result.second);
    }

    /**
     * Test tagSplit() with tag containing colon in value
     */
    @Test
    public void testTagSplit_TagWithColonInValue() {
        Pair<String, String> result = Tinode.tagSplit("protocol:http://example.com");
        assertNotNull(result);
        assertEquals("protocol", result.first);
        assertEquals("http://example.com", result.second);
    }

    /**
     * Test tagSplit() with tag with spaces
     */
    @Test
    public void testTagSplit_TagWithSpaces() {
        Pair<String, String> result = Tinode.tagSplit("  email:user@example.com  ");
        assertNotNull(result);
        assertEquals("email", result.first);
        assertEquals("user@example.com", result.second);
    }

    /**
     * Test tagSplit() with invalid tag (no colon)
     */
    @Test
    public void testTagSplit_InvalidTag_NoColon() {
        Pair<String, String> result = Tinode.tagSplit("notag");
        assertNull(result);
    }

    /**
     * Test tagSplit() with invalid tag (empty value)
     */
    @Test
    public void testTagSplit_InvalidTag_EmptyValue() {
        Pair<String, String> result = Tinode.tagSplit("email:");
        assertNull(result);
    }

    /**
     * Test setUniqueTag() adding tag to null array
     */
    @Test
    public void testSetUniqueTag_AddToNullArray() {
        String[] result = Tinode.setUniqueTag(null, "email:test@example.com");
        assertNotNull(result);
        assertEquals(1, result.length);
        assertEquals("email:test@example.com", result[0]);
    }

    /**
     * Test setUniqueTag() adding tag to empty array
     */
    @Test
    public void testSetUniqueTag_AddToEmptyArray() {
        String[] result = Tinode.setUniqueTag(new String[]{}, "email:test@example.com");
        assertNotNull(result);
        assertEquals(1, result.length);
        assertEquals("email:test@example.com", result[0]);
    }

    /**
     * Test setUniqueTag() replacing existing tag with same prefix
     */
    @Test
    public void testSetUniqueTag_ReplaceExisting() {
        String[] tags = {"email:old@example.com", "phone:1234567890"};
        String[] result = Tinode.setUniqueTag(tags, "email:new@example.com");
        assertNotNull(result);
        assertEquals(2, result.length);
        assertTrue(contains(result, "email:new@example.com"));
        assertTrue(contains(result, "phone:1234567890"));
        assertFalse(contains(result, "email:old@example.com"));
    }

    /**
     * Test setUniqueTag() with invalid tag
     */
    @Test
    public void testSetUniqueTag_InvalidTag() {
        String[] result = Tinode.setUniqueTag(new String[]{"email:test@example.com"}, "invalid");
        assertNull(result);
    }

    /**
     * Test clearTagPrefix() removing tags with prefix
     */
    @Test
    public void testClearTagPrefix_RemovePrefix() {
        String[] tags = {"email:test@example.com", "phone:1234567890", "email:another@example.com"};
        String[] result = Tinode.clearTagPrefix(tags, "email:");
        assertNotNull(result);
        assertEquals(1, result.length);
        assertEquals("phone:1234567890", result[0]);
    }

    /**
     * Test clearTagPrefix() with no matching prefix
     */
    @Test
    public void testClearTagPrefix_NoMatchingPrefix() {
        String[] tags = {"email:test@example.com", "phone:1234567890"};
        String[] result = Tinode.clearTagPrefix(tags, "fax:");
        assertNotNull(result);
        assertEquals(2, result.length);
    }

    /**
     * Test clearTagPrefix() with empty array
     */
    @Test
    public void testClearTagPrefix_EmptyArray() {
        String[] result = Tinode.clearTagPrefix(new String[]{}, "email:");
        assertNull(result);
    }

    /**
     * Test isValidTagValueFormat() with valid tag
     */
    @Test
    public void testIsValidTagValueFormat_ValidTag() {
        assertTrue(Tinode.isValidTagValueFormat("john_doe"));
        assertTrue(Tinode.isValidTagValueFormat("user123"));
        assertTrue(Tinode.isValidTagValueFormat("valid-tag"));
    }

    /**
     * Test isValidTagValueFormat() with empty string (valid by spec)
     */
    @Test
    public void testIsValidTagValueFormat_EmptyString() {
        assertTrue(Tinode.isValidTagValueFormat(""));
    }

    /**
     * Test isValidTagValueFormat() with null (valid by spec)
     */
    @Test
    public void testIsValidTagValueFormat_Null() {
        assertTrue(Tinode.isValidTagValueFormat(null));
    }

    /**
     * Test isValidTagValueFormat() with invalid characters
     */
    @Test
    public void testIsValidTagValueFormat_InvalidCharacters() {
        assertFalse(Tinode.isValidTagValueFormat("user@example.com"));
        assertFalse(Tinode.isValidTagValueFormat("tag with spaces"));
        assertFalse(Tinode.isValidTagValueFormat("tag*special"));
    }

    /**
     * Test tagByPrefix() finding tag with prefix
     */
    @Test
    public void testTagByPrefix_FindExisting() {
        String[] tags = {"email:test@example.com", "phone:1234567890"};
        String result = Tinode.tagByPrefix(tags, "email:");
        assertEquals("email:test@example.com", result);
    }

    /**
     * Test tagByPrefix() with no matching prefix
     */
    @Test
    public void testTagByPrefix_NoMatch() {
        String[] tags = {"email:test@example.com", "phone:1234567890"};
        String result = Tinode.tagByPrefix(tags, "fax:");
        assertNull(result);
    }

    /**
     * Test tagByPrefix() finding first matching tag
     */
    @Test
    public void testTagByPrefix_FirstMatch() {
        String[] tags = {"email:first@example.com", "email:second@example.com", "phone:1234567890"};
        String result = Tinode.tagByPrefix(tags, "email:");
        assertEquals("email:first@example.com", result);
    }

    /**
     * Test jsonSerialize() with simple object
     */
    @Test
    public void testJsonSerialize_SimpleObject() throws JsonProcessingException {
        Map<String, Object> map = new HashMap<>();
        map.put("key", "value");
        map.put("number", 123);

        String json = Tinode.jsonSerialize(map);
        assertNotNull(json);
        assertTrue(json.contains("key"));
        assertTrue(json.contains("value"));
        assertTrue(json.contains("number"));
    }

    /**
     * Test jsonSerialize() with null object
     */
    @Test
    public void testJsonSerialize_NullObject() throws JsonProcessingException {
        String result = Tinode.jsonSerialize(null);
        assertNotNull(result);
        assertEquals("null", result);
    }

    /**
     * Test jsonSerialize() with nested object
     */
    @Test
    public void testJsonSerialize_NestedObject() throws JsonProcessingException {
        Map<String, Object> inner = new HashMap<>();
        inner.put("nested", "value");

        Map<String, Object> outer = new HashMap<>();
        outer.put("outer", inner);

        String json = Tinode.jsonSerialize(outer);
        assertNotNull(json);
        assertTrue(json.contains("nested"));
    }

    /**
     * Test newTopic() creates MeTopic for "me"
     */
    @Test
    public void testNewTopic_MeTopic() {
        Topic topic = Tinode.newTopic(null, Tinode.TOPIC_ME, null);
        assertNotNull(topic);
        assertIsInstance(topic, MeTopic.class);
    }

    /**
     * Test newTopic() creates FndTopic for "fnd"
     */
    @Test
    public void testNewTopic_FndTopic() {
        Topic topic = Tinode.newTopic(null, Tinode.TOPIC_FND, null);
        assertNotNull(topic);
        assertIsInstance(topic, FndTopic.class);
    }

    /**
     * Test newTopic() creates ComTopic for other names
     */
    @Test
    public void testNewTopic_ComTopic() {
        Topic topic = Tinode.newTopic(null, "grpABC", null);
        assertNotNull(topic);
        assertIsInstance(topic, ComTopic.class);
    }

    /**
     * Test headersForReply() creates correct map
     */
    @Test
    public void testHeadersForReply() {
        Map<String, Object> headers = Tinode.headersForReply(42);
        assertNotNull(headers);
        assertTrue(headers.containsKey("reply"));
        assertEquals("42", headers.get("reply"));
    }

    /**
     * Test headersForReply() with different sequence number
     */
    @Test
    public void testHeadersForReply_DifferentSeq() {
        Map<String, Object> headers = Tinode.headersForReply(999);
        assertEquals("999", headers.get("reply"));
    }

    /**
     * Test headersForReplacement() creates correct map
     */
    @Test
    public void testHeadersForReplacement() {
        Map<String, Object> headers = Tinode.headersForReplacement(42);
        assertNotNull(headers);
        assertTrue(headers.containsKey("replace"));
        assertEquals(":42", headers.get("replace"));
    }

    /**
     * Test headersForReplacement() with different sequence number
     */
    @Test
    public void testHeadersForReplacement_DifferentSeq() {
        Map<String, Object> headers = Tinode.headersForReplacement(999);
        assertEquals(":999", headers.get("replace"));
    }

    /**
     * Test isUrlRelative() with absolute HTTP URL
     */
    @Test
    public void testIsUrlRelative_AbsoluteHttp() {
        assertFalse(Tinode.isUrlRelative("http://example.com/path"));
    }

    /**
     * Test isUrlRelative() with absolute HTTPS URL
     */
    @Test
    public void testIsUrlRelative_AbsoluteHttps() {
        assertFalse(Tinode.isUrlRelative("https://example.com/path"));
    }

    /**
     * Test isUrlRelative() with protocol-relative URL
     */
    @Test
    public void testIsUrlRelative_ProtocolRelative() {
        assertFalse(Tinode.isUrlRelative("//example.com/path"));
    }

    /**
     * Test isUrlRelative() with relative path
     */
    @Test
    public void testIsUrlRelative_RelativePath() {
        assertTrue(Tinode.isUrlRelative("/path/to/resource"));
        assertTrue(Tinode.isUrlRelative("path/to/resource"));
        assertTrue(Tinode.isUrlRelative("resource.txt"));
    }

    /**
     * Test isUrlRelative() with URL starting with whitespace
     */
    @Test
    public void testIsUrlRelative_WithWhitespace() {
        assertFalse(Tinode.isUrlRelative("   http://example.com/path"));
        assertTrue(Tinode.isUrlRelative("   /path/to/resource"));
    }

    /**
     * Test isUrlRelative() with weird schema part.
     */
    @Test
    public void testIsUrlRelative_WeirdSchemas() {
        // Relative URLs
        assertTrue(Tinode.isUrlRelative("-schema://example.com/path"));
        assertTrue(Tinode.isUrlRelative("sche=ma://example.com/path"));
        assertTrue(Tinode.isUrlRelative("\"sche\\ma://example.com/path"));
        assertTrue(Tinode.isUrlRelative(":schema://example.com/path"));
        assertTrue(Tinode.isUrlRelative("s$chema://example.com/path"));
        assertTrue(Tinode.isUrlRelative("123schema://example.com/path"));
        assertTrue(Tinode.isUrlRelative("s'chema://example.com/path"));

        // Absolute URLs
        assertFalse(Tinode.isUrlRelative("sch:ema://example.com/path"));
        assertFalse(Tinode.isUrlRelative("sch--ema://example.com/path"));
        assertFalse(Tinode.isUrlRelative("sc123ema://example.com/path"));
        assertFalse(Tinode.isUrlRelative("aaa:::://example.com/path"));
    }

    /**
     * Test parseTinodeUrl() with null input
     */
    @Test
    public void testParseTinodeUrl_NullInput() {
        String result = Tinode.parseTinodeUrl(null);
        assertNull(result);
    }

    /**
     * Test parseTinodeUrl() with non-tinode URL
     */
    @Test
    public void testParseTinodeUrl_NonTinodeUrl() {
        String url = "https://example.com";
        String result = Tinode.parseTinodeUrl(url);
        assertEquals(url, result);
    }

    /**
     * Test parseTinodeUrl() with valid tinode URL
     */
    @Test
    public void testParseTinodeUrl_ValidTinodeUrl() {
        String result = Tinode.parseTinodeUrl("tinode:///id/usrABC12345");
        assertEquals("usrABC12345", result);
    }

    /**
     * Test parseTinodeUrl() with tinode URL with host
     */
    @Test
    public void testParseTinodeUrl_TinodeUrlWithHost() {
        String result = Tinode.parseTinodeUrl("tinode://example.com/id/usrXYZ98765");
        assertEquals("usrXYZ98765", result);
    }

    /**
     * Test parseTinodeUrl() with invalid tinode URL (missing id)
     */
    @Test
    public void testParseTinodeUrl_InvalidTinodeUrl() {
        String url = "tinode:///user/usrABC12345";
        String result = Tinode.parseTinodeUrl(url);
        assertEquals(url, result);
    }

    /**
     * Test parseTinodeUrl() with tinode URL with single part
     */
    @Test
    public void testParseTinodeUrl_TinodeUrlSinglePart() {
        String url = "tinode://onlypart";
        String result = Tinode.parseTinodeUrl(url);
        assertEquals(url, result);
    }

    /**
     * Test parseTinodeUrl() with tinode URL with no host part
     */
    @Test
    public void testParseTinodeUrl_TinodeUrlNoHost() {
        String url = "tinode:id/usrABC12345";
        String result = Tinode.parseTinodeUrl(url);
        assertEquals("usrABC12345", result);
    }

    /**
     * Test parseTinodeUrl() with tinode URL with no host and no id
     */
    @Test
    public void testParseTinodeUrl_TinodeUrlNoHostNoId() {
        String url = "tinode:678/usrABC12345";
        String result = Tinode.parseTinodeUrl(url);
        assertEquals(url, result);
    }

    // Helper methods

    /**
     * Check if array contains string
     */
    private boolean contains(String[] array, String value) {
        for (String s : array) {
            if (value.equals(s)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Assert that object is instance of class
     */
    private void assertIsInstance(Object obj, Class<?> clazz) {
        assertTrue("Expected " + clazz.getName() + " but got " + obj.getClass().getName(),
                clazz.isInstance(obj));
    }
}
