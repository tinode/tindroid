package co.tinode.tinodesdk.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import com.ibm.icu.text.BreakIterator;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;
import java.net.URI;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_DEFAULT;


/**
 <p>Basic parser and formatter for very simple rich text. Mostly targeted at
 mobile use cases similar to Telegram and WhatsApp.</p>

 <p>Supports:</p>
 <ul>
 <li>*abc* &rarr; <b>abc</b></li>
 <li>_abc_ &rarr; <i>abc</i></li>
 <li>~abc~ &rarr; <span style="text-decoration:line-through">abc</span></li>
 <li>`abc` &rarr; <tt>abc</tt></li>
 </ul>

 <p>Nested formatting is supported, e.g. *abc _def_* &rarr; <b>abc <i>def</i></b></p>

 <p>URLs, @mentions, and #hashtags are extracted.</p>

 <p>JSON data representation is similar to Draft.js raw formatting.</p>

 <p>Sample text:</p>
 <pre>
     this is *bold*, `code` and _italic_, ~strike~
     combined *bold and _italic_*
     an url: https://www.example.com/abc#fragment and another _www.tinode.co_
     this is a @mention and a #hashtag in a string
     second #hashtag
 </pre>

 <p>JSON representation of the sample text above:</p>
 <pre>
 {
    "txt":  "this is bold, code and italic, strike combined bold and italic an url: https://www.example.com/abc#fragment " +
            "and another www.tinode.co this is a @mention and a #hashtag in a string second #hashtag",
    "fmt": [
        { "at":8, "len":4,"tp":"ST" },{ "at":14, "len":4, "tp":"CO" },{ "at":23, "len":6, "tp":"EM"},
        { "at":31, "len":6, "tp":"DL" },{ "tp":"BR", "len":1, "at":37 },{ "at":56, "len":6, "tp":"EM" },
        { "at":47, "len":15, "tp":"ST" },{ "tp":"BR", "len":1, "at":62 },{ "at":120, "len":13, "tp":"EM" },
        { "at":71, "len":36, "key":0 },{ "at":120, "len":13, "key":1 },{ "tp":"BR", "len":1, "at":133 },
        { "at":144, "len":8, "key":2 },{ "at":159, "len":8, "key":3 },{ "tp":"BR", "len":1, "at":179 },
        { "at":187, "len":8, "key":3 },{ "tp":"BR", "len":1, "at":195 }
    ],
    "ent": [
        { "tp":"LN", "data":{ "url":"https://www.example.com/abc#fragment" } },
        { "tp":"LN", "data":{ "url":"http://www.tinode.co" } },
        { "tp":"MN", "data":{ "val":"mention" } },
        { "tp":"HT", "data":{ "val":"hashtag" } }
    ]
 }
 </pre>
 */
@JsonInclude(NON_DEFAULT)
public class Drafty implements Serializable {
    public static final String MIME_TYPE = "text/x-drafty";

    private static final String DRAFTY_FR_TYPE_LEGACY = "application/json";
    private static final String DRAFTY_FR_TYPE = "text/x-drafty-fr";

    private static final int MAX_PREVIEW_DATA_SIZE = 64;
    private static final int MAX_PREVIEW_ATTACHMENTS = 3;

    private static final String[] DATA_FIELDS =
            new String[]{"act", "duration", "height", "incoming", "mime", "name", "premime", "preref", "preview", "ref",
                    "size", "state", "title", "url", "val", "width"};

    private static final Map<Class<?>, Class<?>> WRAPPER_TYPE_MAP;
    static {
        WRAPPER_TYPE_MAP = new HashMap<>(8);
        WRAPPER_TYPE_MAP.put(Integer.class, int.class);
        WRAPPER_TYPE_MAP.put(Boolean.class, boolean.class);
        WRAPPER_TYPE_MAP.put(Double.class, double.class);
        WRAPPER_TYPE_MAP.put(Float.class, float.class);
        WRAPPER_TYPE_MAP.put(Long.class, long.class);
        WRAPPER_TYPE_MAP.put(Short.class, short.class);
    }

    // Regular expressions for parsing inline formats.
    // Name of the style, regexp start, regexp end.
    private static final String[] INLINE_STYLE_NAME = {
            "ST", "EM", "DL", "CO"
    };
    private static final Pattern[] INLINE_STYLE_RE = {
            Pattern.compile("(?<=^|[\\W_])\\*([^*]+[^\\s*])\\*(?=$|[\\W_])"), // bold *bo*
            Pattern.compile("(?<=^|\\W)_([^_]+[^\\s_])_(?=$|\\W)"),    // italic _it_
            Pattern.compile("(?<=^|[\\W_])~([^~]+[^\\s~])~(?=$|[\\W_])"), // strikethough ~st~
            Pattern.compile("(?<=^|\\W)`([^`]+)`(?=$|\\W)")     // code/monospace `mono`
    };

    // Relative weights of formatting spans. Greater index in array means greater weight.
    private static final List<String> FMT_WEIGHTS = Collections.singletonList("QQ");

    private static final String[] ENTITY_NAME = {"LN", "MN", "HT"};
    private static final EntityProc[] ENTITY_PROC = {
            new EntityProc("LN",
                    Pattern.compile("(?<=^|[\\W_])(https?://)?(?:www\\.)?(?:[a-z0-9][-a-z0-9]*[a-z0-9]\\.){1,5}" +
                            "[a-z]{2,6}(?:[/?#:][-a-z0-9@:%_+.~#?&/=]*)?", Pattern.CASE_INSENSITIVE)) {

                @Override
                Map<String, Object> pack(Matcher m) {
                    Map<String, Object> data = new HashMap<>();
                    data.put("url", m.group(1) == null ? "http://" + m.group() : m.group());
                    return data;
                }
            },
            new EntityProc("MN",
                    Pattern.compile("(?<=^|[\\W_])@([\\p{L}\\p{N}][._\\p{L}\\p{N}]*[\\p{L}\\p{N}])")) {
                @Override
                Map<String, Object> pack(Matcher m) {
                    Map<String, Object> data = new HashMap<>();
                    data.put("val", m.group());
                    return data;
                }
            },
            new EntityProc("HT",
                    Pattern.compile("(?<=^|[\\W_])#([\\p{L}\\p{N}][._\\p{L}\\p{N}]*[\\p{L}\\p{N}])")) {
                @Override
                Map<String, Object> pack(Matcher m) {
                    Map<String, Object> data = new HashMap<>();
                    data.put("val", m.group());
                    return data;
                }
            }
    };

    public String txt;
    public Style[] fmt;
    public Entity[] ent;

    @JsonIgnore
    private transient int[] sizes;

    public Drafty() {
        txt = null;
        fmt = null;
        ent = null;
    }

    public Drafty(String content) {
        Drafty that = parse(content);

        this.txt = that.txt;
        this.fmt = that.fmt;
        this.ent = that.ent;
        this.sizes = that.sizes;
    }

    /**
     * Creates Drafty document with txt set to the parameter without any parsing.
     * This is needed in order to disable secondary parsing of received plain-text messages.
     * Used by Jackson XML to deserialize plain text received from the server.
     *
     * @param plainText text assigned without parsing.
     * @return Drafty document with <code>txt</code> set to the parameter.
     */
    @JsonCreator
    public static Drafty fromPlainText(String plainText) {
        Drafty that = new Drafty();
        that.txt = Normalizer.normalize(plainText, Normalizer.Form.NFC);
        return that;
    }

    protected Drafty(String text, Style[] fmt, Entity[] ent) {
        this.txt = text;
        this.fmt = fmt;
        this.ent = ent;
    }

    // Detect starts and ends of formatting spans. Unformatted spans are
    // ignored at this stage.
    private static List<Span> spannify(String original, Pattern re, String type) {
        List<Span> spans = new ArrayList<>();
        Matcher matcher = re.matcher(original);
        while (matcher.find()) {
            Span s = new Span();
            s.start = matcher.start(0);  // 'hello *world*'
                                                // ^ group(zero) -> index of the opening markup character
            s.end = matcher.end(1);      // group(one) -> index of the closing markup character
            s.text = matcher.group(1);          // text without the markup
            s.type = type;
            spans.add(s);
        }
        return spans;
    }

    // Take a string and defined earlier style spans, re-compose them into a tree where each leaf is
    // a same-style (including unstyled) string. I.e. 'hello *bold _italic_* and ~more~ world' ->
    // ('hello ', (b: 'bold ', (i: 'italic')), ' and ', (s: 'more'), ' world');
    //
    // This is needed in order to clear markup, i.e. 'hello *world*' -> 'hello world' and convert
    // ranges from markup-ed offsets to plain text offsets.
    private static List<Span> chunkify(String line, int start, int end, List<Span> spans) {

        if (spans == null || spans.isEmpty()) {
            return null;
        }

        List<Span> chunks = new ArrayList<>();
        for (Span span : spans) {
            // Grab the initial unstyled chunk.

            if (span.start > start) {
                chunks.add(new Span(line.substring(start, span.start)));
            }

            // Grab the styled chunk. It may include subchunks.
            Span chunk = new Span();
            chunk.type = span.type;

            List<Span> chld = chunkify(line, span.start + 1, span.end, span.children);
            if (chld != null) {
                chunk.children = chld;
            } else {
                chunk.text = span.text;
            }

            chunks.add(chunk);
            start = span.end + 1; // '+1' is to skip the formatting character
        }

        // Grab the remaining unstyled chunk, after the last span
        if (start < end) {
            chunks.add(new Span(line.substring(start, end)));
        }

        return chunks;
    }

    // Convert linear array or spans into a tree representation.
    // Keep standalone and nested spans, throw away partially overlapping spans.
    private static List<Span> toSpanTree(List<Span> spans) {
        if (spans == null || spans.isEmpty()) {
            return null;
        }

        List<Span> tree = new ArrayList<>();

        Span last = spans.get(0);
        tree.add(last);
        for (int i = 1; i < spans.size(); i++) {
            Span curr = spans.get(i);
            // Keep spans which start after the end of the previous span or those which
            // are complete within the previous span.
            if (curr.start > last.end) {
                // Span is completely outside of the previous span.
                tree.add(curr);
                last = curr;
            } else if (curr.end < last.end) {
                // Span is fully inside of the previous span. Push to subnode.
                if (last.children == null) {
                    last.children = new ArrayList<>();
                }
                last.children.add(curr);
            }
            // Span could also partially overlap, ignore it as invalid.
        }

        // Recursively rearrange the subnodes.
        for (Span s : tree) {
            s.children = toSpanTree(s.children);
        }

        return tree;
    }

    // Convert a list of chunks into block.
    private static Block draftify(List<Span> chunks, int startAt) {
        if (chunks == null) {
            return null;
        }

        Block block = new Block("");
        List<Style> ranges = new ArrayList<>();
        for (Span chunk : chunks) {
            if (chunk.text == null) {
                Block drafty = draftify(chunk.children, block.txt.length() + startAt);
                if (drafty != null) {
                    chunk.text = drafty.txt;
                    if (drafty.fmt != null) {
                        ranges.addAll(drafty.fmt);
                    }
                }
            }

            if (chunk.type != null) {
                ranges.add(new Style(chunk.type, block.txt.length() + startAt, chunk.text.length()));
            }

            if (chunk.text != null) {
                block.txt += chunk.text;
            }
        }

        if (!ranges.isEmpty()) {
            block.fmt = ranges;
        }

        return block;
    }

    // Get a list of entities from a text.
    private static List<ExtractedEnt> extractEntities(String line) {
        List<ExtractedEnt> extracted = new ArrayList<>();

        for (int i = 0; i < ENTITY_NAME.length; i++) {
            Matcher matcher = ENTITY_PROC[i].re.matcher(line);
            while (matcher.find()) {
                ExtractedEnt ee = new ExtractedEnt();
                ee.at = matcher.start(0);
                ee.value = matcher.group(0);
                //noinspection ConstantConditions
                ee.len = ee.value.length();
                ee.tp = ENTITY_NAME[i];
                ee.data = ENTITY_PROC[i].pack(matcher);
                extracted.add(ee);
            }
        }

        return extracted;
    }

    /**
     * Parse plain text into structured representation.
     *
     * @param  content content with optional markdown-style markup to parse.
     * @return parsed Drafty object.
     */
    public static Drafty parse(String content) {
        if (content == null) {
            return Drafty.fromPlainText("");
        }
        // Normalize possible Unicode 32 codepoints.
        content = Normalizer.normalize(content, Normalizer.Form.NFC);

        // Break input into individual lines. Markdown cannot span multiple lines.
        String[] lines = content.split("\\r?\\n");
        List<Block> blks = new ArrayList<>();
        List<Entity> refs = new ArrayList<>();

        List<Span> spans = new ArrayList<>();
        Map<String, Integer> entityMap = new HashMap<>();
        List<ExtractedEnt> entities;
        for (String line : lines) {
            spans.clear();
            // Select styled spans.
            for (int i = 0;i < INLINE_STYLE_NAME.length; i++) {
                spans.addAll(spannify(line, INLINE_STYLE_RE[i], INLINE_STYLE_NAME[i]));
            }

            Block b;
            if (!spans.isEmpty()) {
                // Sort styled spans in ascending order by .start
                Collections.sort(spans);

                // Rearrange linear list of styled spans into a tree, throw away invalid spans.
                spans = toSpanTree(spans);

                // Parse the entire string into spans, styled or unstyled.
                spans = chunkify(line, 0, line.length(), spans);

                // Convert line into a block.
                b = draftify(spans, 0);
            } else {
                b = new Block(line);
            }

            // Extract entities from the string already cleared of markup.
            entities = extractEntities(b.txt);
            // Normalize entities by splitting them into spans and references.
            for (ExtractedEnt ent : entities) {
                // Check if the entity has been indexed already
                Integer index = entityMap.get(ent.value);
                if (index == null) {
                    index = refs.size();
                    entityMap.put(ent.value, index);
                    refs.add(new Entity(ent.tp, ent.data));
                }

                b.addStyle(new Style(ent.at, ent.len, index));
            }

            blks.add(b);
        }

        StringBuilder text = new StringBuilder();
        List<Style> fmt = new ArrayList<>();
        // Merge lines and save line breaks as BR inline formatting.
        if (!blks.isEmpty()) {
            Block b = blks.get(0);
            if (b.txt != null) {
                text.append(b.txt);
            }
            if (b.fmt != null) {
                for (Style s : b.fmt) {
                    fmt.add(s.toGraphemeCounts(text));
                }
            }

            for (int i = 1; i<blks.size(); i++) {
                int offset = gcLength(text) + 1;
                fmt.add(new Style("BR", offset - 1, 1));

                b = blks.get(i);
                text.append(" ");
                if (b.txt != null) {
                    text.append(b.txt);
                }
                if (b.fmt != null) {
                    for (Style s : b.fmt) {
                        s.at += offset;
                        fmt.add(s);
                    }
                }
            }
        }

        return new Drafty(text.toString(),
                !fmt.isEmpty() ? fmt.toArray(new Style[0]) : null,
                !refs.isEmpty() ? refs.toArray(new Entity[0]) : null);
    }

    // Check if Drafty has at least one entity of the given type.
    public boolean hasEntities(Iterable<String> types) {
        if (ent == null) {
            return false;
        }
        for (Entity e : ent) {
            if (e == null || e.tp == null) {
                continue;
            }
            for (String type : types) {
                if (type.equals(e.tp)) {
                    return true;
                }
            }
        }
        return false;
    }

    @JsonIgnore
    public Entity[] getEntities() {
        return ent;
    }

    @JsonIgnore
    public Style[] getStyles() {
        return fmt;
    }

    /**
     * Extract attachment references for use in message header.
     *
     * @return string array of attachment references or null if no attachments with references found.
     */
    @JsonIgnore
    public String[] getEntReferences() {
        if (ent == null) {
            return null;
        }

        ArrayList<String> result = new ArrayList<>();
        for (Entity anEnt : ent) {
            if (anEnt != null && anEnt.data != null) {
                Object ref = anEnt.data.get("ref");
                if (ref instanceof String) {
                    result.add((String) ref);
                }
                ref = anEnt.data.get("preref");
                if (ref instanceof String) {
                    result.add((String) ref);
                }
            }
        }
        return !result.isEmpty() ? result.toArray(new String[]{}) : null;
    }

    // Ensure Drafty has enough space to add 'count' formatting styles.
    // Returns old length.
    private int prepareForStyle(int count) {
        int len = 0;
        if (fmt == null) {
            fmt = new Style[count];
        } else {
            len = fmt.length;
            fmt = Arrays.copyOf(fmt, fmt.length + count);
        }
        return len;
    }

    // Ensure Drafty is properly initialized for entity insertion.
    private void prepareForEntity(int at, int len) {
        prepareForStyle(1);

        if (ent == null) {
            ent = new Entity[1];
        } else {
            ent = Arrays.copyOf(ent, ent.length + 1);
        }
        fmt[fmt.length - 1] = new Style(at, len, ent.length - 1);
    }

    /**
     * Insert a styled text at the given location.
     * @param at insertion point
     * @param text text to insert
     * @param style formatting style.
     * @param data entity data
     * @return 'this' Drafty document with the new insertion.
     */
    public Drafty insert(int at, @Nullable String text, @Nullable String style, @Nullable Map<String, Object> data) {
        if (at == 0 && txt == null) {
            // Allow insertion into an empty document.
            txt = "";
        }

        if (txt == null || txt.length() < at || at < 0) {
            throw new IndexOutOfBoundsException("Invalid insertion position");
        }

        int addedLength = text != null ? text.length() : 0;
        if (addedLength > 0) {
            if (fmt != null) {
                // Shift all existing styles by inserted length.
                for (Style f : fmt) {
                    if (f.at >= 0) {
                        f.at += addedLength;
                    }
                }
            }

            // Insert the new string at the requested location.
            txt = txt.substring(0, at) + text + txt.substring(at);
        }

        if (style != null) {
            if (data != null) {
                // Adding an entity
                prepareForEntity(at, addedLength);
                ent[ent.length - 1] = new Entity(style, data);
            } else {
                // Adding formatting style only.
                prepareForStyle(1);
                fmt[fmt.length - 1] = new Style(style, at, addedLength);
            }
        }
        return this;
    }

    /**
     * Insert inline image
     *
     * @param at location to insert image at
     * @param mime Content-type, such as 'image/jpeg'.
     * @param bits Content as an array of bytes
     * @param width image width in pixels
     * @param height image height in pixels
     * @param fname name of the file to suggest to the receiver.
     * @param refurl Reference to full/extended image.
     * @param size file size hint (in bytes) as reported by the client.
     *
     * @return 'this' Drafty object.
     */
    @SuppressWarnings("UnusedReturnValue")
    public Drafty insertImage(int at,
                              @Nullable String mime,
                              byte[] bits, int width, int height,
                              @Nullable String fname,
                              @Nullable URI refurl,
                              long size) {
        if (bits == null && refurl == null) {
            throw new IllegalArgumentException("Either image bits or reference URL must not be null.");
        }

        Map<String,Object> data = new HashMap<>();
        addOrSkip(data, "mime", mime);
        addOrSkip(data, "val", bits);
        data.put("width", width);
        data.put("height", height);
        addOrSkip(data,"name", fname);
        if (refurl != null) {
            addOrSkip(data, "ref", refurl.toString());
        }
        if (size > 0) {
            data.put("size", size);
        }

        insert(at, " ", "IM", data);

        return this;
    }

    /**
     * Attach file to a drafty object in-band.
     *
     * @param mime Content-type, such as 'text/plain'.
     * @param bits Content as an array of bytes.
     * @param fname Optional file name to suggest to the receiver.
     * @return 'this' Drafty object.
     */
    @SuppressWarnings("UnusedReturnValue")
    public Drafty attachFile(String mime, byte[] bits, String fname) {
        return attachFile(mime, bits, fname, null, bits.length);
    }

    /**
     * Attach file to a drafty object as a reference.
     *
     * @param mime Content-type, such as 'text/plain'.
     * @param fname Optional file name to suggest to the receiver
     * @param refurl reference to content location. If URL is relative, assume current server.
     * @param size size of the attachment (untrusted).
     * @return 'this' Drafty object.
     */
    @SuppressWarnings("UnusedReturnValue")
    public Drafty attachFile(String mime, String fname, String refurl, long size) {
        return attachFile(mime, null, fname, refurl, size);
    }

    /**
     * Attach file to a drafty object.
     *
     * @param mime Content-type, such as 'text/plain'.
     * @param fname Optional file name to suggest to the receiver.
     * @param bits File content to include inline.
     * @param refurl Reference to full/extended file content.
     * @param size file size hint as reported by the client.
     *
     * @return 'this' Drafty object.
     */
    protected Drafty attachFile(String mime, byte[] bits, String fname, String refurl, long size) {
        if (bits == null && refurl == null) {
            throw new IllegalArgumentException("Either file bits or reference URL must not be null.");
        }

        prepareForEntity(-1, 1);

        final Map<String,Object> data = new HashMap<>();
        addOrSkip(data, "mime", mime);
        addOrSkip(data, "val", bits);
        addOrSkip(data, "name", fname);
        addOrSkip(data, "ref", refurl);
        if (size > 0) {
            data.put("size", size);
        }
        ent[ent.length - 1] = new Entity("EX", data);

        return this;
    }

    /**
     * Attach object as json. Intended to be used as a form response.
     *
     * @param json object to attach.
     * @return 'this' Drafty document.
     */
    @SuppressWarnings("UnusedReturnValue")
    public Drafty attachJSON(@NotNull Map<String,Object> json) {
        prepareForEntity(-1, 1);

        Map<String, Object> data = new HashMap<>();
        data.put("mime", DRAFTY_FR_TYPE);
        data.put("val", json);

        ent[ent.length - 1] = new Entity("EX", data);

        return this;
    }

    /**
     * Insert audio recording into Drafty document.
     *
     * @param at location to insert at.
     * @param mime Content-type, such as 'audio/aac'.
     * @param bits Audio content to include inline.
     * @param preview Amplitude bars to show as preview.
     * @param duration Record duration in milliseconds.
     * @param fname Optional file name to suggest to the receiver.
     * @param refurl Reference to audio content sent out of band.
     * @param size File size hint as reported by the client.
     *
     * @return <code>this</code> Drafty document.
     */
    public Drafty insertAudio(int at,
                              @NotNull String mime,
                              byte[] bits,
                              byte[] preview,
                              int duration,
                              @Nullable String fname,
                              @Nullable URI refurl,
                              long size) {
        if (bits == null && refurl == null) {
            throw new IllegalArgumentException("Either audio bits or reference URL must not be null.");
        }

        Map<String,Object> data = new HashMap<>();
        data.put("mime", mime);
        addOrSkip(data, "val", bits);
        data.put("duration", duration);
        addOrSkip(data, "preview", preview);
        addOrSkip(data,"name", fname);
        if (refurl != null) {
            addOrSkip(data, "ref", refurl.toString());
        }
        if (size > 0) {
            data.put("size", size);
        }

        insert(at, " ", "AU", data);
        return this;
    }

    /**
     * Insert audio recording into Drafty document.
     *
     * @param at Location to insert video at.
     * @param mime Content-type, such as 'video/webm'.
     * @param bits Video content to include inline if video is very small.
     * @param width Width of the video.
     * @param height Height of the video.
     * @param preview image poster for the video to include inline.
     * @param preref URL of an image poster.
     * @param premime Content-type of the image poster, such as 'image/png'.
     * @param duration Record duration in milliseconds.
     * @param fname Optional file name to suggest to the receiver.
     * @param refurl Reference to video content sent out of band.
     * @param size File size hint as reported by the client.
     *
     * @return <code>this</code> Drafty document.
     */
    @SuppressWarnings("UnusedReturnValue")
    public Drafty insertVideo(int at,
                              @NotNull String mime,
                              byte[] bits,
                              int width, int height,
                              byte[] preview,
                              @Nullable URI preref,
                              @Nullable String premime,
                              int duration,
                              @Nullable String fname,
                              @Nullable URI refurl,
                              long size) {
        if (bits == null && refurl == null) {
            throw new IllegalArgumentException("Either video bits or reference URL must not be null.");
        }

        Map<String,Object> data = new HashMap<>();
        data.put("mime", mime);
        addOrSkip(data, "val", bits);
        data.put("duration", duration);
        addOrSkip(data, "preview", preview);
        addOrSkip(data, "premime", premime);
        if (preref != null) {
            addOrSkip(data, "preref", preref.toString());
        }
        addOrSkip(data,"name", fname);
        data.put("width", width);
        data.put("height", height);
        if (refurl != null) {
            addOrSkip(data, "ref", refurl.toString());
        }
        if (size > 0) {
            data.put("size", size);
        }

        insert(at, " ", "VD", data);
        return this;
    }

    /**
     * Append one Drafty document to another Drafty document
     *
     * @param that Drafty document to append to the current document.
     * @return 'this' Drafty document.
     */
    public Drafty append(@Nullable Drafty that) {
        if (that == null) {
            return this;
        }

        int len = txt != null ? gcLength(txt) : 0;
        if (that.txt != null) {
            if (txt != null) {
                txt += that.txt;
            } else {
                txt = that.txt;
            }
        }

        if (that.fmt != null && that.fmt.length > 0) {
            // Insertion point for styles.
            int fmt_idx;
            // Insertion point for entities.
            int ent_idx = 0;

            // Allocate space for copying styles and entities.
            fmt_idx = prepareForStyle(that.fmt.length);
            if (that.ent != null && that.ent.length > 0) {
                if (ent == null) {
                    ent = new Entity[that.ent.length];
                } else {
                    ent_idx = ent.length;
                    ent = Arrays.copyOf(ent, ent.length + that.ent.length);
                }
            }

            for (Style thatst : that.fmt) {
                int at = thatst.at >= 0 ? thatst.at + len : -1;
                Style style = new Style(null, at, thatst.len);
                int key = thatst.key != null ? thatst.key : 0;
                if (thatst.tp != null && !thatst.tp.isEmpty()) {
                    style.tp = thatst.tp;
                } else if (that.ent != null && that.ent.length > key) {
                    style.key = ent_idx;
                    ent[ent_idx ++] = that.ent[key];
                } else {
                    continue;
                }
                fmt[fmt_idx ++] = style;
            }
        }

        return this;
    }

    /**
     * Append line break 'BR' to Drafty document.
     *
     * @return 'this' Drafty document.
     */
    public Drafty appendLineBreak() {
        if (txt == null) {
            txt = "";
        }

        prepareForStyle(1);
        fmt[fmt.length - 1] = new Style("BR", gcLength(txt), 1);

        txt += " ";

        return this;
    }

    /**
     * Create a Drafty document consisting of a single mention.
     * @param name human-readable name of the mentioned user.
     * @param uid is the user ID to be mentioned.
     * @return new Drafty object.
     */
    public static Drafty mention(@NotNull String name, @NotNull String uid) {
        Drafty d = Drafty.fromPlainText(name);
        d.fmt = new Style[]{
             new Style(0, gcLength(name), 0)
        };
        d.ent = new Entity[]{
                new Entity("MN").putData("val", uid)
        };
        return d;
    }

    /**
     * Create a (self-contained) video call Drafty document.
     * @return new Drafty representing a video call.
     */
    public static Drafty videoCall() {
        Drafty d = new Drafty(" ");
        d.fmt = new Style[]{
                new Style(0, 1, 0)
        };
        d.ent = new Entity[]{
                new Entity("VC")
        };
        return d;
    }

    @SuppressWarnings("UnusedReturnValue")
    public static Drafty updateVideoEnt(@NotNull Drafty src, @Nullable Map<String, Object> params, boolean incoming) {
        // The video element could be just a format or a format + entity.
        // Must ensure it's the latter first.
        Style[] fmt = src.fmt;
        if (fmt == null || fmt.length == 0 || (fmt[0].tp != null && !"VC".equals(fmt[0].tp)) || params == null) {
            return src;
        }

        if (fmt[0].tp != null) {
            // Just a format, convert to format + entity.
            fmt[0].tp = null;
            fmt[0].key = 0;
            src.ent = new Entity[]{new Entity("VC")};
        } else if (src.ent == null || src.ent.length == 0 || !"VC".equals(src.ent[0].tp)) {
            // No VC entity.
            return src;
        }
        src.ent[0].putData("state", params.get("webrtc"));
        src.ent[0].putData("duration", params.get("webrtc-duration"));
        src.ent[0].putData("incoming", incoming);

        return src;
    }

    /**
     * Wrap contents of the document into the specified style.
     * @param style to wrap document into.
     * @return 'this' Drafty document.
     */
    public Drafty wrapInto(@NotNull String style) {
        prepareForStyle(1);
        fmt[fmt.length - 1] = new Style(style, 0, gcLength(txt));
        return this;
    }

    /**
     * Insert button into Drafty document.
     * @param at is the location where the button is inserted.
     * @param title is a button title.
     * @param id is an opaque ID of the button. Client should just return it to the server when the button is clicked.
     * @param actionType is the type of the button, one of 'url' or 'pub'.
     * @param actionValue is the value associated with the action: 'url': URL, 'pub': optional data to add to response.
     * @param refUrl parameter required by URL buttons: url to go to on click.
     *
     * @return 'this' Drafty object.
     */
    @SuppressWarnings("unused")
    protected Drafty insertButton(int at, @Nullable String title, @Nullable String id,
                                  @NotNull String actionType,
                                  @Nullable String actionValue,
                                  @Nullable String refUrl) {
        if (!"url".equals(actionType) && !"pub".equals(actionType)) {
            throw new IllegalArgumentException("Unknown action type "+actionType);
        }
        if ("url".equals(actionType) && refUrl == null) {
            throw new IllegalArgumentException("URL required for URL buttons");
        }

        final Map<String,Object> data = new HashMap<>();
        data.put("act", actionType);
        addOrSkip(data, "name", id);
        addOrSkip(data, "val", actionValue);
        addOrSkip(data, "ref", refUrl);
        insert(at, title, "BN", data);
        return this;
    }

    /**
     * Create a quote of a given Drafty document.
     *
     * @param header - Quote header (title, etc.).
     * @param uid - UID of the author to mention.
     * @param body - Body of the quoted message.
     *
     * @return a Drafty doc with the quote formatting.
     */
    public static Drafty quote(String header, String uid, Drafty body) {
        return Drafty.mention(header, uid)
                .appendLineBreak()
                .append(body)
                .wrapInto("QQ");
    }

    /**
     * Check if given content-type is a mime type of a drafty form response.
     * @param mimeType content type to check.
     * @return true if content-type is a mime type of a drafty form response, false otherwise.
     */
    public static boolean isFormResponseType(Object mimeType) {
        return DRAFTY_FR_TYPE.equals(mimeType) || DRAFTY_FR_TYPE_LEGACY.equals(mimeType);
    }

    /**
     * Check if the given Drafty can be represented by plain text.
     *
     * @return true if this Drafty has no markup other thn line breaks.
     */
    @JsonIgnore
    public boolean isPlain() {
        return (ent == null && fmt == null);
    }

    /**
     * Format converts Drafty object into a collection of formatted nodes.
     * Each node contains either a formatted element or a collection of
     * formatted elements.
     *
     * @param formatter is an interface with an `apply` method. It's iteratively
     *                   applied to every node in the tree.
     * @return a tree of components.
     */
    public <T> T format(@NotNull Formatter<T> formatter) {
        Node tree = toTree();
        return treeBottomUp(tree, formatter, new Stack<>());
    }

    /**
     * Mostly for testing: convert Drafty to a markdown string.
     * @param plainLink links should be written as plain text, without any formatting.
     * @return Drafty as markdown-formatted string; elements not representable as markdown are converted to plain text.
     */
    public String toMarkdown(boolean plainLink) {
        return format(new Formatter<>() {
            final boolean usePlainLink = plainLink;

            @Override
            public String wrapText(CharSequence text) {
                return text.toString();
            }

            @Override
            public String apply(String tp, Map<String, Object> attr, List<String> content, Stack<String> context) {
                String res;

                if (content == null) {
                    res = null;
                } else {
                    StringBuilder joined = new StringBuilder();
                    for (String s : content) {
                        joined.append(s);
                    }
                    res = joined.toString();
                }

                if (tp == null) {
                    return res;
                }

                switch (tp) {
                    case "BR":
                        res = "\n";
                        break;
                    case "HT":
                        res = "#" + res;
                        break;
                    case "MN":
                        res = "@" + res;
                        break;
                    case "ST":
                        res = "*" + res + "*";
                        break;
                    case "EM":
                        res = "_" + res + "_";
                        break;
                    case "DL":
                        res = "~" + res + "~";
                        break;
                    case "CO":
                        res = "`" + res + "`";
                        break;
                    case "LN":
                        if (!usePlainLink) {
                            res = "[" + res + "](" + attr.get("url") + ")";
                        }
                        break;
                }

                return res;
            }
        });
    }

    // Returns a tree of nodes.
    @NotNull
    private static Node spansToTree(int[] sizes, @NotNull Node parent,
                                    final @NotNull CharSequence text,
                                    int start, int end,
                                    @Nullable List<Span> spans) {
        if (end > sizes.length) {
            end = sizes.length;
        }

        // Process unstyled range.
        if (spans == null) {
            if (start < end) {
                parent.add(new Node(gcSubSequence(sizes, text, start, end)));
            }
            return parent;
        }

        // Process subspans.
        ListIterator<Span> iter = spans.listIterator();
        while (iter.hasNext()) {
            Span span = iter.next();

            if (span.start < 0 && span.type.equals("EX")) {
                parent.add(new Node(span.type, span.data, span.key, true));
                continue;
            }

            // Add un-styled range before the styled span starts.
            if (start < span.start) {
                parent.add(new Node(gcSubSequence(sizes, text, start, span.start)));
                start = span.start;
            }

            // Get all spans which are within the current span.
            List<Span> subspans = new LinkedList<>();
            while (iter.hasNext()) {
                Span inner = iter.next();
                if (inner.start < 0 || inner.start >= span.end) {
                    // Either an attachment or past the end of the current span, put back and stop.
                    iter.previous();
                    break;
                } else if (inner.end <= span.end) {
                    if (inner.start < inner.end || inner.isVoid()) {
                        // Valid subspan: completely within the current span and
                        // either non-zero length or zero length is acceptable.
                        subspans.add(inner);
                    }
                }
                // else: overlapping subspan, ignore it.
            }

            if (subspans.isEmpty()) {
                subspans = null;
            }

            parent.add(spansToTree(sizes, new Node(span.type, span.data, span.key),
                    text, start, span.end, subspans));

            start = span.end;
        }

        // Add the last unformatted range.
        if (start < end) {
            parent.add(new Node(gcSubSequence(sizes, text, start, end)));
        }

        return parent;
    }

    @Nullable
    // Traverse tree top down.
    protected static Node treeTopDown(@NotNull Node node, @NotNull Transformer tr) {
        node = tr.transform(node);
        if (node == null || node.children == null) {
            return node;
        }

        LinkedList<Node> children = new LinkedList<>();
        for (Node n : node.children) {
            n = treeTopDown(n, tr);
            if (n != null) {
                children.add(n);
            }
        }

        if (children.isEmpty()) {
            node.children = null;
        } else {
            node.children = children;
        }
        return node;
    }

    // Traverse the tree bottom-up: apply formatter to every node.
    protected static <T> T treeBottomUp(Node src, Formatter<T> formatter, Stack<String> stack) {
        if (src == null) {
            return null;
        }

        if (stack != null && src.tp != null) {
            stack.push(src.tp);
        }

        LinkedList<T> content = new LinkedList<>();
        if (src.children != null) {
            for (Node node : src.children) {
                T val = treeBottomUp(node, formatter, stack);
                if (val != null) {
                    content.add(val);
                }
            }
        } else if (src.text != null) {
            content.add(formatter.wrapText(src.text));
        }

        if (content.isEmpty()) {
            content = null;
        }

        if (stack != null && src.tp != null) {
            stack.pop();
        }

        return formatter.apply(src.tp, src.data, content, stack);
    }

    // Convert Drafty document to a tree of formatted nodes.
    protected Node toTree() {
        CharSequence text = txt == null ? "" : txt;
        int entCount = ent != null ? ent.length : 0;
        int[] sizes = gcSizes(text);

        // Handle special case when all values in fmt are 0 and fmt therefore was
        // skipped.
        if (fmt == null || fmt.length == 0) {
            if (entCount == 1) {
                fmt = new Style[1];
                fmt[0] = new Style(0, 0, 0);
            } else {
                return new Node(text);
            }
        }

        // Sanitize spans
        List<Span> spans = new ArrayList<>();
        List<Span> attachments = new ArrayList<>();
        int maxIndex = sizes.length;

        for (Style aFmt : fmt) {
            if (aFmt == null || aFmt.len < 0) {
                // Invalid span.
                continue;
            }
            int key = aFmt.key != null ? aFmt.key : 0;
            if (ent != null && (key < 0 || key >= entCount || ent[key] == null)) {
                // Invalid key or entity.
                continue;
            }

            if (aFmt.at <= -1) {
                // Attachment. Store attachments separately.
                attachments.add(new Span(-1, 0, key));
                continue;
            } else if (aFmt.at + aFmt.len > maxIndex) {
                // Span is out of bounds.
                continue;
            }

            if (aFmt.isUnstyled()) {
                if (ent != null && ent[key] != null) {
                    // No type, entity reference.
                    spans.add(new Span(aFmt.at, aFmt.at + aFmt.len, key));
                }
            } else {
                // Has type: normal format.
                spans.add(new Span(aFmt.tp, aFmt.at, aFmt.at + aFmt.len));
            }
        }
        // Sort spans first by start index (asc) then by length (desc).
        spans.sort((a, b) -> {
            int diff = a.start - b.start;
            if (diff != 0) {
                return diff;
            }
            diff = b.end - a.end; // longer one comes first (<0)
            if (diff != 0) {
                return diff;
            }
            return FMT_WEIGHTS.indexOf(b.type) - FMT_WEIGHTS.indexOf(a.type);
        });

        // Move attachments to the end of the list.
        if (!attachments.isEmpty()) {
            spans.addAll(attachments);
        }

        for (Span span : spans) {
            if (ent != null && span.isUnstyled()) {
                span.type = ent[span.key].tp;
                span.data = ent[span.key].data;
            }

            // Is type still undefined? Hide the invalid element!
            if (span.isUnstyled()) {
                span.type = "HD";
            }
        }
        Node tree = spansToTree(sizes, new Node(), text, 0, sizes.length, spans);

        // Flatten tree nodes, remove styling from buttons, copy button text to 'title' data.
        return treeTopDown(tree, new Transformer() {
            @Override
            public Node transform(Node node) {
                if (node.children != null && node.children.size() == 1) {
                    // Unwrap.
                    Node child = node.children.get(0);
                    if (node.isUnstyled()) {
                        Node parent = node.parent;
                        node = child;
                        node.parent = parent;
                    } else if (child.isUnstyled() && child.children == null) {
                        node.text = child.text;
                        node.children = null;
                    }
                }

                if (node.isStyle("BN")) {
                    node.putData("title", node.text != null ? node.text.toString() : "null");
                }
                return node;
            }
        });
    }

    // Clip tree to the provided limit.
    // If the tree is shortened, prepend tail.
    @SuppressWarnings("SameParameterValue")
    protected static Node shortenTree(Node tree, int length, String tail) {
        if (tail != null) {
            length -= tail.length();
        }

        return treeTopDown(tree, new Transformer() {
            private int limit;

            @SuppressWarnings("unused")
            Transformer init(int limit) {
                this.limit = limit;
                return this;
            }

            @Override
            public @Nullable Node transform(Node node) {
                if (limit <= -1) {
                    // Limit -1 means the doc was already clipped.
                    return null;
                }

                if (node.attachment) {
                    // Attachments are unchanged.
                    return node;
                }
                if (limit == 0) {
                    node.text = tail != null ? new StringBuilder(tail) : null;
                    limit = -1;
                } else if (node.text != null) {
                    int len = gcLength(node.text);
                    if (len > limit) {
                        int clipAt = gcOffset(node.text, limit);
                        node.text.setLength(clipAt);
                        if (tail != null) {
                            node.text.append(tail);
                        }
                        limit = -1;
                    } else {
                        limit -= len;
                    }
                }
                return node;
            }
        }.init(length));
    }

    // Move attachments to the end. Attachments must be at the top level, no need to traverse the tree.
    protected static void attachmentsToEnd(Node tree, int maxAttachments) {
        if (tree == null) {
            return;
        }

        if (tree.attachment) {
            tree.text = new StringBuilder(" ");
            tree.attachment = false;
            tree.children = null;
        } else if (tree.children != null) {
            List<Node> children = new ArrayList<>();
            List<Node> attachments = new ArrayList<>();
            for (Node c : tree.children) {
                if (c.attachment) {
                    if (attachments.size() == maxAttachments) {
                        // Too many attachments to preview;
                        continue;
                    }

                    if (isFormResponseType(c.getData("mime"))) {
                        // JSON attachments are not shown in preview.
                        continue;
                    }

                    c.attachment = false;
                    c.children = null;
                    c.text = new StringBuilder(" ");
                    attachments.add(c);
                } else {
                    children.add(c);
                }
            }

            children.addAll(attachments);
            tree.children = children;
        }
    }

    // Strip heavy entities from a tree.
    protected static Node lightEntity(Node tree) {
        return treeTopDown(tree, new Transformer() {
            @Override
            public Node transform(Node node) {
                node.data = copyEntData(node.data, MAX_PREVIEW_DATA_SIZE);
                return node;
            }
        });
    }

    /**
     * Convert Drafty to plain text.
     * @return plain text representation of the Drafty document.
     */
    @SuppressWarnings("unused")
    public String toPlainText() {
        StringBuilder sb = new StringBuilder("{");
        if (txt != null) {
            sb.append("txt: '").append(txt).append("', ");
        }
        if (fmt != null && fmt.length > 0) {
            sb.append("fmt: [");
            for (Style f : fmt) {
                sb.append(f).append(", ");
            }
            sb.append("], ");
        }
        if (ent != null && ent.length > 0) {
            sb.append("ent: [");
            for (Entity e : ent) {
                sb.append(e).append(", ");
            }
            sb.append("]");
        }
        sb.append("}");
        return sb.toString();
    }

    // Convert Drafty to plain text;
    @NotNull
    @Override
    public String toString() {
        return txt != null ? txt : "";
    }

    @Override
    public boolean equals(Object another) {
        if (another instanceof Drafty that) {
            return equalsNullable(this.txt, that.txt) &&
                    Arrays.equals(this.fmt, that.fmt) &&
                    Arrays.equals(this.ent, that.ent);
        }
        return false;
    }

    /**
     * Shorten Drafty document.
     * @param length length in characters to shorten to.
     * @return new shortened Drafty object leaving the original intact.
     */
    public Drafty shorten(final int length, final boolean light) {
        Node tree = toTree();
        tree = shortenTree(tree, length, "…");
        if (light) {
            tree = lightEntity(tree);
        }
        return tree.toDrafty();
    }

    /**
     * Shorten Drafty document and strip all entity data leaving just inline styles and entity references.
     * @param length length in characters to shorten to.
     * @return new shortened Drafty object leaving the original intact.
     */
    public Drafty preview(final int length) {
        Node tree = toTree();
        // Move attachments to the end.
        attachmentsToEnd(tree, MAX_PREVIEW_ATTACHMENTS);
        tree = treeTopDown(tree, new Transformer() {
            @Override
            public Node transform(Node node) {
                if (node.isStyle("MN")) {
                    if (node.text != null &&
                            node.text.length() > 0 &&
                            node.text.charAt(0) == '➦' &&
                            (node.parent == null || node.parent.isUnstyled())) {
                        node.text = new StringBuilder("➦");
                        node.children = null;
                    }
                } else if (node.isStyle("QQ")) {
                    node.text = new StringBuilder(" ");
                    node.children = null;
                } else if (node.isStyle("BR")) {
                    node.text = new StringBuilder(" ");
                    node.children = null;
                    node.tp = null;
                }
                return node;
            }
        });

        tree = shortenTree(tree, length, "…");
        tree = lightEntity(tree);

        return tree.toDrafty();
    }

    /**
     * Remove leading @mention from Drafty document and any leading line breaks making document
     * suitable for forwarding.
     * @return Drafty document suitable for forwarding.
     */
    @Nullable
    public Drafty forwardedContent() {
        Node tree = toTree();
        // Strip leading mention.
        tree = treeTopDown(tree, new Transformer() {
            @Override
            public Node transform(Node node) {
                if (node.isStyle("MN")) {
                    if (node.parent == null || node.parent.tp == null) {
                        return null;
                    }
                }
                return node;
            }
        });

        if (tree == null) {
            return null;
        }

        // Remove leading whitespace.
        tree.lTrim();
        // Convert back to Drafty.
        return tree.toDrafty();
    }

    /**
     * Prepare Drafty doc for wrapping into QQ as a reply:
     *  - Replace forwarding mention with symbol '➦' and remove data (UID).
     *  - Remove quoted text completely.
     *  - Replace line breaks with spaces.
     *  - Strip entities of heavy content.
     *  - Move attachments to the end of the document.
     *
     * @param length- length in characters to shorten to.
     * @param maxAttachments - maximum number of attachments to keep.
     * @return converted Drafty object leaving the original intact.
     */
    @NotNull
    public Drafty replyContent(int length, int maxAttachments) {
        Node tree = toTree();

        // Strip quote blocks, shorten leading mention, convert line breaks to spaces.
        tree = treeTopDown(tree, new Transformer() {
            @Override
            public @Nullable Node transform(Node node) {
                if (node.isStyle("QQ")) {
                    return null;
                } else if (node.isStyle("MN")) {
                    if (node.text != null && node.text.charAt(0) == '➦' &&
                            (node.parent == null || node.parent.isUnstyled())) {
                        node.text = new StringBuilder("➦");
                        node.children = null;
                        node.data = null;
                    }
                } else if (node.isStyle("BR")) {
                    node.text = new StringBuilder(" ");
                    node.tp = null;
                    node.children = null;
                } else if (node.isStyle("IM") || node.isStyle("VD")) {
                    if (node.data != null) {
                        // Do not rend references to out-of-band large images.
                        node.data.remove("ref");
                        node.data.remove("preref");
                    }
                }
                return node;
            }
        });

        // Move attachments to the end of the doc.
        attachmentsToEnd(tree, maxAttachments);
        // Shorten the doc.
        tree = shortenTree(tree, length, "…");
        String[] imAllow = new String[]{"val"};
        String[] vdAllow = new String[]{"preview"};
        tree = treeTopDown(tree, new Transformer() {
            @Override
            public Node transform(Node node) {
                node.data = copyEntData(node.data, MAX_PREVIEW_DATA_SIZE,
                        node.isStyle("IM") ? imAllow : node.isStyle("VD") ? vdAllow : null);
                return node;
            }
        });
        // Convert back to Drafty.
        return tree == null ? new Drafty() : tree.toDrafty();
    }

    /**
     * Apply custom transformer to Drafty.
     * @param transformer transformer to apply.
     * @return transformed document.
     */
    @NotNull
    public Drafty transform(Transformer transformer) {
        // Apply provided transformer.
        Node tree = treeTopDown(toTree(), transformer);
        return tree == null ? new Drafty() : tree.toDrafty();
    }

    public static class Style implements Serializable, Comparable<Style> {
        public int at;
        public int len;
        public String tp;
        public Integer key;

        @SuppressWarnings("unused")
        public Style() {}

        // Basic inline formatting
        @SuppressWarnings("WeakerAccess")
        public Style(String tp, int at, int len) {
            this.at = at;
            this.len = len;
            this.tp = tp;
            this.key = null;
        }

        // Entity reference
        public Style(int at, int len, int key) {
            this.tp = null;
            this.at = at;
            this.len = len;
            this.key = key;
        }

        boolean isUnstyled() {
            return tp == null || tp.isEmpty();
        }
        @NotNull
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder("{");
            if (tp != null) {
                sb.append("tp: '").append(tp).append("', ");
            }
            sb.append("at: ").append(at).append(", len: ").append(len);
            if (key != null) {
                sb.append(", key: ").append(key);
            }
            sb.append("}");
            return sb.toString();
        }

        @Override
        public int compareTo(Style that) {
            if (this.at == that.at) {
                return that.len - this.len; // longer one comes first (<0)
            }
            return this.at - that.at;
        }

        @Override
        public boolean equals(Object another) {
            if (another instanceof Style that) {
                return this.at == that.at && this.len == that.len &&
                        equalsNullable(this.key, that.key) &&
                        equalsNullable(this.tp, that.tp);
            }
            return false;
        }

        // Convert 'at' and 'len' values from char indexes to grapheme .
        Style toGraphemeCounts(StringBuilder text) {
            len = gcCount(text, at, at + len);
            at = gcCount(text, 0, at);
            return this;
        }
    }

    public static class Entity implements Serializable {
        public String tp;
        public Map<String,Object> data;

        @SuppressWarnings("unused")
        public Entity() {}

        @SuppressWarnings("unused")
        public Entity(String tp, Map<String,Object> data) {
            this.tp = tp;
            this.data = data;
        }

        public Entity(String tp) {
            this.tp = tp;
            this.data = null;
        }

        public Entity putData(String key, Object val) {
            if (data == null) {
                data = new HashMap<>();
            }
            addOrSkip(data, key, val);
            return this;
        }

        @JsonIgnore
        public String getType() {
            return tp;
        }

        @NotNull
        @Override
        public String toString() {
            return "{tp: '" + tp + "', data: " + (data != null ? data.toString() : "null") + "}";
        }

        @Override
        public boolean equals(Object another) {
            if (another instanceof Entity that) {
                return equalsNullable(this.tp, that.tp) && equalsNullable(this.data, that.data);
            }
            return false;
        }
    }

    // Optionally insert nullable value into entity data: null values are not inserted.
    private static void addOrSkip(@NotNull Map<String,Object> data, @NotNull String key, @Nullable Object value) {
        if (value != null) {
            data.put(key, value);
        }
    }

    // Optionally insert nullable value into entity data: null values or empty strings are not inserted.
    private static void addOrSkip(@NotNull Map<String,Object> data, @NotNull String key, @Nullable String value) {
        if (value != null && !value.isEmpty()) {
            data.put(key, value);
        }
    }

    // Create a copy of entity data with (light=false) or without (light=true) the large payload.
    private static Map<String,Object> copyEntData(Map<String,Object> data, int maxLength, String[] allow) {
        if (data != null && !data.isEmpty()) {
            Map<String,Object> dc = new HashMap<>();
            List<String> allowedFields = allow != null ? Arrays.asList(allow) : null;
            for (String key : DATA_FIELDS) {
                Object value = data.get(key);
                if (maxLength <= 0 || (allowedFields != null && allowedFields.contains(key))) {
                    addOrSkip(dc, key, value);
                    continue;
                }

                if (value != null) {
                    if (WRAPPER_TYPE_MAP.containsKey(value.getClass())) {
                        // Primitive type.
                        dc.put(key, value);
                        continue;
                    }
                    if (value instanceof String) {
                        if (((String) value).length() <= maxLength) {
                            dc.put(key, value);
                        }
                        continue;
                    }
                    if (value instanceof byte[]) {
                        if (((byte[]) value).length <= maxLength) {
                            dc.put(key, value);
                        }
                    }
                }
            }

            if (!dc.isEmpty()) {
                return dc;
            }
        }
        return null;
    }
    // Create a copy of entity data with (light=false) or without (light=true) the large payload.
    @SuppressWarnings("SameParameterValue")
    private static Map<String,Object> copyEntData(Map<String,Object> data, int maxLength) {
        return copyEntData(data, maxLength, null);
    }

    public interface Formatter<T> {
        /**
         * Format one span.
         *
         * @param tp span style such as "EM", "LN", etc.
         * @param attr attributes of the format, for example URL for "LN" or image for "IM".
         * @param content span content: null, CharSequence, or List<T>.
         * @param context styles of parent elements.
         * @return formatted span.
         */
        T apply(String tp, Map<String,Object> attr, List<T> content, Stack<String> context);

        /**
         * Takes CharSequence and wraps it into the type used by formatter.
         * @param text text to wrap.
         * @return wrapped text.
         */
        T wrapText(CharSequence text);
    }

    public interface Transformer {
        @Nullable
        <T extends Node> Node transform(T node);
    }

    public static class Node {
        Node parent;
        String tp;
        Integer key;
        Map<String,Object> data;
        StringBuilder text;
        List<Node> children;
        boolean attachment;

        public Node() {
            parent = null;
            tp = null;
            data = null;
            text = null;
            children = null;
            attachment = false;
        }

        public Node(@NotNull CharSequence content) {
            parent = null;
            tp = null;
            data = null;
            text = new StringBuilder(content);
            children = null;
            attachment = false;
        }

        public Node(@NotNull String tp, @Nullable Map<String,Object> data, int key, boolean attachment) {
            parent = null;
            this.tp = tp;
            this.key = key;
            this.data = data;
            text = null;
            children = null;
            this.attachment = attachment;
        }

        public Node(@NotNull String tp, @Nullable Map<String,Object> data, int key) {
            this(tp, data, key, false);
        }

        @SuppressWarnings("unused")
        public Node(@NotNull String tp, @Nullable Map<String,Object> data,
             @NotNull CharSequence content, int key) {
            parent = null;
            this.tp = tp;
            this.key = key;
            this.data = data;
            text = new StringBuilder(content);
            children = null;
            attachment = false;
        }

        @SuppressWarnings("unused")
        public Node(@NotNull String tp, @Nullable Map<String,Object> data, @NotNull Node node, int key) {
            parent = null;
            this.tp = tp;
            this.key = key;
            this.data = data;
            text = null;
            attachment = false;
            add(node);
        }

        @SuppressWarnings("unused")
        public Node(@NotNull Node node) {
            parent = node.parent;
            tp = node.tp;
            key = node.key;
            data = node.data;
            text = node.text;
            children = node.children;
            attachment = node.attachment;
        }

        public void setStyle(@NotNull String style) {
            tp = style;
        }

        protected void add(@Nullable Node n) {
            if (n == null) {
                return;
            }

            if (children == null) {
                children = new LinkedList<>();
            }

            // If text is present, move it to a subnode.
            if (text != null) {
                Node nn = new Node(text);
                nn.parent = this;
                children.add(nn);
                text = null;
            }

            n.parent = this;
            children.add(n);
        }

        public boolean isStyle(@NotNull String style) {
            return style.equals(tp);
        }

        public boolean isUnstyled() {
            return tp == null || tp.isEmpty();
        }

        public CharSequence getText() {
            return text;
        }

        public void setText(CharSequence text) {
            this.text = new StringBuilder(text);
        }

        public Object getData(String key) {
            return data == null ? null : data.get(key);
        }

        public void putData(String key, Object val) {
            if (key == null || val == null) {
                return;
            }

            if (data == null) {
                data = new HashMap<>();
            }
            data.put(key, val);
        }

        public void clearData(String key) {
            if (key == null || data == null) {
                return;
            }
            data.remove(key);
        }

        public int length() {
            if (text != null) {
                return gcLength(text);
            }
            if (children == null) {
                return 0;
            }
            int len = 0;
            for (Node c : children) {
                len += c.length();
            }
            return len;
        }

        // Remove spaces and breaks on the left.
        public void lTrim() {
            if (isStyle("BR")) {
                text = null;
                tp = null;
                children = null;
                data = null;
            } else if (isUnstyled()) {
                if (text != null) {
                    text = ltrim(text);
                } else if (children != null && !children.isEmpty()) {
                    children.get(0).lTrim();
                }
            }
        }

        public Drafty toDrafty() {
            MutableDrafty doc = new MutableDrafty();
            appendToDrafty(doc);
            return doc.toDrafty();
        }

        private void appendToDrafty(@NotNull MutableDrafty doc) {
            int start = doc.length();

            if (text != null) {
                doc.append(text);
            } else if (children != null) {
                for (Node c : children) {
                    c.appendToDrafty(doc);
                }
            }

            if (tp != null) {
                int len = doc.length() - start;
                if (data != null && !data.isEmpty()) {
                    int newKey = doc.append(new Entity(tp, data), key);
                    if (attachment) {
                        // Attachment.
                        doc.append(new Style(-1, 0, newKey));
                    } else {
                        doc.append(new Style(start, len, newKey));
                    }
                } else {
                    doc.append(new Style(tp, start, len));
                }
            }
        }

        @NotNull
        private static StringBuilder ltrim(@NotNull StringBuilder str) {
            int len = gcLength(str);
            if (len == 0) {
                return str;
            }
            int start = 0;
            int end = len - 1;
            while (Character.isWhitespace(str.charAt(start)) && start < end) {
                start++;
            }
            if (start > 0) {
                return str.delete(0, start);
            }
            return str;
        }

        @NotNull
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder("{");
            if (tp != null) {
                sb.append("tp: ").append(tp).append("; ");
            }
            if (data != null) {
                sb.append("data: ").append(data).append(" ");
            }
            if (text != null) {
                sb.append("text: '").append(text).append("'");
            } else if (children != null) {
                sb.append("[");
                for (Node c : children) {
                    sb.append(c).append(",");
                }
                sb.append("]");
            }
            sb.append("}");
            return sb.toString();
        }
    }

    // ================
    // Internal classes

    private static class Block {
        String txt;
        List<Style> fmt;

        Block(String txt) {
            this.txt = txt;
        }

        void addStyle(Style s) {
            if (fmt == null) {
                fmt = new ArrayList<>();
            }
            fmt.add(s);
        }

        @NotNull
        @Override
        public String toString() {
            return "{'" + txt + "', " +
                    "fmt: [" + fmt + "]}";
        }
    }

    private static class Span implements Comparable<Span> {
        // Sorted in ascending order.
        private static final String[] VOID_STYLES = new String[]{"BR", "EX", "HD"};

        int start;
        int end;
        int key;
        String text;
        String type;
        Map<String,Object> data;
        List<Span> children;

        Span() {
        }

        Span(String text) {
            this.text = text;
        }

        // Inline style
        Span(String type, int start, int end) {
            this.type = type;
            this.start = start;
            this.end = end;
        }

        // Entity reference
        Span(int start, int end, int index) {
            this.type = null;
            this.start = start;
            this.end = end;
            this.key = index;
        }

        boolean isUnstyled() {
            return type == null || type.isEmpty();
        }
        static boolean isVoid(final String tp) {
            return Arrays.binarySearch(VOID_STYLES, tp) >= 0;
        }

        boolean isVoid() {
            return isVoid(type);
        }

        @Override
        public int compareTo(Span s) {
            return start - s.start;
        }

        @NotNull
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder("{");
            if (type != null) {
                sb.append("type: ").append(type).append(", ");
            }
            sb.append("start: ").append(start).append(", end: ").append(end);
            if (text != null) {
                sb.append(", text: '").append(text).append("'");
            }
            if (data != null) {
                sb.append(", data: ").append(data);
            }
            if (children != null) {
                sb.append(", children: [\n");
                for (Span c : children) {
                    sb.append("\t").append(c).append("\n");
                }
                sb.append("\n]");
            }
            sb.append("}");
            return sb.toString();
        }
    }

    private static class ExtractedEnt {
        int at;
        int len;
        String tp;
        String value;

        Map<String,Object> data;
    }

    private static abstract class EntityProc {
        final String name;
        final Pattern re;

        EntityProc(String name, Pattern patten) {
            this.name = name;
            this.re = patten;
        }

        abstract Map<String,Object> pack(Matcher m);
    }

    private static boolean equalsNullable(@Nullable Object first, @Nullable Object second) {
        if (first == null) {
            return second == null;
        }
        return first.equals(second);
    }

    private static class MutableDrafty {
        //private boolean ta = false;
        private StringBuilder txt = null;
        private List<Style> fmt = null;
        private List<Entity> ent = null;
        private Map<Integer,Integer> keymap = null;

        MutableDrafty() {
        }

        Drafty toDrafty() {
            Drafty doc = txt != null ?
                    Drafty.fromPlainText(txt.toString()) : new Drafty();

            if (fmt != null && !fmt.isEmpty()) {
                doc.fmt = fmt.toArray(new Style[]{});
                if (ent != null && !ent.isEmpty()) {
                    doc.ent = ent.toArray(new Entity[]{});
                }
            }

            return doc;
        }

        int length() {
            return txt != null ? gcLength(txt) : 0;
        }

        void append(CharSequence text) {
            if (txt == null) {
                txt = new StringBuilder();
            }
            txt.append(text);
        }

        void append(Style style) {
            if (fmt == null) {
                fmt = new LinkedList<>();
            }
            fmt.add(style);
        }

        int append(Entity entity, int oldKey) {
            if (ent == null) {
                ent = new LinkedList<>();
                keymap = new HashMap<>();
            }
            Integer key = keymap.get(oldKey);
            if (key == null) {
                ent.add(entity);
                key = ent.size() - 1;
                keymap.put(oldKey, key);
            }
            return key;
        }
    }

    /**
     * Methods related to grapheme clusters.
     */

    // Get array of grapheme cluster sizes in the string.
    private static int[] gcSizes(CharSequence seq) {
        List<Integer> sizes = new ArrayList<>();
        BreakIterator bi = BreakIterator.getCharacterInstance();
        bi.setText(seq);
        int start = bi.first();
        for (int end = bi.next(); end != BreakIterator.DONE; start = end, end = bi.next()) {
            sizes.add(end - start);
        }
        return sizes.stream().mapToInt(i -> i).toArray();
    }

    // Extract subsequences of grapheme clusters between start and end clusters.
    private static CharSequence gcSubSequence(int[] sizes, CharSequence seq, int start, int end) {
        int from = 0;
        for (int i = 0; i < start; i++) {
            from += sizes[i];
        }
        int to = from;
        for (int i = start; i < end; i++) {
            to += sizes[i];
        }
        return seq.subSequence(from, to);
    }

    private static int gcLength(CharSequence seq) {
        // return str.codePointCount(0, str.length());
        return gcCount(seq, 0, seq.length());
    }

    // Convert offset measured in grapheme count to offset in character count.
    private static int gcOffset(CharSequence seq, int graphemeCount) {
        BreakIterator bi = BreakIterator.getCharacterInstance();
        bi.setText(seq);
        bi.first();
        int offset = 0;
        for (int end = bi.next(), count = 0;
             end != BreakIterator.DONE && count < graphemeCount;
             end = bi.next(), count ++) {
            offset = end;
        }
        return offset;
    }

    // Count grapheme clusters in the string between start and end.
    private static int gcCount(CharSequence seq, int start, int end) {
        BreakIterator bi = BreakIterator.getCharacterInstance();
        bi.setText(seq.subSequence(start, end));
        int count = 0;
        for (bi.first(); bi.next() != BreakIterator.DONE;){
            ++ count;
        }
        return count;
    }
}
