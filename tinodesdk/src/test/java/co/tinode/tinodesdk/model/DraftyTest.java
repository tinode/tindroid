package co.tinode.tinodesdk.model;

import static org.junit.Assert.*;

import org.junit.Test;

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
        expected.ent = new Drafty.Entity[]{
                new Drafty.Entity("LN")
                        .addData("url", "https://www.example.com/abc#fragment"),
                new Drafty.Entity("LN")
                        .addData("url", "http://www.tinode.co")
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
        expected.ent = new Drafty.Entity[]{
                new Drafty.Entity("HT")
                        .addData("val", "#hashtag"),
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
        expected.ent = new Drafty.Entity[]{
                new Drafty.Entity("HT")
                        .addData("val", "#юникод"),
        };
        assertEquals("String 5 has failed", expected.toPlainText(), actual.toPlainText());
    }

    @Test
    public void testPreview() {
        // ------- Preview 1
        Drafty src = new Drafty("This is a plain text string.");
        Drafty actual = src.preview(15);
        Drafty expected = new Drafty("This is a plain");
        assertEquals("Preview 1 has failed", expected, actual);

        // ------- Preview 2
        src = new Drafty();
        src.fmt = new Drafty.Style[]{
                new Drafty.Style(-1, 0, 0),
        };
        src.ent = new Drafty.Entity[]{
                new Drafty.Entity("EX")
                        .addData("mime", "image/jpeg")
                        .addData("name", "hello.jpg")
                        .addData("val", "<38992, bytes: ...>")
                        .addData("width", 100)
                        .addData("height", 80),
        };
        actual = src.preview(15);
        expected = new Drafty();
        expected.fmt = new Drafty.Style[]{
                new Drafty.Style(-1, 0, 0),
        };
        expected.ent = new Drafty.Entity[]{
                new Drafty.Entity("EX")
                        .addData("mime", "image/jpeg")
                        .addData("name", "hello.jpg")
                        .addData("width", 100)
                        .addData("height", 80),
        };
        assertEquals("Preview 2 has failed", expected, actual);

        // ------- Preview 3
        src = new Drafty("https://api.tinode.co/");
        src.fmt = new Drafty.Style[]{
                new Drafty.Style(0, 22, 0),
        };
        src.ent = new Drafty.Entity[]{
                new Drafty.Entity("LN").addData("url", "https://www.youtube.com/watch?v=dQw4w9WgXcQ"),
        };
        actual = src.preview(15);
        expected = new Drafty("https://api.tin");
        expected.fmt = new Drafty.Style[]{
                new Drafty.Style(0, 15, 0),
        };
        expected.ent = new Drafty.Entity[]{
                new Drafty.Entity("LN").addData("url", "https://www.youtube.com/watch?v=dQw4w9WgXcQ"),
        };
        assertEquals("Preview 3 has failed", expected, actual);

        // ------- Preview 4 (two references to the same entity).
        src = new Drafty("Url one, two");
        src.fmt = new Drafty.Style[]{
                new Drafty.Style(9, 3, 0),
                new Drafty.Style(4, 3, 0),
        };
        src.ent = new Drafty.Entity[]{
                new Drafty.Entity("LN").addData("url", "http://tinode.co"),
        };
        actual = src.preview(15);
        expected = new Drafty("Url one, two");
        expected.fmt = new Drafty.Style[]{
                new Drafty.Style(9, 3, 0),
                new Drafty.Style(4, 3, 0),
        };
        expected.ent = new Drafty.Entity[]{
                new Drafty.Entity("LN").addData("url", "http://tinode.co"),
        };
        assertEquals("Preview 4 has failed", expected, actual);

        // ------- Preview 5 (two different entities).
        src = new Drafty("Url one, two");
        src.fmt = new Drafty.Style[] {
                new Drafty.Style(4, 3, 0),
                new Drafty.Style(9, 3, 1),
        };
        src.ent = new Drafty.Entity[]{
                new Drafty.Entity("LN").addData("url", "http://tinode.co"),
                new Drafty.Entity("LN").addData("url", "http://example.com"),
        };
        actual = src.preview(15);
        expected = new Drafty("Url one, two");
        expected.fmt = new Drafty.Style[]{
                new Drafty.Style(4, 3, 0),
                new Drafty.Style(9, 3, 1),
        };
        expected.ent = new Drafty.Entity[]{
                new Drafty.Entity("LN").addData("url", "http://tinode.co"),
                new Drafty.Entity("LN").addData("url", "http://example.com"),
        };
        assertEquals("Preview 5 has failed", expected.toPlainText(), actual.toPlainText());

        // ------- Preview 6 (inline image)
        src = new Drafty(" ");
        src.fmt = new Drafty.Style[]{
                new Drafty.Style(0, 1, 0),
        };
        src.ent = new Drafty.Entity[]{
                new Drafty.Entity("IM")
                        .addData("height", 213)
                        .addData("width", 638)
                        .addData("name", "roses.jpg")
                        .addData("val", "<38992, bytes: ...>")
                        .addData("mime", "image/jpeg"),
        };
        actual = src.preview(15);
        expected = new Drafty(" ");
        expected.fmt = new Drafty.Style[]{
                new Drafty.Style(0, 1, 0),
        };
        expected.ent = new Drafty.Entity[]{
                new Drafty.Entity("IM")
                        .addData("height", 213)
                        .addData("width", 638)
                        .addData("name", "roses.jpg")
                        .addData("mime", "image/jpeg"),
        };
        assertEquals("Preview 3 has failed", expected, actual);

        // ------- Preview 7 (staggered formats)
        src = new Drafty("This text has staggered formats");
        src.fmt = new Drafty.Style[]{
                new Drafty.Style("EM", 5, 8),
                new Drafty.Style("ST", 10, 13),
        };
        actual = src.preview(15);
        expected = new Drafty("This text has s");
        expected.fmt = new Drafty.Style[]{
                new Drafty.Style("EM", 5, 8),
        };

        // ------- Preview 8 (multiple formatting)
        src = new Drafty("This text is formatted and deleted too");
        src.fmt = new Drafty.Style[]{
                new Drafty.Style("ST", 5, 4),
                new Drafty.Style("EM", 13, 9),
                new Drafty.Style("ST", 35, 3),
                new Drafty.Style("DL", 27, 11),
        };

        actual = src.preview(15);
        expected = new Drafty("This text is fo");
        expected.fmt = new Drafty.Style[]{
                new Drafty.Style("ST", 5, 4),
                new Drafty.Style("EM", 13, 2),
        };
        assertEquals("Preview 8 has failed", expected, actual);

        //  -------  Preview 9 (multibyte unicode)
        src = new Drafty("мультибайтовый юникод");
        src.fmt = new Drafty.Style[]{
                new Drafty.Style("ST", 0, 14),
                new Drafty.Style("EM", 15, 6),
        };
        actual = src.preview(15);
        expected = new Drafty("мультибайтовый ");
        expected.fmt = new Drafty.Style[]{
                new Drafty.Style("ST", 0, 14),
        };
        assertEquals("Preview 9 has failed", expected, actual);

        //  -------  Preview 10 (quoted reply)
        src = new Drafty("Alice Johnson    This is a test");
        src.fmt = new Drafty.Style[]{
                new Drafty.Style("BR", 13,1),
                new Drafty.Style(15,1, 0),
                new Drafty.Style(0, 13, 1),
                new Drafty.Style("QQ", 0, 16),
                new Drafty.Style("BR", 16,1),
        };
        src.ent = new Drafty.Entity[]{
                new Drafty.Entity("IM")
                        .addData("mime","image/jpeg")
                        .addData("val", "<1292, bytes: /9j/4AAQSkZJ...rehH5o6D/9k=>")
                        .addData("width", 25)
                        .addData("height", 14)
                        .addData("size", 968),
                new Drafty.Entity("MN")
                        .addData("val", "usr12345678")
        };
        actual = src.preview(15);
        expected = new Drafty("This is a test");
        assertEquals("Preview 10 has failed", expected, actual);
    }
}
