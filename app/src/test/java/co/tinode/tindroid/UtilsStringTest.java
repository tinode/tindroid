package co.tinode.tindroid;

import org.junit.Test;
import static org.junit.Assert.*;

public class UtilsStringTest {

    // Test case 1: Empty string
    @Test
    public void countEmoji_emptyString_returnsZero() {
        assertEquals(0, UtilsString.countEmoji("", 5));
    }

    // Test case 2: Null string
    @Test
    public void countEmoji_nullString_returnsZero() {
        assertEquals(0, UtilsString.countEmoji(null, 5));
    }

    // Test case 3: Single simple emoji
    @Test
    public void countEmoji_singleSimpleEmoji_returnsOne() {
        assertEquals(1, UtilsString.countEmoji("😀", 5)); // Grinning Face
    }

    // Test case 4: Multiple simple emojis
    @Test
    public void countEmoji_multipleSimpleEmojis_returnsCorrectCount() {
        assertEquals(3, UtilsString.countEmoji("😀😂🤣", 5)); // Grinning Face, Face with Tears of Joy, Rolling on the Floor Laughing
    }

    // Test case 5: Emoji with skin tone modifier
    @Test
    public void countEmoji_emojiWithModifier_countsAsOne() {
        // 👍 U+1F44D Thumbs Up
        // 🏽 U+1F3FD Emoji Modifier Fitzpatrick Type-4
        assertEquals(1, UtilsString.countEmoji("👍🏽", 5));
    }

    // Test case 6: Multiple emojis with modifiers
    @Test
    public void countEmoji_multipleEmojisWithModifiers_returnsCorrectCount() {
        assertEquals(2, UtilsString.countEmoji("👍🏽👨🏾‍💻", 5)); // Thumbs up type 4, Man technologist type 5
    }


    // Test case 7: ZWJ sequence for family (Man, Woman, Girl, Boy)
    // This will be tricky with the current logic because ZWJ decrements.
    // The current logic seems to count components and then ZWJ reduces.
    // Let's test based on the *current implementation's expected behavior*.
    @Test
    public void countEmoji_zwjFamilySequence_returnsBasedOnImplementation() {
        assertEquals(1, UtilsString.countEmoji("\uD83D\uDC69\uD83C\uDFFD\u200D❤\uFE0F\u200D\uD83D\uDC68\uD83C\uDFFE", 5)); // Family with skin tones
        assertEquals(1, UtilsString.countEmoji("👨‍💻", 5)); // Man Technologist
        assertEquals(1, UtilsString.countEmoji("🏳️‍🌈", 5)); // Rainbow Flag (White flag, ZWJ, Rainbow)
        assertEquals(1, UtilsString.countEmoji("\uD83E\uDDD1\u200D\uD83C\uDF3E", 5)); // Farmer 🧑‍🌾
    }

    // Test case 8: String with only non-emoji characters
    @Test
    public void countEmoji_nonEmojiString_returnsNegativeOne() {
        assertEquals(-1, UtilsString.countEmoji("Hello", 5));
    }

    // Test case 9: String with mixed emoji and non-emoji characters
    @Test
    public void countEmoji_mixedContent_returnsNegativeOne() {
        assertEquals(-1, UtilsString.countEmoji("😀Hello😂", 5));
    }

    // Test case 10: maxCount limits the count
    @Test
    public void countEmoji_maxCountLimits_returnsMaxCount() {
        assertEquals(2, UtilsString.countEmoji("😀😂🤣😊😁", 2)); // 5 emojis, maxCount 2
    }

    // Test case 11: maxCount is zero
    @Test
    public void countEmoji_maxCountZero_returnsZero() {
        assertEquals(0, UtilsString.countEmoji("😀😂🤣", 0));
    }

    // Test case 12: Emoji with variation selector (e.g., text vs emoji style)
    @Test
    public void countEmoji_emojiWithVariationSelector_countsAsOne() {
        assertEquals(1, UtilsString.countEmoji("#️⃣", 5)); // Keycap Number Sign.
        assertEquals(1, UtilsString.countEmoji("\u2764\uFE0F", 5)); // Heavy Black Heart
        assertEquals(1, UtilsString.countEmoji("⁉️", 5)); // Exclamation Question Mark (U+2049 U+FE0F)
    }

    // Test case 13: String starting with ZWJ (should be invalid)
    @Test
    public void countEmoji_startsWithZWJ_returnsNegativeOne() {
        // Constructing such a string: ZWJ + Grinning Face
        assertEquals(-1, UtilsString.countEmoji("\u200D😀", 5));
    }

    // Test case 14: String with only modifiers and selectors (should be invalid or zero).
    @Test
    public void countEmoji_modifierOnly_returnsNegativeOne() {
        assertEquals(-1, UtilsString.countEmoji("🏽", 5)); // Modifier only - should be invalid.
    }

    // Test case 15: Variation selector only (should be invalid).
    @Test
    public void countEmoji_escapeSequence_returnsNegativeOne() {
        assertEquals(-1, UtilsString.countEmoji("\uFE0F", 5)); // Escape sequence - should be invalid.
    }

    // Test case 16: Variation selector only (should be invalid).
    @Test
    public void countEmoji_plainText_returnsNegativeOne() {
        assertEquals(-1, UtilsString.countEmoji("hello", 5));
        assertEquals(-1, UtilsString.countEmoji("11", 5));
        assertEquals(-1, UtilsString.countEmoji("###", 5));
    }



    // --- Valid Phone Numbers ---

    @Test
    public void asPhone_validE164Format_returnsNormalized() {
        assertEquals("+12223334455", UtilsString.asPhone("+12223334455"));
    }

    @Test
    public void asPhone_validNationalWithParenthesesAndSpaces_returnsNormalized() {
        assertEquals("2223334455", UtilsString.asPhone("(222) 333-4455"));
    }

    @Test
    public void asPhone_validNationalWithDots_returnsNormalized() {
        assertEquals("2223334455", UtilsString.asPhone("222.333.44.55"));
    }

    @Test
    public void asPhone_validNationalWithDashes_returnsNormalized() {
        assertEquals("2223334455", UtilsString.asPhone("222-333-44-55"));
    }

    @Test
    public void asPhone_validInternationalWithSpaces_returnsNormalized() {
        assertEquals("+442223334455", UtilsString.asPhone("+44 222 333 4455"));
    }

    @Test
    public void asPhone_validInternationalWithParenthesesAndDashes_returnsNormalized() {
        assertEquals("+12223334455", UtilsString.asPhone("+1 (222) 333-44-55"));
    }

    @Test
    public void asPhone_shorterLastGroup_returnsNormalized() {
        assertEquals("2223334455", UtilsString.asPhone("222-333-4455")); // This fits (22)(55)
    }

    @Test
    public void asPhone_full10DigitNumberNoCountryCode_returnsNormalized() {
        assertEquals("1234567890", UtilsString.asPhone("123-456-78-90"));
        assertEquals("1234567890", UtilsString.asPhone("123.456.78.90"));
    }

    @Test
    public void asPhone_numberWithLeadingAndTrailingSpaces_returnsNormalized() {
        assertEquals("+12223334455", UtilsString.asPhone("  +1 (222) 333-4455  "));
    }

    @Test
    public void asPhone_onlyDigitsNoSeparatorsNoCountryCode_returnsNormalized() {
        // This should match the groups XXX XXX XX XX
        assertEquals("1234567890", UtilsString.asPhone("1234567890"));
    }

    @Test
    public void asPhone_onlyDigitsWithCountryCode_returnsNormalized() {
        assertEquals("+11234567890", UtilsString.asPhone("+11234567890"));
    }

    // --- Invalid Phone Numbers ---

    @Test
    public void asPhone_emptyString_returnsNull() {
        assertNull(UtilsString.asPhone(""));
    }

    @Test
    public void asPhone_lettersInNumber_returnsNull() {
        assertNull(UtilsString.asPhone("123-ABC-4567"));
    }

    @Test
    public void asPhone_tooShort_returnsNull() {
        assertNull(UtilsString.asPhone("123-456"));
    }

    @Test
    public void asPhone_tooLongForPattern_returnsNull() {
        // Phone number is too long.
        assertNull(UtilsString.asPhone("+123452223334455")); // Too long
        assertNull(UtilsString.asPhone("123-456-789-012-345")); // Too many groups for this specific regex
    }

    @Test
    public void asPhone_incorrectSeparators_returnsNull() {
        assertNull(UtilsString.asPhone("123/456/7890")); // Uses '/' which is not in [- ().]*
    }

    @Test
    public void asPhone_justPlusSign_returnsNull() {
        assertNull(UtilsString.asPhone("+"));
    }

    @Test
    public void asPhone_alphanumericString_returnsNull() {
        assertNull(UtilsString.asPhone("notaphonenumber"));
    }

    @Test
    public void asPhone_countryCodeTooShortIfPresent() {
        // The regex `(\\d{1,3})` for country code.
        // If it starts with + but no digits, it might fail.
        // The `sTelRegex.matcher(val).matches()` ensures the whole string fits the pattern.
        assertNull(UtilsString.asPhone("+ 1234567890")); // space after + might be an issue depending on strictness
        assertNull(UtilsString.asPhone("+ 123 456 78 90"));
    }

    @Test
    public void asPhone_digitGrouping() {
        assertEquals("1234567890", UtilsString.asPhone("12-345-678-90")); // e.g. 2-3-3-2 instead of 3-3-2-2
        assertNull(UtilsString.asPhone("1234-567-890"));
    }
}