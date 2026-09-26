package co.tinode.tindroid.format;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.StyleSpan;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import co.tinode.tindroid.R;
import co.tinode.tinodesdk.model.Drafty;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

/** Tests behavior shared by all {@link AbstractDraftyFormatter} implementations. */
@RunWith(AndroidJUnit4.class)
public class AbstractDraftyFormatterInstrumentationTest {
    private static final Context CONTEXT = ApplicationProvider.getApplicationContext();

    @Test
    public void apply_dispatchesEveryKnownTypeAndPreservesArguments() {
        FormatterProbeTest formatter = new FormatterProbeTest();
        List<SpannableStringBuilder> content = Collections.singletonList(new SpannableStringBuilder("content"));
        Map<String, Object> data = Collections.singletonMap("key", "value");
        Stack<String> context = new Stack<>();
        context.push("parent");

        String[] types = {"ST", "EM", "DL", "CO", "HD", "BR", "LN", "MN", "HT", "AU", "IM", "VD",
                "EX", "BN", "FM", "RW", "QQ", "VC"};
        for (String type : types) {
            assertEquals(type, formatter.apply(type, data, content, context).toString());
            assertEquals(type, formatter.lastType);
            assertSame(data, formatter.lastData);
            assertSame(content, formatter.lastContent);
            assertSame(context, formatter.lastContext);
        }
    }

    @Test
    public void apply_routesNullToPlainAndUnknownToUnknown() {
        FormatterProbeTest formatter = new FormatterProbeTest();
        List<SpannableStringBuilder> content = Collections.singletonList(new SpannableStringBuilder("plain"));

        assertEquals("plain", formatter.apply(null, null, content, null).toString());
        assertEquals("plain", formatter.lastType);
        assertNull(formatter.lastData);
        assertNull(formatter.lastContext);

        Map<String, Object> data = Collections.singletonMap("data", 1);
        assertEquals("unknown", formatter.apply("XX", data, content, null).toString());
        assertEquals("unknown", formatter.lastType);
        assertSame(data, formatter.lastData);
    }

    @Test
    public void helpers_joinStyleTimeStatusAndDataAccessors_handleExpectedValues() {
        SpannableStringBuilder first = new SpannableStringBuilder("one");
        SpannableStringBuilder joined = FormatterProbeTest.joinForTest(Arrays.asList(first, new SpannableStringBuilder(" two")));
        assertSame(first, joined);
        assertEquals("one two", joined.toString());
        assertNull(FormatterProbeTest.joinForTest(null));

        StyleSpan style = new StyleSpan(Typeface.BOLD);
        SpannableStringBuilder styled = FormatterProbeTest.styleForTest(style,
                Collections.singletonList(new SpannableStringBuilder("bold")));
        assertArrayEquals(new StyleSpan[]{style}, styled.getSpans(0, styled.length(), StyleSpan.class));
        assertEquals(0, styled.getSpanStart(style));
        assertEquals(styled.length(), styled.getSpanEnd(style));
        assertNull(FormatterProbeTest.styleForTest(style, null));

        assertEquals("0:00", FormatterProbeTest.timeForTest(0, false));
        assertEquals("00:00", FormatterProbeTest.timeForTest(0, true));
        assertEquals("1:01", FormatterProbeTest.timeForTest(61_000, false));
        assertEquals("1:01:01", FormatterProbeTest.timeForTest(3_661_000, false));

        assertEquals(R.string.busy_call, FormatterProbeTest.callStatusForTest(true, "busy"));
        assertEquals(R.string.missed_call, FormatterProbeTest.callStatusForTest(true, "missed"));
        assertEquals(R.string.cancelled_call, FormatterProbeTest.callStatusForTest(false, "missed"));
        assertEquals(R.string.disconnected_call, FormatterProbeTest.callStatusForTest(false, "other"));

        Map<String, Object> data = new HashMap<>();
        data.put("number", 2.9d);
        data.put("text", new StringBuilder("text"));
        data.put("flag", true);
        assertEquals(2, FormatterProbeTest.intForTest("number", data));
        assertEquals(0, FormatterProbeTest.intForTest("missing", data));
        assertEquals("text", FormatterProbeTest.stringForTest("text", data, "default"));
        assertEquals("default", FormatterProbeTest.stringForTest("missing", data, "default"));
        assertEquals("fallback", FormatterProbeTest.stringForTest("missing", data, "fallback"));
        assertTrue(FormatterProbeTest.booleanForTest("flag", data));
        assertFalse(FormatterProbeTest.booleanForTest("missing", data));
        assertTrue(FormatterProbeTest.skippableForTest("text/x-drafty-fr"));
        assertTrue(FormatterProbeTest.skippableForTest("application/json"));
        assertFalse(FormatterProbeTest.skippableForTest("text/plain"));
    }

    @Test
    public void previewFormatter_appliesStylesAndPreviewSpecificElements() {
        PreviewFormatter formatter = new PreviewFormatter(CONTEXT, 16f);
        SpannableStringBuilder strong = formatter.apply("ST", null,
                Collections.singletonList(new SpannableStringBuilder("bold")), null);
        StyleSpan[] spans = strong.getSpans(0, strong.length(), StyleSpan.class);
        assertEquals(1, spans.length);
        assertEquals(Typeface.BOLD, spans[0].getStyle());

        assertEquals(" ", formatter.apply("BR", null, null, null).toString());
        assertNull(formatter.apply("HD", null, null, null));
        assertNull(formatter.apply("QQ", null, null, null));
        assertNull(formatter.apply("EX", null, null, null));
        assertNull(formatter.apply("EX", Collections.singletonMap("mime", "text/x-drafty-fr"), null, null));
    }

    @Test
    public void previewFormatter_formatsDraftyTreeWithExpectedTextAndStyles() {
        SpannableStringBuilder formatted = Drafty.parse("one *bold* and _italic_")
                .format(new PreviewFormatter(CONTEXT, 16f));

        assertEquals("one bold and italic", formatted.toString());
        StyleSpan[] styles = formatted.getSpans(0, formatted.length(), StyleSpan.class);
        assertEquals(2, styles.length);
        assertTrue(hasStyleAt(styles, formatted, Typeface.BOLD, 4, 8));
        assertTrue(hasStyleAt(styles, formatted, Typeface.ITALIC, 13, 19));
    }

    @Test
    public void fontAndCopyFormatters_useTextOnlyRepresentations() {
        FontFormatter font = new FontFormatter(CONTEXT, 16f);
        assertEquals("🎤 " + CONTEXT.getString(R.string.audio), font.apply("AU", null, null, null).toString());
        assertEquals("📎 " + CONTEXT.getString(R.string.attachment),
                font.apply("EX", Collections.singletonMap("mime", "text/plain"), null, null).toString());
        assertNull(font.apply("EX", Collections.singletonMap("mime", "text/x-drafty-fr"), null, null));

        CopyFormatter copy = new CopyFormatter(CONTEXT);
        assertEquals("\u00A0[\u00A0send\u00A0]", copy.apply("BN", null,
                Collections.singletonList(new SpannableStringBuilder("send")), null).toString());
    }

    private static boolean hasStyleAt(StyleSpan[] styles, Spanned text, int style, int start, int end) {
        for (StyleSpan candidate : styles) {
            if (candidate.getStyle() == style && text.getSpanStart(candidate) == start && text.getSpanEnd(candidate) == end) {
                return true;
            }
        }
        return false;
    }

    private static final class FormatterProbeTest extends AbstractDraftyFormatter<SpannableStringBuilder> {
        String lastType;
        Map<String, Object> lastData;
        List<SpannableStringBuilder> lastContent;
        Stack<String> lastContext;

        FormatterProbeTest() {
            super(CONTEXT);
        }

        @Override
        public SpannableStringBuilder apply(String type, Map<String, Object> data, List<SpannableStringBuilder> content,
                                            Stack<String> context) {
            lastContext = context;
            return super.apply(type, data, content, context);
        }

        @Override public SpannableStringBuilder wrapText(CharSequence text) { return new SpannableStringBuilder(text); }
        @Override protected SpannableStringBuilder handleStrong(List<SpannableStringBuilder> content) { return record("ST", content, null); }
        @Override protected SpannableStringBuilder handleEmphasized(List<SpannableStringBuilder> content) { return record("EM", content, null); }
        @Override protected SpannableStringBuilder handleDeleted(List<SpannableStringBuilder> content) { return record("DL", content, null); }
        @Override protected SpannableStringBuilder handleCode(List<SpannableStringBuilder> content) { return record("CO", content, null); }
        @Override protected SpannableStringBuilder handleHidden(List<SpannableStringBuilder> content) { return record("HD", content, null); }
        @Override protected SpannableStringBuilder handleLineBreak() { return record("BR", null, null); }
        @Override protected SpannableStringBuilder handleLink(Context ctx, List<SpannableStringBuilder> content, Map<String, Object> data) { return record("LN", content, data); }
        @Override protected SpannableStringBuilder handleMention(Context ctx, List<SpannableStringBuilder> content, Map<String, Object> data) { return record("MN", content, data); }
        @Override protected SpannableStringBuilder handleHashtag(Context ctx, List<SpannableStringBuilder> content, Map<String, Object> data) { return record("HT", content, data); }
        @Override protected SpannableStringBuilder handleAudio(Context ctx, List<SpannableStringBuilder> content, Map<String, Object> data) { return record("AU", content, data); }
        @Override protected SpannableStringBuilder handleImage(Context ctx, List<SpannableStringBuilder> content, Map<String, Object> data) { return record("IM", content, data); }
        @Override protected SpannableStringBuilder handleVideo(Context ctx, List<SpannableStringBuilder> content, Map<String, Object> data) { return record("VD", content, data); }
        @Override protected SpannableStringBuilder handleAttachment(Context ctx, Map<String, Object> data) { return record("EX", null, data); }
        @Override protected SpannableStringBuilder handleButton(Context ctx, List<SpannableStringBuilder> content, Map<String, Object> data) { return record("BN", content, data); }
        @Override protected SpannableStringBuilder handleFormRow(Context ctx, List<SpannableStringBuilder> content, Map<String, Object> data) { return record("RW", content, data); }
        @Override protected SpannableStringBuilder handleForm(Context ctx, List<SpannableStringBuilder> content, Map<String, Object> data) { return record("FM", content, data); }
        @Override protected SpannableStringBuilder handleQuote(Context ctx, List<SpannableStringBuilder> content, Map<String, Object> data) { return record("QQ", content, data); }
        @Override protected SpannableStringBuilder handleVideoCall(Context ctx, List<SpannableStringBuilder> content, Map<String, Object> data) { return record("VC", content, data); }
        @Override protected SpannableStringBuilder handleUnknown(Context ctx, List<SpannableStringBuilder> content, Map<String, Object> data) { return record("unknown", content, data); }
        @Override protected SpannableStringBuilder handlePlain(List<SpannableStringBuilder> content) { return record("plain", content, null); }

        private SpannableStringBuilder record(String type, List<SpannableStringBuilder> content, Map<String, Object> data) {
            lastType = type;
            lastContent = content;
            lastData = data;
            return new SpannableStringBuilder(type);
        }

        static SpannableStringBuilder joinForTest(List<SpannableStringBuilder> content) { return join(content); }
        static SpannableStringBuilder styleForTest(Object style, List<SpannableStringBuilder> content) { return assignStyle(style, content); }
        static String timeForTest(Number millis, boolean fixedMin) { return millisToTime(millis, fixedMin).toString(); }
        static int callStatusForTest(boolean incoming, String event) { return callStatus(incoming, event); }
        static int intForTest(String name, Map<String, Object> data) { return getIntVal(name, data); }
        static String stringForTest(String name, Map<String, Object> data, String def) { return getStringVal(name, data, def); }
        static boolean booleanForTest(String name, Map<String, Object> data) { return getBooleanVal(name, data); }
        static boolean skippableForTest(Object mime) { return isSkippableJson(mime); }
    }
}




