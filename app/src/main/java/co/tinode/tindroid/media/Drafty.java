package co.tinode.tindroid.media;

import android.graphics.Typeface;
import android.support.annotation.NonNull;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.CharacterStyle;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.text.style.URLSpan;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
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
     this is *bold*, `code`, _italic_ and ~deleted~.
     nested styles: *just bold _bold-italic_*
     couple of urls: http://www.example.com/path?a=b%20c#fragment, and bolded *www.tinode.co*
     this is a @mention and a #hashtag in a string
     the _#hashtag_ used again

 Sample JSON representation of the text above:
 {
    "blocks":[
        {
            "txt":"this is bold, code, italic and deleted.",
            "fmt":[
                {
                    "at":8,
                    "len":4,
                    "tp":"ST"
                },
                {
                    "at":14,
                    "len":4,
                    "tp":"CO"
                },
                {
                    "at":20,
                    "len":6,
                    "tp":"EM"
                },
                {
                    "at":31,
                    "len":7,
                    "tp":"DL"
                }
            ]
        },
        {
            "txt":"nested styles: just bold bold-italic",
            "fmt":[
                {
                    "at":25,
                    "len":11,
                    "tp":"EM"
                },
                {
                    "at":15,
                    "len":21,
                    "tp":"ST"
                }
            ]
        },
        {
            "txt":"couple of urls: http://www.example.com/path?a=b%20c#fragment, and bolded www.tinode.co",
            "fmt":[
                {
                    "at":73,
                    "len":13,
                    "tp":"ST"
                }
            ],
            "ent":[
                {
                    "at":16,
                    "len":44,
                    "key":0
                },
                {
                    "at":73,
                    "len":13,
                    "key":1
                }
            ]
        },
        {
            "txt":"this is a @mention and a #hashtag in a string",
            "ent":[
                {
                    "at":10,
                    "len":8,
                    "key":2
                },
                {
                    "at":25,
                    "len":8,
                    "key":3
                }
            ]
        },
        {
            "txt":"the #hashtag used again",
            "fmt":[
                {
                    "at":4,
                    "len":8,
                    "tp":"EM"
                }
            ],
            "ent":[
                {
                    "at":4,
                    "len":8,
                    "key":3
                }
            ]
        }
    ],
    "refs":[
        {
            "tp":"LN",
            "data":{
                "url":"http://www.example.com/path?a=b%20c#fragment"
            }
        },
        {
            "tp":"LN",
            "data":{
                "url":"http://www.tinode.co"
            }
        },
        {
            "tp":"MN",
            "data":{
                "val":"mention"
            }
        },
        {
            "tp":"HT",
            "data":{
               "val":"hashtag"
            }
        }
    ]
 }
 */

public class Drafty implements Serializable {
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
                    "(?<=^|\\W)(https?:\\/\\/)?(?:www\\.)?[-a-zA-Z0-9@:%._\\+~#=]{2,256}\\.[a-z]{2,4}\\b(?:[-a-zA-Z0-9@:%_\\+.~#?&//=]*)") {

                @Override
                Object pack(Matcher m) {
                    Map<String, String> data = new HashMap<>();
                    data.put("url", m.group(1) == null ? "http://" + m.group() : m.group());
                    return data;
                }

                @Override
                Object decorate(Object data) {
                    String url = ((Map<String, String>) data).get("url");
                    if (url != null) {
                        return new URLSpan(url);
                    }
                    return null;
                }
            },
            new EntityProc("MN", "\\B@(\\w\\w+)") {
                @Override
                Object pack(Matcher m) {
                    Map<String, String> data = new HashMap<>();
                    data.put("val", m.group());
                    return data;
                }

                @Override
                Object decorate(Object data) {
                    // Don't know how to handle mentions in the UI
                    // String val = ((Map<String, String>) data).get("val");
                    return null;
                }
            },
            new EntityProc("HT", "(?<=[\\s,.!]|^)#(\\w\\w+)") {
                @Override
                Object pack(Matcher m) {
                    Map<String, String> data = new HashMap<>();
                    data.put("val", m.group());
                    return data;
                }

                @Override
                Object decorate(Object data) {
                    // Don't know how to handle hashtags in the UI
                    // String val = ((Map<String, String>) data).get("val");
                    return null;
                }
            }
    };

    public Block[] blocks;
    public EntityRef[] refs;

    public Drafty() {
    }

    public Drafty(String content) {
        Drafty that = parse(content);

        this.blocks = that.blocks;
        this.refs = that.refs;
    }

    protected Drafty(Block[] blocks, EntityRef[] refs) {
        this.blocks = blocks;
        this.refs = refs;
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

        List<InlineStyle> ranges = new ArrayList<>();
        for (Span chunk : chunks) {
            if (chunk.text == null) {
                Block drafty = draftify(chunk.children, block.txt.length() + startAt);
                chunk.text = drafty.txt;
                ranges.addAll(Arrays.asList(drafty.fmt));
            }

            if (chunk.type != null) {
                ranges.add(new InlineStyle(block.txt.length() + startAt, chunk.text.length(), chunk.type));
            }

            block.txt += chunk.text;
        }

        if (ranges.size() > 0) {
            block.fmt = new InlineStyle[ranges.size()];
            block.fmt = ranges.toArray(block.fmt);
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
            }
        }

        if (extracted.size() == 0) {
            return null;
        }

        return extracted;
    }

    public static Drafty parse(String content) {
        // Break input into individual lines. Format cannot span multiple lines.
        String lines[] = content.split("\\r?\\n");
        List<Block> blks = new ArrayList<>();
        List<EntityRef> refs = new ArrayList<>();

        List<Span> spans = new ArrayList<>();
        List<Entity> ent_ranges = new ArrayList<>();
        Map<String, Integer> ent_map = new HashMap<>();
        List<ExtractedEnt> entities;
        for (String line : lines) {
            spans.clear();
            // Select styled spans.
            for (int i = 0;i < INLINE_STYLE_NAME.length; i++) {
                spans.addAll(spannify(line, INLINE_STYLE_RE[i], INLINE_STYLE_NAME[i]));
            }

            Block b = new Block(null);
            if (spans.isEmpty()) {
                b.txt = line;
            } else {
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

            // Extract entities from the string already cleared of markup.
            entities = extractEntities(b.txt);
            if (entities != null) {
                ent_ranges.clear();
                for (ExtractedEnt ent : entities) {
                    // Check if the entity has been indexed already
                    Integer index = ent_map.get(ent.value);
                    if (index == null) {
                        index = refs.size();
                        ent_map.put(ent.value, index);
                        refs.add(new EntityRef(ent.tp, ent.data));
                    }
                    ent_ranges.add(new Entity(ent.at, ent.len, index));
                }
                b.ent = (Entity[]) ent_ranges.toArray();
            }
        }

        return new Drafty((Block[]) blks.toArray(), (EntityRef[]) refs.toArray());
    }

    private Spannable applySpans(Block block) {
        SpannableString line = new SpannableString(block.txt);

        if (block.fmt != null) {
            for (InlineStyle style : block.fmt) {
                CharacterStyle span;
                switch (style.tp) {
                    case "ST": span = new StyleSpan(Typeface.BOLD); break;
                    case "EM": span = new StyleSpan(Typeface.ITALIC); break;
                    case "DL": span = new StrikethroughSpan(); break;
                    case "CO": span = new TypefaceSpan("monospace"); break;
                    default: span = null;
                }

                if (span != null) {
                    line.setSpan(span, style.at, style.at + style.len, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }
        }

        if (block.ent != null) {
            for (Entity ent : block.ent) {
                EntityRef ref = refs[ent.key];
                CharacterStyle span;
                switch (ref.tp) {
                    case "LN": span = (CharacterStyle) ENTITY_PROC[0].decorate(ref.data); break;
                    case "MN": span = (CharacterStyle) ENTITY_PROC[1].decorate(ref.data); break;
                    case "HT": span = (CharacterStyle) ENTITY_PROC[2].decorate(ref.data); break;
                    default: span = null;
                }

                if (span != null) {
                    line.setSpan(span, ent.at, ent.at + ent.len, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }
        }

        return line;
    }

    public Spanned toSpanned() {
        SpannableStringBuilder text = new SpannableStringBuilder(applySpans(blocks[0]));

        for (int i = 1; i < blocks.length; i++) {
            text.append("\n").append(applySpans(blocks[i]));
        }

        return text;
    }

    // Convert Drafty to plain text;
    @Override
    public String toString() {
        StringBuilder plainText = new StringBuilder(blocks[0].txt);

        for (int i = 1; i < blocks.length; i++) {
            plainText.append("\n").append(blocks[i].txt);
        }

        return plainText.toString();
    }

    /**
     * Check if the give Drafty can be represented by plain text.
     *
     * @return true if this Drafty has no markup other thn line breaks.
     */
    public boolean isPlain() {
        if (refs != null) {
            return false;
        }

        for (Block b : blocks) {
            if (b.fmt != null) {
                return false;
            }
        }

        return true;
    }

    public static class Block  implements Serializable {
        String txt;
        InlineStyle[] fmt;
        Entity[] ent;

        public Block() {
        }

        public Block(String txt) {
            this.txt = txt;
        }
    }

    public static class InlineStyle  implements Serializable {
        int at;
        int len;
        String tp;

        public InlineStyle() {}

        public InlineStyle(int at, int len, String tp) {
            this.at = at;
            this.len = len;
            this.tp = tp;
        }
    }

    public static class Entity  implements Serializable {
        int at;
        int len;
        int key;

        public Entity() {}

        public Entity(int at, int len, int key) {
            this.at = at;
            this.len = len;
            this.key = key;
        }
    }

    public static class EntityRef  implements Serializable {
        String tp;
        Object data;

        public EntityRef() {}

        public EntityRef(String tp, Object data) {
            this.tp = tp;
            this.data = data;
        }
    }

    private static class Span implements Comparable<Span> {
        int start;
        int end;
        String text;
        String type;
        List<Span> children;

        public Span() {
        }

        public Span(String text) {
            this.text = text;
        }

        @Override
        public int compareTo(@NonNull Span s) {
            return start - s.start;
        }
    }

    private static class ExtractedEnt {
        int at;
        int len;
        String tp;
        String value;

        Object data;
    }

    private static abstract class EntityProc {
        String name;
        Pattern re;

        EntityProc(String name, String patten) {
            this.name = name;
            this.re = Pattern.compile(patten);
        }

        abstract Object pack(Matcher m);

        abstract Object decorate(Object data);
    }
}
