package co.tinode.tinodesdk.model;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.HashMap;

public class DraftyTest {
    @Test
    public void testParse() {
        // String 1
        String source = "this is *bold*, `code` and _italic_, ~strike~";
        Drafty actual = Drafty.parse(source);
        Drafty expected = new Drafty();
        expected.txt = "this is bold, code and italic, strike";
        expected.fmt = new Drafty.Style[]{
                new Drafty.Style("ST", 8, 4),
                new Drafty.Style("CO", 14, 4),
                new Drafty.Style("EM", 23, 6),
                new Drafty.Style("DL", 31, 6)
        };
        assertEquals("String 1 has failed", expected, actual);

        // String 2
        source = "combined *bold and _italic_*";
        actual = Drafty.parse(source);
        expected = new Drafty();
        expected.txt = "combined bold and italic";
        expected.fmt = new Drafty.Style[]{
                new Drafty.Style("EM", 18, 6),
                new Drafty.Style("ST", 9, 15)
        };
        assertEquals("String 2 has failed", expected, actual);

        // String 3
        source = "an url: https://www.example.com/abc#fragment and another _www.tinode.co_";
        actual = Drafty.parse(source);
        expected = new Drafty();
        expected.txt = "an url: https://www.example.com/abc#fragment and another www.tinode.co";
        expected.fmt = new Drafty.Style[]{
                new Drafty.Style("EM", 57, 13),
                new Drafty.Style(8, 36, 0),
                new Drafty.Style(57, 13, 1)
        };
        HashMap<String, Object> entData0 = new HashMap<>();
        entData0.put("url", "https://www.example.com/abc#fragment");
        HashMap<String, Object> entData1 = new HashMap<>();
        entData1.put("url", "http://www.tinode.co");
        expected.ent = new Drafty.Entity[]{
                new Drafty.Entity("LN", entData0),
                new Drafty.Entity("LN", entData1)
        };
        assertEquals("String 3 has failed", expected, actual);

        // String 4
        source = "this is a @mention and a #hashtag in a string";
        actual = Drafty.parse(source);
        expected = new Drafty();
        expected.txt = "this is a @mention and a #hashtag in a string";
        expected.fmt = new Drafty.Style[]{
                new Drafty.Style(25, 8, 0),
        };
        entData0 = new HashMap<>();
        entData0.put("val", "#hashtag");
        expected.ent = new Drafty.Entity[]{
                new Drafty.Entity("HT", entData0),
        };
        assertEquals("String 4 has failed", expected, actual);

        // String 5
        source = "second #юникод";
        actual = Drafty.parse(source);
        expected = new Drafty();
        expected.txt = "second #юникод";
        expected.fmt = new Drafty.Style[]{
                new Drafty.Style(7, 7, 0),
        };
        entData0 = new HashMap<>();
        entData0.put("val", "#юникод");
        expected.ent = new Drafty.Entity[]{
                new Drafty.Entity("HT", entData0),
        };
        assertEquals("String 5 has failed", expected.toPlainText(), actual.toPlainText());
    }

    @Test
    public void testPreview() {
        // Preview 1
        Drafty src = new Drafty();
        src.fmt = new Drafty.Style[]{
                new Drafty.Style(-1, 0, 0),
        };
        HashMap<String, Object> entData0 = new HashMap<>();
        entData0.put("mime", "image/jpeg");
        entData0.put("name", "hello.jpg");
        entData0.put("val", "<38992, bytes: ...>");
        entData0.put("width", 100);
        entData0.put("height", 80);
        src.ent = new Drafty.Entity[]{
                new Drafty.Entity("EX", entData0),
        };
        Drafty actual = src.preview(15);
        Drafty expected = new Drafty();
        expected.fmt = new Drafty.Style[]{
                new Drafty.Style(-1, 0, 0),
        };
        entData0 = new HashMap<>();
        entData0.put("mime", "image/jpeg");
        entData0.put("name", "hello.jpg");
        entData0.put("width", 100);
        entData0.put("height", 80);
        expected.ent = new Drafty.Entity[]{
                new Drafty.Entity("EX", entData0),
        };
        assertEquals("Preview 1 has failed", expected, actual);

        // Preview 2
        src = new Drafty();
        src.txt = "https://api.tinode.co/";
        src.fmt = new Drafty.Style[]{
                new Drafty.Style(0, 22, 0),
        };
        entData0 = new HashMap<>();
        entData0.put("url", "https://www.youtube.com/watch?v=dQw4w9WgXcQ");
        src.ent = new Drafty.Entity[]{
                new Drafty.Entity("LN", entData0),
        };
        actual = src.preview(15);
        expected = new Drafty();
        expected.txt = "https://api.tin";
        expected.fmt = new Drafty.Style[]{
                new Drafty.Style(0, 15, 0),
        };
        expected.ent = new Drafty.Entity[]{
                new Drafty.Entity("LN", null),
        };
        assertEquals("Preview 2 has failed", expected, actual);

        // Preview 3 (inline image)
        src = new Drafty();
        src.txt = " ";
        src.fmt = new Drafty.Style[]{
                new Drafty.Style(0, 1, 0),
        };
        entData0 = new HashMap<>();
        entData0.put("height", 213);
        entData0.put("width", 638);
        entData0.put("name", "roses.jpg");
        entData0.put("val", "<38992, bytes: ...>");
        entData0.put("mime", "image/jpeg");
        src.ent = new Drafty.Entity[]{
                new Drafty.Entity("IM", entData0),
        };
        actual = src.preview(15);
        expected = new Drafty();
        expected.txt = " ";
        expected.fmt = new Drafty.Style[]{
                new Drafty.Style(0, 1, 0),
        };
        entData0 = new HashMap<>();
        entData0.put("height", 213);
        entData0.put("width", 638);
        entData0.put("name", "roses.jpg");
        entData0.put("mime", "image/jpeg");
        expected.ent = new Drafty.Entity[]{
                new Drafty.Entity("IM", entData0),
        };
        assertEquals("Preview 3 has failed", expected, actual);

        // Preview 4 (multiple formatting)
        src = new Drafty();
        src.txt = "This text is formatted and deleted too";
        src.fmt = new Drafty.Style[]{
                new Drafty.Style("ST", 5, 4),
                new Drafty.Style("EM", 13, 9),
                new Drafty.Style("ST", 35, 3),
                new Drafty.Style("DL", 27, 11),
        };

        actual = src.preview(15);
        expected = new Drafty();
        expected.txt = "This text is fo";
        expected.fmt = new Drafty.Style[]{
                new Drafty.Style("ST", 5, 4),
                new Drafty.Style("EM", 13, 2),
        };
        assertEquals("Preview 4 has failed", expected, actual);

        // Preview 5 (multibyte unicode)
        src = new Drafty();
        src.txt = "мультибайтовый юникод";
        src.fmt = new Drafty.Style[]{
                new Drafty.Style("ST", 0, 14),
                new Drafty.Style("EM", 15, 6),
        };
        actual = src.preview(15);
        expected = new Drafty();
        expected.txt = "мультибайтовый ";
        expected.fmt = new Drafty.Style[]{
                new Drafty.Style("ST", 0, 14),
        };
        assertEquals("Preview 5 has failed", expected, actual);
    }
}
