package co.tinode.tinodesdk.model;

import static org.junit.Assert.*;

import org.junit.Test;

public class DraftyTest {
    @Test
    public void testParse() {
        // Basic formatting  1
        Drafty actual = Drafty.parse("this is *bold*, `code` and _italic_, ~strike~");
        Drafty expected = new Drafty("this is bold, code and italic, strike");
        expected.fmt = new Drafty.Style[]{
                new Drafty.Style("ST", 8, 4),
                new Drafty.Style("CO", 14, 4),
                new Drafty.Style("EM", 23, 6),
                new Drafty.Style("DL", 31, 6)
        };
        assertEquals("String 1 has failed", expected, actual);

        // Basic formatting over Unicode string 2.
        actual = Drafty.parse("Это *жЫрный*, `код` и _наклонный_, ~зачеркнутый~");
        expected = new Drafty("Это жЫрный, код и наклонный, зачеркнутый");
        expected.fmt = new Drafty.Style[]{
                new Drafty.Style("ST", 4, 6),
                new Drafty.Style("CO", 12, 3),
                new Drafty.Style("EM", 18, 9),
                new Drafty.Style("DL", 29, 11),
        };
        assertEquals("String 2 has failed", expected, actual);

        // String 3
        actual = Drafty.parse("combined *bold and _italic_*");
        expected = new Drafty("combined bold and italic");
        expected.fmt = new Drafty.Style[]{
                new Drafty.Style("EM", 18, 6),
                new Drafty.Style("ST", 9, 15)
        };
        assertEquals("String 3 has failed", expected, actual);

        // String 4
        actual = Drafty.parse("an url: https://www.example.com/abc#fragment and another _www.tinode.co_");
        expected = new Drafty("an url: https://www.example.com/abc#fragment and another www.tinode.co");
        expected.fmt = new Drafty.Style[]{
                new Drafty.Style("EM", 57, 13),
                new Drafty.Style(8, 36, 0),
                new Drafty.Style(57, 13, 1)
        };
        expected.ent = new Drafty.Entity[]{
                new Drafty.Entity("LN")
                        .putData("url", "https://www.example.com/abc#fragment"),
                new Drafty.Entity("LN")
                        .putData("url", "http://www.tinode.co")
        };
        assertEquals("String 4 has failed", expected, actual);

        // String 5
        actual = Drafty.parse("this is a @mention and a #hashtag in a string");
        expected = new Drafty("this is a @mention and a #hashtag in a string");
        expected.fmt = new Drafty.Style[]{
                new Drafty.Style(10, 8, 0),
                new Drafty.Style(25, 8, 1),
        };
        expected.ent = new Drafty.Entity[]{
                new Drafty.Entity("MN").putData("val", "@mention"),
                new Drafty.Entity("HT").putData("val", "#hashtag"),
        };
        assertEquals("String 5 has failed", expected.toPlainText(), actual.toPlainText());

        // String 6
        actual = Drafty.parse("second #юникод");
        expected = new Drafty("second #юникод");
        expected.fmt = new Drafty.Style[]{
                new Drafty.Style(7, 7, 0),
        };
        expected.ent = new Drafty.Entity[]{
                new Drafty.Entity("HT").putData("val", "#юникод"),
        };
        assertEquals("String 6 has failed", expected.toPlainText(), actual.toPlainText());
    }

    @Test
    public void testShorten() {
        final int limit = 15;

        // ------- Shorten 1
        Drafty src = new Drafty("This is a plain text string.");
        Drafty actual = src.shorten(limit, true);
        Drafty expected = new Drafty("This is a plai…");
        assertEquals("Shorten 1 has failed", expected, actual);

        // ------- Shorten 2
        src = new Drafty();
        src.fmt = new Drafty.Style[]{
                new Drafty.Style(-1, 0, 0),
        };
        src.ent = new Drafty.Entity[]{
                new Drafty.Entity("EX")
                        .putData("mime", "image/jpeg")
                        .putData("name", "hello.jpg")
                        .putData("val", "<38992, 123456789012345678901234567890123456789012345678901234567890 bytes: ...>")
                        .putData("width", 100)
                        .putData("height", 80),
        };
        actual = src.shorten(limit, true);
        expected = new Drafty();
        expected.fmt = new Drafty.Style[]{
                new Drafty.Style(-1, 0, 0),
        };
        expected.ent = new Drafty.Entity[]{
                new Drafty.Entity("EX")
                        .putData("mime", "image/jpeg")
                        .putData("name", "hello.jpg")
                        .putData("width", 100)
                        .putData("height", 80),
        };
        assertEquals("Shorten 2 has failed", expected, actual);

        // ------- Shorten 3
        src = new Drafty("https://api.tinode.co/");
        src.fmt = new Drafty.Style[]{
                new Drafty.Style(0, 22, 0),
        };
        src.ent = new Drafty.Entity[]{
                new Drafty.Entity("LN").putData("url", "https://www.youtube.com/watch?v=dQw4w9WgXcQ"),
        };
        actual = src.shorten(limit, true);
        expected = new Drafty("https://api.ti…");
        expected.fmt = new Drafty.Style[]{
                new Drafty.Style(0, 15, 0),
        };
        expected.ent = new Drafty.Entity[]{
                new Drafty.Entity("LN").putData("url", "https://www.youtube.com/watch?v=dQw4w9WgXcQ"),
        };
        assertEquals("Shorten 3 has failed", expected, actual);

        // ------- Shorten 4 (two references to the same entity).
        src = new Drafty("Url one, two");
        src.fmt = new Drafty.Style[]{
                new Drafty.Style(9, 3, 0),
                new Drafty.Style(4, 3, 0),
        };
        src.ent = new Drafty.Entity[]{
                new Drafty.Entity("LN").putData("url", "http://tinode.co"),
        };
        actual = src.shorten(limit, true);
        expected = new Drafty("Url one, two");
        expected.fmt = new Drafty.Style[]{
                new Drafty.Style(4, 3, 0),
                new Drafty.Style(9, 3, 0),
        };
        expected.ent = new Drafty.Entity[]{
                new Drafty.Entity("LN").putData("url", "http://tinode.co"),
        };
        assertEquals("Shorten 4 has failed", expected, actual);

        // ------- Shorten 5 (two different entities).
        src = new Drafty("Url one, two");
        src.fmt = new Drafty.Style[] {
                new Drafty.Style(4, 3, 0),
                new Drafty.Style(9, 3, 1),
        };
        src.ent = new Drafty.Entity[]{
                new Drafty.Entity("LN").putData("url", "http://tinode.co"),
                new Drafty.Entity("LN").putData("url", "http://example.com"),
        };
        actual = src.shorten(limit, true);
        expected = new Drafty("Url one, two");
        expected.fmt = new Drafty.Style[]{
                new Drafty.Style(4, 3, 0),
                new Drafty.Style(9, 3, 1),
        };
        expected.ent = new Drafty.Entity[]{
                new Drafty.Entity("LN").putData("url", "http://tinode.co"),
                new Drafty.Entity("LN").putData("url", "http://example.com"),
        };
        assertEquals("Shorten 5 has failed", expected, actual);

        // ------- Shorten 6 (inline image)
        src = new Drafty(" ");
        src.fmt = new Drafty.Style[]{
                new Drafty.Style(0, 1, 0),
        };
        src.ent = new Drafty.Entity[]{
                new Drafty.Entity("IM")
                        .putData("height", 213)
                        .putData("width", 638)
                        .putData("name", "roses.jpg")
                        .putData("val", "<38992, 123456789012345678901234567890123456789012345678901234567890 bytes: ...>")
                        .putData("mime", "image/jpeg"),
        };
        actual = src.shorten(limit, true);
        expected = new Drafty(" ");
        expected.fmt = new Drafty.Style[]{
                new Drafty.Style(0, 1, 0),
        };
        expected.ent = new Drafty.Entity[]{
                new Drafty.Entity("IM")
                        .putData("height", 213)
                        .putData("width", 638)
                        .putData("name", "roses.jpg")
                        .putData("mime", "image/jpeg"),
        };
        assertEquals("Shorten 6 has failed", expected, actual);

        // ------- Shorten 7 (staggered formats)
        src = new Drafty("This text has staggered formats");
        src.fmt = new Drafty.Style[]{
                new Drafty.Style("EM", 5, 8),
                new Drafty.Style("ST", 10, 13),
        };
        actual = src.shorten(limit, true);
        expected = new Drafty("This text has …");
        expected.fmt = new Drafty.Style[]{
                new Drafty.Style("EM", 5, 8),
        };
        assertEquals("Shorten 7 has failed", expected, actual);

        // ------- Shorten 8 (multiple formatting)
        src = new Drafty("This text is formatted and deleted too");
        src.fmt = new Drafty.Style[]{
                new Drafty.Style("ST", 5, 4),
                new Drafty.Style("EM", 13, 9),
                new Drafty.Style("ST", 35, 3),
                new Drafty.Style("DL", 27, 11),
        };

        actual = src.shorten(limit, true);
        expected = new Drafty("This text is f…");
        expected.fmt = new Drafty.Style[]{
                new Drafty.Style("ST", 5, 4),
                new Drafty.Style("EM", 13, 2),
        };
        assertEquals("Shorten 8 has failed", expected, actual);

        //  -------  Shorten 9 (multibyte unicode)
        src = new Drafty("мультибайтовый юникод");
        src.fmt = new Drafty.Style[]{
                new Drafty.Style("ST", 0, 14),
                new Drafty.Style("EM", 15, 6),
        };
        actual = src.shorten(limit, true);
        expected = new Drafty("мультибайтовый…");
        expected.fmt = new Drafty.Style[]{
                new Drafty.Style("ST", 0, 14),
        };
        assertEquals("Shorten 9 has failed", expected, actual);

        //  -------  Shorten 10 (quoted reply)
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
                        .putData("mime","image/jpeg")
                        .putData("val", "<1292, bytes: /9j/4AAQSkZJ.123456789012345678901234567890123456789012345678901234567890.rehH5o6D/9k=>")
                        .putData("width", 25)
                        .putData("height", 14)
                        .putData("size", 968),
                new Drafty.Entity("MN")
                        .putData("val", "usr123abcDE")
        };
        actual = src.shorten(limit, true);

        expected = new Drafty("Alice Johnson …");
        expected.fmt = new Drafty.Style[]{
                new Drafty.Style(0, 13, 0),
                new Drafty.Style("BR", 13, 1),
                new Drafty.Style("QQ", 0, 15)
        };
        expected.ent = new Drafty.Entity[]{
                new Drafty.Entity("MN")
                    .putData("val", "usr123abcDE")
        };
        assertEquals("Shorten 10 has failed", expected, actual);
    }

    @Test
    public void testForward() {
        // ------- Forward 1 (unchanged).
        Drafty src = new Drafty("Alice Johnson This is a reply to replyThis is a Reply -> Forward -> Reply.");
        src.fmt = new Drafty.Style[]{
                new Drafty.Style(0, 13, 0),
                new Drafty.Style("BR", 13,1),
                new Drafty.Style("QQ", 0, 38)
        };
        src.ent = new Drafty.Entity[]{
                new Drafty.Entity("MN")
                        .putData("val", "usr123abcDE")
        };
        Drafty actual = src.forwardedContent();

        Drafty expected = new Drafty("Alice Johnson This is a reply to replyThis is a Reply -> Forward -> Reply.");
        expected.fmt = new Drafty.Style[]{
                new Drafty.Style(0, 13, 0),
                new Drafty.Style("BR", 13,1),
                new Drafty.Style("QQ", 0, 38)
        };
        expected.ent = new Drafty.Entity[]{
                new Drafty.Entity("MN")
                        .putData("val", "usr123abcDE")
        };
        assertEquals("Forward 1 has failed", expected, actual);

        // ------- Forward 2 (mention stripped).
        src = new Drafty("➦ Alice Johnson Alice Johnson This is a simple replyThis is a reply to reply");
        src.fmt = new Drafty.Style[]{
                new Drafty.Style(0, 15, 0),
                new Drafty.Style("BR", 15,1),
                new Drafty.Style(16, 13, 1),
                new Drafty.Style("BR", 29,1),
                new Drafty.Style("QQ", 16, 36)
        };
        src.ent = new Drafty.Entity[]{
                new Drafty.Entity("MN").putData("val", "usr123abcDE"),
                new Drafty.Entity("MN").putData("val", "usr123abcDE")
        };
        actual = src.forwardedContent();
        expected = new Drafty("Alice Johnson This is a simple replyThis is a reply to reply");
        expected.fmt = new Drafty.Style[]{
                new Drafty.Style(0, 13, 0),
                new Drafty.Style("BR", 13,1),
                new Drafty.Style("QQ", 0, 36)
        };
        expected.ent = new Drafty.Entity[]{
                new Drafty.Entity("MN").putData("val", "usr123abcDE")
        };
        assertEquals("Forward 2 has failed", expected, actual);
    }

    @Test
    public void testPreview() {
        // ------- Preview 1.
        Drafty src = new Drafty("Alice Johnson This is a reply to replyThis is a Reply -> Forward -> Reply.");
        src.fmt = new Drafty.Style[]{
                new Drafty.Style(0, 13, 0),
                new Drafty.Style("BR", 13,1),
                new Drafty.Style("QQ", 0, 38)
        };
        src.ent = new Drafty.Entity[]{
                new Drafty.Entity("MN")
                        .putData("val", "usr123abcDE")
        };
        Drafty actual = src.preview(25);
        Drafty expected = new Drafty(" This is a Reply -> Forw…");
        expected.fmt = new Drafty.Style[]{
                new Drafty.Style("QQ", 0, 1)
        };
        assertEquals("Preview 1 has failed", expected, actual);

        // ------- Preview 2.
        src = new Drafty("➦ Alice Johnson Alice Johnson This is a simple replyThis is a reply to reply");
        src.fmt = new Drafty.Style[]{
                new Drafty.Style(0, 15, 0),
                new Drafty.Style("BR", 15,1),
                new Drafty.Style(16, 13, 1),
                new Drafty.Style("BR", 29,1),
                new Drafty.Style("QQ", 16, 36)
        };
        src.ent = new Drafty.Entity[]{
                new Drafty.Entity("MN").putData("val", "usr123abcDE"),
                new Drafty.Entity("MN").putData("val", "usr123abcDE")
        };
        actual = src.preview(25);
        expected = new Drafty("➦  This is a reply to re…");
        expected.fmt = new Drafty.Style[]{
                new Drafty.Style(0, 1, 0),
                new Drafty.Style("QQ", 2, 1)
        };
        expected.ent = new Drafty.Entity[]{
                new Drafty.Entity("MN")
                        .putData("val", "usr123abcDE")
        };
        assertEquals("Preview 2 has failed", expected, actual);
    }

    @Test
    public void testReply() {
        // --------- Reply 1
        Drafty src = new Drafty("Alice Johnson This is a reply to replyThis is a Reply -> Forward -> Reply.");
        src.fmt = new Drafty.Style[]{
                new Drafty.Style(0, 13, 0),
                new Drafty.Style("BR", 13,1),
                new Drafty.Style("QQ", 0, 38)
        };
        src.ent = new Drafty.Entity[]{
                new Drafty.Entity("MN")
                        .putData("val", "usr123abcDE")
        };
        Drafty actual = src.replyContent(25, 3);
        Drafty expected = new Drafty("This is a Reply -> Forwa…");
        assertEquals("Reply 1 has failed", expected, actual);

        // ----------- Reply 2
        src = new Drafty("➦ Alice Johnson Alice Johnson This is a simple replyThis is a reply to reply");
        src.fmt = new Drafty.Style[]{
                new Drafty.Style(0, 15, 0),
                new Drafty.Style("BR", 15,1),
                new Drafty.Style(16, 13, 1),
                new Drafty.Style("BR", 29,1),
                new Drafty.Style("QQ", 16, 36)
        };
        src.ent = new Drafty.Entity[]{
                new Drafty.Entity("MN").putData("val", "usr123abcDE"),
                new Drafty.Entity("MN").putData("val", "usr123abcDE")
        };
        actual = src.replyContent(25, 3);
        expected = new Drafty("➦ This is a reply to rep…");
        expected.fmt = new Drafty.Style[]{
                new Drafty.Style("MN", 0, 1)
        };
        assertEquals("Reply 2 has failed", expected, actual);

        // ----------- Reply 3
        src = new Drafty("Message with attachment");
        src.fmt = new Drafty.Style[]{
                new Drafty.Style(-1, 0, 0),
                new Drafty.Style("ST", 8,4)
        };
        src.ent = new Drafty.Entity[]{
                new Drafty.Entity("EX")
                        .putData("mime","image/jpeg")
                        .putData("val", "<1292, bytes: /9j/4AAQSkZJ.123456789012345678901234567890123456789012345678901234567890.rehH5o6D/9k=>")
                        .putData("width", 25)
                        .putData("height", 14)
                        .putData("size", 968)
                        .putData("name", "hello.jpg"),
        };
        actual = src.replyContent(25, 3);
        expected = new Drafty("Message with attachment ");
        expected.fmt = new Drafty.Style[]{
                new Drafty.Style("ST", 8,4),
                new Drafty.Style(23, 1, 0)
        };
        expected.ent = new Drafty.Entity[]{
                new Drafty.Entity("EX")
                        .putData("mime","image/jpeg")
                        .putData("width", 25)
                        .putData("height", 14)
                        .putData("size", 968)
                        .putData("name", "hello.jpg"),
        };
        assertEquals("Reply 3 has failed", expected, actual);

        // ----------- Reply 4
        src = new Drafty();
        src.fmt = new Drafty.Style[]{
                new Drafty.Style(-1, 0, 0)
        };
        src.ent = new Drafty.Entity[]{
                new Drafty.Entity("EX")
                        .putData("mime","image/jpeg")
                        .putData("val", "<1292, bytes: /9j/4AAQSkZJ.123456789012345678901234567890123456789012345678901234567890.rehH5o6D/9k=>")
                        .putData("width", 25)
                        .putData("height", 14)
                        .putData("size", 968)
                        .putData("name", "hello.jpg"),
        };
        actual = src.replyContent(25, 3);
        expected = new Drafty(" ");
        expected.fmt = new Drafty.Style[]{
                new Drafty.Style(0, 1, 0)
        };
        expected.ent = new Drafty.Entity[]{
                new Drafty.Entity("EX")
                        .putData("mime","image/jpeg")
                        .putData("width", 25)
                        .putData("height", 14)
                        .putData("size", 968)
                        .putData("name", "hello.jpg"),
        };
        assertEquals("Reply 4 has failed", expected, actual);
    }

    @Test
    public void testFormat() {
        // --------- Format 1
        Drafty src = new Drafty("Alice Johnson This is a reply to replyThis is a Reply -> Forward -> Reply.");
        src.fmt = new Drafty.Style[]{
                new Drafty.Style(0, 13, 0),
                new Drafty.Style("BR", 13,1),
                new Drafty.Style("ST", 0, 38)
        };
        src.ent = new Drafty.Entity[]{
                new Drafty.Entity("MN")
                        .putData("val", "usr123abcDE")
        };
        String actual = src.toMarkdown();
        String expected = "*@Alice Johnson\nThis is a reply to reply*This is a Reply -> Forward -> Reply.";
        assertEquals("Format 1 has failed", expected, actual);

        // --------- Format 2

        src = new Drafty("an url: https://www.example.com/abc#fragment and another www.tinode.co");
        src.fmt = new Drafty.Style[]{
                new Drafty.Style("EM", 57, 13),
                new Drafty.Style(8, 36, 0),
                new Drafty.Style(57, 13, 1)
        };
        src.ent = new Drafty.Entity[]{
                new Drafty.Entity("LN")
                        .putData("url", "https://www.example.com/abc#fragment"),
                new Drafty.Entity("LN")
                        .putData("url", "http://www.tinode.co")
        };
        actual = src.toMarkdown();
        expected = "an url: [https://www.example.com/abc#fragment](https://www.example.com/abc#fragment) "+
                "and another _[www.tinode.co](http://www.tinode.co)_";
        assertEquals("Format 2 has failed", expected, actual);
    }
}
