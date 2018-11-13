package co.tinode.tinodesdk.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.io.Serializable;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


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

public class Drafty implements Serializable {
    public static final String MIME_TYPE = "text/x-drafty";
    public static final String JSON_MIME_TYPE = "application/json";

    private static final String TAG = "Drafty";

    private static final int MAX_FORM_ELEMENTS = 8;

    // Regular expressions for parsing inline formats.
    // Name of the style, regexp start, regexp end
    private static final String INLINE_STYLE_NAME[] = {"ST", "EM", "DL", "CO"};
    private static final Pattern INLINE_STYLE_RE[] = {
            Pattern.compile("(?<=^|\\W)\\*([^\\s*]+)\\*(?=$|\\W)"),    // bold *bo*
            Pattern.compile("(?<=^|[\\W_])_([^\\s_]+)_(?=$|[\\W_])"),  // italic _it_
            Pattern.compile("(?<=^|\\W)~([^\\s~]+)~(?=$|\\W)"),        // strikethough ~st~
            Pattern.compile("(?<=^|\\W)`([^`]+)`(?=$|\\W)")             // code/monospace `mono`
    };

    private static final String ENTITY_NAME[] = {"LN", "MN", "HT"};
    private static final EntityProc ENTITY_PROC[] = {
            new EntityProc("LN",
                    Pattern.compile("(?<=^|\\W)(https?:\\/\\/)?(?:www\\.)?[-a-zA-Z0-9@:%._\\+~#=]{2,256}" +
                            "\\.[a-z]{2,4}\\b(?:[-a-zA-Z0-9@:%_\\+.~#?&\\/=]*)")) {

                @Override
                Map<String,Object> pack(Matcher m) {
                    Map<String, Object> data = new HashMap<>();
                    data.put("url", m.group(1) == null ? "http://" + m.group() : m.group());
                    return data;
                }
            },
            new EntityProc("MN", Pattern.compile("\\B@(\\w\\w+)")) {
                @Override
                Map<String,Object> pack(Matcher m) {
                    Map<String, Object> data = new HashMap<>();
                    data.put("val", m.group());
                    return data;
                }
            },
            new EntityProc("HT", Pattern.compile("(?<=[\\s,.!]|^)#(\\w\\w+)")) {
                @Override
                Map<String,Object> pack(Matcher m) {
                    Map<String, Object> data = new HashMap<>();
                    data.put("val", m.group());
                    return data;
                }
            }
    };

    public String txt;
    public Style[] fmt;
    public Entity[] ent;

    public Drafty() {
    }

    public Drafty(String content) {
        Drafty that = parse(content);

        this.txt = that.txt;
        this.fmt = that.fmt;
        this.ent = that.ent;
    }

    @SuppressWarnings("WeakerAccess")
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
            s.text = matcher.group(1);          // text without of the markup
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

        if (spans == null || spans.size() == 0) {
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

            List<Span> chld = chunkify(line, span.start + 1, span.end - 1, span.children);
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

    private static List<Span> toTree(List<Span> spans) {
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
            s.children = toTree(s.children);
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

        if (ranges.size() > 0) {
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
                ee.len = ee.value.length();
                ee.tp = ENTITY_NAME[i];
                ee.data = ENTITY_PROC[i].pack(matcher);
                extracted.add(ee);
            }
        }

        return extracted;
    }

    public static Drafty parse(String content) {
        // Break input into individual lines. Format cannot span multiple lines.
        String lines[] = content.split("\\r?\\n");
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
                spans = toTree(spans);

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
        if (blks.size() > 0) {
            Block b = blks.get(0);
            if (b.txt != null) {
                text.append(b.txt);
            }
            if (b.fmt != null) {
                fmt.addAll(b.fmt);
            }

            for (int i = 1; i<blks.size(); i++) {
                int offset = text.length() + 1;
                fmt.add(new Style("BR", offset - 1, 1));

                b = blks.get(i);
                text.append(" ").append(b.txt);
                if (b.fmt != null) {
                    for (Style s : b.fmt) {
                        s.at += offset;
                        fmt.add(s);
                    }
                }
            }
        }

        return new Drafty(text.toString(),
                fmt.size() > 0 ? fmt.toArray(new Style[0]) : null,
                refs.size() > 0 ? refs.toArray(new Entity[0]) : null);
    }

    @JsonIgnore
    public Style[] getStyles() {
        return fmt;
    }

    @JsonIgnore
    public Entity[] getEntities() {
        return ent;
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
                if (ref != null) {
                    result.add((String) ref);
                }
            }
        }
        return result.size() > 0 ? result.toArray(new String[]{}) : null;
    }

    @JsonIgnore
    public Entity getEntity(Style style) {
        if (ent != null) {
            try {
                return ent[style.key == null ? 0 : style.key];
            } catch (ArrayIndexOutOfBoundsException ignored) {
            }
        }
        return null;
    }

    // Convert Drafty to plain text;
    @Override
    public String toString() {
        return txt != null ? txt : "";
    }

    // Make sure Drafty is properly initialized for entity insertion.
    private void prepareForEntity(int at, int len) {
        if (fmt == null) {
            fmt = new Style[1];
        } else {
            fmt = Arrays.copyOf(fmt, fmt.length + 1);
        }
        if (ent == null) {
            ent = new Entity[1];
        } else {
            ent = Arrays.copyOf(ent, ent.length + 1);
        }
        fmt[fmt.length - 1] = new Style(at, len, ent.length - 1);
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
     * @return 'this' Drafty object.
     */
    public Drafty insertImage(int at, String mime, byte[] bits, int width, int height, String fname) {
        return insertImage(at, mime, bits, width, height, fname, null, 0);
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
    public Drafty insertImage(int at, String mime, byte[] bits, int width, int height, String fname, URL refurl, long size) {
        if (bits == null && refurl == null) {
            throw new IllegalArgumentException("Either image bits or reference URL must not be null.");
        }

        if (txt == null || txt.length() < at + 1 || at < 0) {
            throw new IndexOutOfBoundsException("Invalid insertion position");
        }

        prepareForEntity(at, 1);

        Map<String,Object> data = new HashMap<>();
        if (mime != null && !mime.equals("")) {
            data.put("mime", mime);
        }
        if (bits != null) {
            data.put("val", bits);
        }
        data.put("width", width);
        data.put("height", height);
        if (fname != null && !fname.equals("")) {
            data.put("name", fname);
        }
        if (refurl != null) {
            data.put("ref", refurl.toString());
        }
        if (size > 0) {
            data.put("size", size);
        }
        ent[ent.length - 1] = new Entity("IM", data);

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
        if (mime != null && !mime.equals("")) {
            data.put("mime", mime);
        }
        if (bits != null) {
            data.put("val", bits);
        }
        if (fname != null && !fname.equals("")) {
            data.put("name", fname);
        }
        if (refurl != null) {
            data.put("ref", refurl);
        }
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
     * @return 'this' Drafty object.
     */
    public Drafty attachJSON(Map<String,Object> json) {
        prepareForEntity(-1, 1);

        Map<String, Object> data = new HashMap<>();
        data.put("mime", JSON_MIME_TYPE);
        data.put("val", json);

        ent[ent.length - 1] = new Entity("EX", data);

        return this;
    }


    /**
     * Insert button into Drafty document.
     * @param at is location where the button is inserted.
     * @param len is the length of the text to be used as button title.
     * @param name is an opaque ID of the button. Client should just return it to the server when the button is clicked.
     * @param actionType is the type of the button, one of 'url' or 'pub'.
     * @param actionValue is the value associated with the action: 'url': URL, 'pub': optional data to add to response.
     * @param refUrl parameter required by URL buttons: url to go to on click.
     *
     * @return 'this' Drafty object.
     */
    protected Drafty insertButton(int at, int len, String name, String actionType, String actionValue, String refUrl) {
        prepareForEntity(at, len);

        if (!"url".equals(actionType) && !"pub".equals(actionType)) {
            throw new IllegalArgumentException("Unknown action type "+actionType);
        }
        if ("url".equals(actionType) && refUrl == null) {
            throw new IllegalArgumentException("URL required for URL buttons");
        }

        final Map<String,Object> data = new HashMap<>();
        data.put("act", actionType);
        if (name != null && !name.equals("")) {
            data.put("name", name);
        }
        if (actionValue != null && !actionValue.equals("")) {
            data.put("val", actionValue);
        }
        if ("url".equals(actionType)) {
            data.put("ref", refUrl);
        }
        ent[ent.length - 1] = new Entity("BN", data);
        return this;
    }

    /**
     * Check if the give Drafty can be represented by plain text.
     *
     * @return true if this Drafty has no markup other thn line breaks.
     */
    @JsonIgnore
    public boolean isPlain() {
        return (ent == null && fmt == null);
    }

    // Inverse of chunkify. Returns a tree of formatted spans.
    private <T> List<T> forEach(String line, int start, int end, List<Span> spans, Formatter<T> formatter) {
        List<T> result = new LinkedList<>();
        if (spans == null) {
            result.add(formatter.apply(null, null, line.substring(start, end)));
            return result;
        }

        // Process ranges calling formatter for each range.
        ListIterator<Span> iter = spans.listIterator();
        while (iter.hasNext()) {
            Span span = iter.next();

            if (span.start < 0 && span.type.equals("EX")) {
                // This is different from JS SDK. JS ignores these spans here.
                // JS uses Drafty.attachments() to get attachments.
                result.add(formatter.apply(span.type, span.data, null));
                continue;
            }

            // Add un-styled range before the styled span starts.
            if (start < span.start) {
                result.add(formatter.apply(null, null, line.substring(start, span.start)));
                start = span.start;
            }

            // Get all spans which are within the current span.
            List<Span> subspans = new LinkedList<>();
            while (iter.hasNext()) {
                Span inner = iter.next();
                if (inner.start < span.end) {
                    subspans.add(inner);
                } else {
                    // Move back.
                    iter.previous();
                    break;
                }
            }

            if (subspans.size() == 0) {
                subspans = null;
            }

            if (span.type.equals("BN")) {
                // Make button content unstyled.
                span.data = span.data != null ? span.data : new HashMap<String, Object>();
                String title = line.substring(span.start, span.end);
                span.data.put("title", title);
                result.add(formatter.apply(span.type, span.data, title));
            } else {
                result.add(formatter.apply(span.type, span.data,
                        forEach(line, start, span.end, subspans, formatter)));
            }

            start = span.end;
        }

        // Add the last unformatted range.
        if (start < end) {
            result.add(formatter.apply(null, null, line.substring(start, end)));
        }

        return result;
    }

    /**
     * Format converts Drafty object into a collection of formatted nodes.
     * Each node contains either a formatted element or a collection of
     * formatted elements.
     *
     * @param formatter is an interface with an `apply` method. It's iteratively
     *                  applied to every node in the tree.
     * @returns a tree of components.
     */
    public <T> T format(Formatter<T> formatter) {
        if (txt == null) {
            txt = "";
        }

        // Handle special case when all values in fmt are 0 and fmt is therefore was
        // skipped.
        if (fmt == null || fmt.length == 0) {
            if (ent != null && ent.length == 1) {
                fmt = new Style[1];
                fmt[0] = new Style(0, 0, 0);
            } else {
                return formatter.apply(null, null, txt);
            }
        }

        List<Span> spans = new ArrayList<>();
        for (Style aFmt : fmt) {
            if (aFmt.len < 0) {
                aFmt.len = 0;
            }
            if (aFmt.at < -1) {
                aFmt.at = -1;
            }
            if (aFmt.tp == null || "".equals(aFmt.tp)) {
                spans.add(new Span(aFmt.at, aFmt.at + aFmt.len,
                        aFmt.key != null ? aFmt.key : 0));
            } else {
                spans.add(new Span(aFmt.tp, aFmt.at, aFmt.at + aFmt.len));
            }
        }

        // Sort spans first by start index (asc) then by length (desc).
        Collections.sort(spans, new Comparator<Span>() {
            @Override
            public int compare(Span a, Span b) {
                if (a.start - b.start == 0) {
                    return b.end - a.end; // longer one comes first (<0)
                }
                return a.start - b.start;
            }
        });

        for (Span span : spans) {
            if (span.type == null || span.type.equals("")) {
                if (span.key >= 0 && span.key < ent.length && ent[span.key] != null) {
                    span.type = ent[span.key].tp;
                    span.data = ent[span.key].data;
                } else {
                    span.type = "HD";
                }
            }
        }

        return formatter.apply(null, null, forEach(txt, 0, txt.length(), spans, formatter));
    }

    private String toPlainText() {
        return "{txt: '" + txt + "'," +
                "fmt: " + Arrays.toString(fmt) + "," +
                "ent: " + Arrays.toString(ent) + "}";
    }

    public static class Style implements Serializable, Comparable<Style> {
        public int at;
        public int len;
        public String tp;
        public Integer key;

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

        @Override
        public int compareTo(Style that) {
            if (this.at == that.at) {
                return that.len - this.len; // longer one comes first (<0)
            }
            return this.at - that.at;
        }

        @JsonIgnore
        public String getType() {
            return tp;
        }

        @JsonIgnore
        public int getOffset() {
            return at;
        }

        @JsonIgnore
        public int length() {
            return len;
        }


        @Override
        public String toString() {
            return "{tp: '" + tp + "', at: " + at + ", len: " + len + ", key: " + key + "}";
        }
    }

    public static class Entity implements Serializable {
        public String tp;
        public Map<String,Object> data;

        public Entity() {}

        public Entity(String tp, Map<String,Object> data) {
            this.tp = tp;
            this.data = data;
        }

        @JsonIgnore
        public String getType() {
            return tp;
        }

        public Map<String,Object> getData() {
            return data;
        }

        @Override
        public String toString() {
            return "{tp: '" + tp + "', data: " + (data != null ? data.toString() : "null") + "}";
        }
    }

    public interface Formatter<T> {
        T apply(String tp, Map<String,Object> attr, Object content);
    }

    // ================
    // Internal classes

    private static class Block {
        String txt;
        List<Style> fmt;

        public Block() {
        }

        Block(String txt) {
            this.txt = txt;
        }

        void addStyle(Style s) {
            if (fmt == null) {
                fmt = new ArrayList<>();
            }
            fmt.add(s);
        }
    }

    private static class Span implements Comparable<Span> {
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

        @Override
        public int compareTo(Span s) {
            return start - s.start;
        }

        @Override
        public String toString() {
            return "{" + "start=" + start + "," +
                    "end=" + end + "," +
                    "type=" + type + "," +
                    "data=" + (data != null ? data.toString() : "null") +
                    "}";
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
        String name;
        Pattern re;

        EntityProc(String name, Pattern patten) {
            this.name = name;
            this.re = patten;
        }

        abstract Map<String,Object> pack(Matcher m);
    }
}
