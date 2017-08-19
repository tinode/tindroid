package co.tinode.tindroid.media;

import android.support.annotation.NonNull;
import android.text.SpannableString;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Class for representing and handling text with minimal formatting.
 */
public class Drafty {
    // Regular expressions for parsing inline formats.
    // Name of the style, regexp start, regexp end
    private static final String INLINE_STYLE_NAME[] = {"BO", "IT", "ST", "CO" };
    private static final Pattern INLINE_STYLE_RE[] = {
            Pattern.compile("(?<=^|\\W)\\*([^\\s*]+)\\*(?=$|\\W)"),    // bold *bo*
            Pattern.compile("(?<=^|[\\W_])_([^\\s_]+)_(?=$|[\\W_])"),  // italic _it_
            Pattern.compile("(?<=^|\\W)~([^\\s~]+)~(?=$|\\W)"),        // strikethough ~st~
            Pattern.compile("(?<=^|\\W)`([^`]+)`(?=$|\\W)")             // code/monospace `mono`
    };

    private static final String ENTITY_NAME[] = {"LN", "MN", "HT"};
    private static final EntityProc ENTITY_PROC[] = {
            new EntityProc("LN", ) {

                @Override
                Object pack(String val) {
                    return null;
                }

                @Override
                String openDecor(Object data) {
                    return null;
                }

                @Override
                String closeDecor(Object data) {
                    return null;
                }
            },
            new EntityProc("MN", ) {
                @Override
                Object pack(String val) {
                    return null;
                }

                @Override
                String openDecor(Object data) {
                    return null;
                }

                @Override
                String closeDecor(Object data) {
                    return null;
                }
            },
            new EntityProc("HT", ) {
                @Override
                Object pack(String val) {
                    return null;
                }

                @Override
                String openDecor(Object data) {
                    return null;
                }

                @Override
                String closeDecor(Object data) {
                    return null;
                }
            }
    };

    Block[] blocks;
    EntityMap[] refs;

    public Drafty() {
    }

    public Drafty(String content) {
        parse(content);
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
    private Block draftify(List<Span> chunks, int startAt) {
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
                ee.data = ENTITY_PROC[i].pack(ee.value);
            }
        }

        if (extracted.size() == 0) {
            return null;
        }

        return extracted;
    }

    void parse(String content) {
        // Break input into individual lines. Format cannot span multiple lines.
        String lines[] = content.split("\\r?\\n");
        List<Block> blks = new ArrayList<>();

        List<Span> spans = new ArrayList<>();
        for (String line : lines) {
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
        }

        blocks = (Block[]) blks.toArray();
    }

    protected SpannableString toSpannable() {
        return null;
    }

    // Convert Drafty to plain text;
    public String toString() {
        return null;
    }

    public static class Block {
        String txt;
        InlineStyle[] fmt;
        Entity[] ent;

        public Block() {
        }

        public Block(String txt) {
            this.txt = txt;
        }
    }

    public static class InlineStyle {
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

    public static class Entity {
        int at;
        int len;
        int key;
    }

    public static class EntityMap {
        String tp;
        Object data;
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

        abstract Object pack(String val);

        abstract String openDecor(Object data);
        abstract String closeDecor(Object data);
    }
}
