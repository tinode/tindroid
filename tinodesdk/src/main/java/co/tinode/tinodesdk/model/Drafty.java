package co.tinode.tinodesdk.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 Basic parser and formatter for very simple rich text. Mostly targeted at
 mobile use cases similar to Telegram and WhatsApp.

 Supports:
 *abc* -> <b>abc</b>
 _abc_ -> <i>abc</i>
 ~abc~ -> <del>abc</del>
 `abc` -> <tt>abc</tt>

 Nested formatting is supported, e.g. *abc _def_* -> <b>abc <i>def</i></b>

 URLs, @mentions, and #hashtags are extracted.

 JSON data representation is similar to Draft.js raw formatting.

 Text:
     this is *bold*, `code` and _italic_, ~strike~
     combined *bold and _italic_*
     an url: https://www.example.com/abc#fragment and another _www.tinode.co_
     this is a @mention and a #hashtag in a string
     second #hashtag

 Sample JSON representation of the text above:
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
 */

public class Drafty implements Serializable {
    public static final String MIME_TYPE = "text/x-drafty";

    // Regular expressions for parsing inline formats.
    // Name of the style, regexp start, regexp end
    private static final String INLINE_STYLE_NAME[] = {"ST", "EM", "DL", "CO" };
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
                Map<String,String> pack(Matcher m) {
                    Map<String, String> data = new HashMap<>();
                    data.put("url", m.group(1) == null ? "http://" + m.group() : m.group());
                    return data;
                }
            },
            new EntityProc("MN", Pattern.compile("\\B@(\\w\\w+)")) {
                @Override
                Map<String,String> pack(Matcher m) {
                    Map<String, String> data = new HashMap<>();
                    data.put("val", m.group());
                    return data;
                }
            },
            new EntityProc("HT", Pattern.compile("(?<=[\\s,.!]|^)#(\\w\\w+)")) {
                @Override
                Map<String,String> pack(Matcher m) {
                    Map<String, String> data = new HashMap<>();
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
            s.start = matcher.start(0);     // 'hello *world*'
                                            //        ^ group(zero) -> index of the opening markup character
            s.end = matcher.end(1);         // group(one) -> index of the closing markup character
            s.text = matcher.group(1);      // text without of the markup
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
        Block block = new Block("");

        List<Style> ranges = new ArrayList<>();
        for (Span chunk : chunks) {
            if (chunk.text == null) {
                Block drafty = draftify(chunk.children, block.txt.length() + startAt);
                chunk.text = drafty.txt;
                ranges.addAll(drafty.fmt);
            }

            if (chunk.type != null) {
                ranges.add(new Style(chunk.type, block.txt.length() + startAt, chunk.text.length()));
            }

            block.txt += chunk.text;
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
                ee.at = matcher.start(1);
                ee.value = matcher.group(1);
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
            Block b = new Block(line);

            spans.clear();
            // Select styled spans.
            for (int i = 0;i < INLINE_STYLE_NAME.length; i++) {
                spans.addAll(spannify(line, INLINE_STYLE_RE[i], INLINE_STYLE_NAME[i]));
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
                spans.add(new Span(ent.at, ent.len, index));
            }


            if (!spans.isEmpty()) {
                // Sort styled spans in ascending order by .start
                Collections.sort(spans);

                // Rearrange linear list of styled spans into a tree, throw away invalid spans.
                spans = toTree(spans);

                // Parse the entire string into spans, styled or unstyled.
                spans = chunkify(line, 0, line.length(), spans);

                // Convert line into a block.
                b = draftify(spans, 0);
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
                fmt.size() > 0 ? fmt.toArray(new Style[fmt.size()]) : null,
                refs.size() > 0 ? refs.toArray(new Entity[refs.size()]) : null);
    }

    public Style[] getStyles() {
        return fmt;
    }

    public Entity getEntity(Style style) {
        return ent != null && style.key != null? ent[style.key] : null;
    }

    // Convert Drafty to plain text;
    @Override
    public String toString() {
        return txt;
    }

    /**
     * Check if the give Drafty can be represented by plain text.
     *
     * @return true if this Drafty has no markup other thn line breaks.
     */
    public boolean isPlain() {
        return (ent == null && fmt == null);
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
        }

        // Entity reference
        public Style(int at, int len, int key) {
            this.at = at;
            this.len = len;
            this.key = key;
        }

        @Override
        public int compareTo(Style that) {
            if (this.at - that.at == 0) {
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


    }

    public static class Entity implements Serializable {
        public String tp;
        public Map<String,String> data;

        public Entity() {}


        public Entity(String tp, Map<String,String> data) {
            this.tp = tp;
            this.data = data;
        }

        @JsonIgnore
        public String getType() {
            return tp;
        }

        @JsonIgnore
        public Map<String,String> getData() {
            return data;
        }
    }

    // Internal classes

    private static class Block {
        String txt;
        List<Style> fmt;

        public Block() {
        }

        Block(String txt) {
            this.txt = txt;
        }
    }


    private static class Span implements Comparable<Span> {
        int start;
        int end;
        int key;
        String text;
        String type;
        Map<String,String> data;
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
    }

    private static class ExtractedEnt {
        int at;
        int len;
        String tp;
        String value;

        Map<String,String> data;
    }

    private static abstract class EntityProc {
        String name;
        Pattern re;

        EntityProc(String name, Pattern patten) {
            this.name = name;
            this.re = patten;
        }

        abstract Map<String,String> pack(Matcher m);
    }
}
