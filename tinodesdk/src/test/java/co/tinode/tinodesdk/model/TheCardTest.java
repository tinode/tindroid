package co.tinode.tinodesdk.model;

import static org.junit.Assert.*;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import co.tinode.tinodesdk.Tinode;

public class TheCardTest {

    @Test
    public void testConstructorEmpty() {
        TheCard card = new TheCard();
        assertNull(card.fn);
    }

    @Test
    public void testConstructorWithFn() {
        TheCard card = new TheCard("Alice", (byte[]) null, null);
        assertEquals("Alice", card.fn);
    }

    @Test
    public void testConstructorWithAllFields() {
        TheCard card = new TheCard("Alice", "http://example.com/img.jpg", "image/jpeg");
        assertEquals("Alice", card.fn);
        assertNull(card.note);
        assertNotNull(card.photo);
        assertEquals("http://example.com/img.jpg", card.photo.ref);
        assertEquals("jpeg", card.photo.type);
        assertArrayEquals(Tinode.NULL_BYTES, card.photo.data);
    }
/*
    @Test
    public void testSetFn() {
        TheCard card = new TheCard().setFn("Bob");
        assertEquals("Bob", card.fn);
    }

    @Test
    public void testSetFnTrim() {
        TheCard card = new TheCard().setFn("  Bob  ");
        assertEquals("Bob", card.fn);
    }

    @Test
    public void testSetFnRemoveIfEmpty() {
        TheCard card = new TheCard();
        card.fn = "Bob";
        card = card.setFn("");
        assertNull(card.fn);
    }

    @Test
    public void testGetFn() {
        TheCard card = new TheCard();
        card.fn = "Charlie";
        assertEquals("Charlie", card.getFn());
    }

    @Test
    public void testGetFnMissing() {
        TheCard card = new TheCard();
        card.note = "Some note";
        assertNull(card.getFn());
    }

    @Test
    public void testSetNote() {
        TheCard card = new TheCard().setNote("My Note");
        assertEquals("My Note", card.note);
    }

    @Test
    public void testSetNoteDelCharIfEmpty() {
        TheCard card = new TheCard();
        card.note = "Old";
        card = card.setNote("");
        assertNull(card.note);
    }

    @Test
    public void testSetPhotoFromRef() {
        TheCard card = new TheCard().setPhoto("http://example.com/a.png", "image/png");
        assertNotNull(card.photo);
        assertEquals("http://example.com/a.png", card.photo.ref);
        assertEquals("png", card.photo.type);
        assertArrayEquals(Tinode.NULL_BYTES, card.photo.data);
    }

    @Test
    public void testSetPhotoFromDataUri() {
        TheCard card = new TheCard().setPhoto(new byte[]{1, 2, 3}, "image/png");
        assertNotNull(card.photo);
        assertArrayEquals(new byte[]{1, 2, 3}, card.photo.data);
        assertEquals("png", card.photo.type);
        assertEquals(Tinode.NULL_VALUE, card.photo.ref);
    }

    @Test
    public void testSetPhotoDelCharIfNull() {
        TheCard card = new TheCard();
        card.photo = new TheCard.Photo();
        card = card.setPhoto((String) null, null);
        assertNull(card.photo);
    }

    @Test
    public void testSetPhotoInferMimeTypeFromExtension() {
        TheCard card = new TheCard().setPhoto("http://example.com/a.jpg", null);
        assertNotNull(card.photo);
        assertEquals("http://example.com/a.jpg", card.photo.ref);
        assertEquals("jpeg", card.photo.type);
        assertArrayEquals(Tinode.NULL_BYTES, card.photo.data);
    }

    @Test
    public void testSetPhotoUseDefaultMimeTypeIfUnknown() {
        TheCard card = new TheCard().setPhoto("http://example.com/a.unknown", null);
        assertNotNull(card.photo);
        assertEquals("http://example.com/a.unknown", card.photo.ref);
        assertEquals("jpeg", card.photo.type);
        assertArrayEquals(Tinode.NULL_BYTES, card.photo.data);
    }
*/
    @Test
    public void testGetPhotoRefNoPhoto() {
        assertNull(new TheCard().getPhotoRef());
    }

    @Test
    public void testGetPhotoUrlRef() {
        TheCard card = new TheCard();
        card.photo = new TheCard.Photo();
        card.photo.ref = "http://example.com/a.png";
        card.photo.type = "png";
        assertEquals("http://example.com/a.png", card.getPhotoRef());
    }

    @Test
    public void testGetPhotoUrlDataUri() {
        TheCard card = new TheCard();
        card.photo = new TheCard.Photo();
        card.photo.data = new byte[]{1, 2, 3};
        card.photo.type = "png";
        // getPhotoRef() returns ref field, not data URL
        // Check the data field directly
        assertArrayEquals(new byte[]{1, 2, 3}, card.photo.data);
        assertEquals("png", card.photo.type);
        assertNull(card.getPhotoRef());
    }

    @Test
    public void testGetPhotoUrlHandleMissingType() {
        TheCard card = new TheCard();
        card.photo = new TheCard.Photo();
        card.photo.data = new byte[]{1, 2, 3};
        // Check the data field directly
        assertArrayEquals(new byte[]{1, 2, 3}, card.photo.data);
        assertNull(card.getPhotoRef());
    }
/*
    @Test
    public void testAddPhone() {
        TheCard card = new TheCard();
        card = card.addPhone("1234567890", TheCard.CommDes.MOBILE);
        assertEquals(1, card.comm.length);
        assertEquals("1234567890", card.comm[0].value);
        assertArrayEquals(new TheCard.CommDes[]{TheCard.CommDes.MOBILE}, card.comm[0].des);
        assertEquals(TheCard.CommProto.TEL, card.comm[0].proto);
    }

    @Test
    public void testSetPhoneReplace() {
        TheCard card = new TheCard();
        card.comm = new TheCard.CommEntry[]{
                new TheCard.CommEntry(TheCard.CommProto.TEL,
                        new TheCard.CommDes[]{TheCard.CommDes.WORK}, "000")
        };
        card.setPhone("1234567890", TheCard.CommDes.WORK);
        assertEquals(1, card.comm.length);
        assertEquals("1234567890", card.comm[0].value);
        assertArrayEquals(new TheCard.CommDes[]{TheCard.CommDes.WORK}, card.comm[0].des);
        assertEquals(TheCard.CommProto.TEL, card.comm[0].proto);
    }

    @Test
    public void testAddEmail() {
        TheCard card = new TheCard();
        card.addEmail("bob@example.com", TheCard.CommDes.WORK);
        assertEquals(1, card.comm.length);
        assertEquals("bob@example.com", card.comm[0].value);
        assertArrayEquals(new TheCard.CommDes[]{TheCard.CommDes.WORK}, card.comm[0].des);
        assertEquals(TheCard.CommProto.EMAIL, card.comm[0].proto);
    }

    @Test
    public void testAddTinodeID() {
        TheCard card = new TheCard();
        card.addTinodeID("usr123", TheCard.CommDes.HOME);
        assertEquals(1, card.comm.length);
        assertEquals("usr123", card.comm[0].value);
        assertArrayEquals(new TheCard.CommDes[]{TheCard.CommDes.HOME}, card.comm[0].des);
        assertEquals(TheCard.CommProto.TINODE, card.comm[0].proto);
    }

    @Test
    public void testClearPhone() {
        TheCard card = new TheCard();
        card.comm = new TheCard.CommEntry[]{
                new TheCard.CommEntry(TheCard.CommProto.TEL,
                        new TheCard.CommDes[]{TheCard.CommDes.MOBILE}, "123"),
                new TheCard.CommEntry(TheCard.CommProto.TEL,
                        new TheCard.CommDes[]{TheCard.CommDes.WORK}, "456")
        };
        card.clearPhone("123", TheCard.CommDes.MOBILE);
        assertEquals(1, card.comm.length);
        assertEquals("456", card.comm[0].value);
    }

    @Test
    public void testSetEmail() {
        TheCard card = new TheCard();
        card.comm = new TheCard.CommEntry[]{
                new TheCard.CommEntry(TheCard.CommProto.EMAIL,
                        new TheCard.CommDes[]{TheCard.CommDes.WORK}, "old@example.com")
        };
        card.setEmail("new@example.com", TheCard.CommDes.WORK);
        assertEquals(1, card.comm.length);
        assertEquals("new@example.com", card.comm[0].value);
        assertArrayEquals(new TheCard.CommDes[]{TheCard.CommDes.WORK}, card.comm[0].des);
        assertEquals(TheCard.CommProto.EMAIL, card.comm[0].proto);
    }

    @Test
    public void testSetTinodeID() {
        TheCard card = new TheCard();
        card.comm = new TheCard.CommEntry[]{
                new TheCard.CommEntry(TheCard.CommProto.TINODE,
                        new TheCard.CommDes[]{TheCard.CommDes.HOME}, "tinode:id/usr111")
        };
        card.setTinodeID("usr222", TheCard.CommDes.HOME);
        assertEquals(1, card.comm.length);
        assertEquals("usr222", card.comm[0].value);
        assertArrayEquals(new TheCard.CommDes[]{TheCard.CommDes.HOME}, card.comm[0].des);
        assertEquals(TheCard.CommProto.TINODE, card.comm[0].proto);
    }

    @Test
    public void testClearEmail() {
        TheCard card = new TheCard();
        card.comm = new TheCard.CommEntry[]{
                new TheCard.CommEntry(TheCard.CommProto.EMAIL,
                        new TheCard.CommDes[]{TheCard.CommDes.HOME}, "a@example.com"),
                new TheCard.CommEntry(TheCard.CommProto.EMAIL,
                        new TheCard.CommDes[]{TheCard.CommDes.WORK}, "b@example.com")
        };
        card.clearEmail("a@example.com", TheCard.CommDes.HOME);
        assertEquals(1, card.comm.length);
        assertEquals("b@example.com", card.comm[0].value);
    }

    @Test
    public void testClearTinodeID() {
        TheCard card = new TheCard();
        card.comm = new TheCard.CommEntry[]{
                new TheCard.CommEntry(TheCard.CommProto.TINODE,
                        new TheCard.CommDes[]{TheCard.CommDes.HOME}, "tinode:id/usr1"),
                new TheCard.CommEntry(TheCard.CommProto.TINODE,
                        new TheCard.CommDes[]{TheCard.CommDes.WORK}, "tinode:id/usr2")
        };
        card.clearTinodeID("tinode:id/usr1", TheCard.CommDes.HOME);
        assertEquals(1, card.comm.length);
        assertEquals("tinode:id/usr2", card.comm[0].value);
    }

    @Test
    public void testExportVCardNull() {
        assertNull(TheCard.exportVCard(null));
    }

    @Test
    public void testExportVCardMinimal() {
        TheCard card = new TheCard();
        card.fn = "Alice";
        String vcard = TheCard.exportVCard(card);
        assertNotNull(vcard);
        assertTrue(vcard.contains("BEGIN:VCARD"));
        assertTrue(vcard.contains("VERSION:3.0"));
        assertTrue(vcard.contains("FN:Alice"));
        assertTrue(vcard.contains("END:VCARD"));
    }

    @Test
    public void testExportVCardFull() {
        TheCard card = new TheCard();
        card.fn = "Alice";
        card.note = "My Note";
        card.photo = new TheCard.Photo();
        card.photo.type = "jpeg";
        card.photo.data = new byte[]{'b', 'a', 's', 'e', '6', '4', 'd', 'a', 't', 'a'};
        card.photo.ref = Tinode.NULL_VALUE;
        card.comm = new TheCard.CommEntry[]{
                new TheCard.CommEntry(TheCard.CommProto.TEL,
                        new TheCard.CommDes[]{TheCard.CommDes.MOBILE}, "123456"),
                new TheCard.CommEntry(TheCard.CommProto.EMAIL,
                        new TheCard.CommDes[]{TheCard.CommDes.HOME}, "alice@example.com"),
                new TheCard.CommEntry(TheCard.CommProto.TINODE,
                        new TheCard.CommDes[]{TheCard.CommDes.HOME}, "tinode:id/usr123"),
                new TheCard.CommEntry(TheCard.CommProto.HTTP,
                        new TheCard.CommDes[]{TheCard.CommDes.WORK}, "https://example.com")
        };
        String vcard = TheCard.exportVCard(card);
        assertNotNull(vcard);
        assertTrue(vcard.contains("FN:Alice"));
        assertTrue(vcard.contains("NOTE:My Note"));
        // The bytes {'b','a','s','e','6','4','d','a','t','a'} base64-encoded = "YmFzZTY0ZGF0YQ=="
        assertTrue(vcard.contains("PHOTO;TYPE=JPEG;ENCODING=b:YmFzZTY0ZGF0YQ=="));
        assertTrue(vcard.contains("TEL;TYPE=MOBILE:123456"));
        assertTrue(vcard.contains("EMAIL;TYPE=HOME:alice@example.com"));
        assertTrue(vcard.contains("IMPP;TYPE=HOME:tinode:id/usr123"));
        assertTrue(vcard.contains("URL;TYPE=WORK:https://example.com"));
    }

    @Test
    public void testExportVCardWithNOrgAndTitle() {
        TheCard card = new TheCard();
        card.fn = "Alice";
        card.n = new TheCard.Name();
        card.n.surname = "Wonderland";
        card.n.given = "Alice";
        card.org = new TheCard.Organization();
        card.org.fn = "Organization";
        card.org.title = "Boss";
        String vcard = TheCard.exportVCard(card);
        assertNotNull(vcard);
        assertTrue(vcard.contains("FN:Alice"));
        assertTrue(vcard.contains("N:Wonderland;Alice;;;\r\n"));
        assertTrue(vcard.contains("ORG:Organization\r\n"));
        assertTrue(vcard.contains("TITLE:Boss\r\n"));
    }

    @Test
    public void testExportVCardWithBirthdayFullDate() {
        TheCard card = new TheCard();
        card.fn = "Alice";
        card.bday = new TheCard.Birthday();
        card.bday.y = 1985;
        card.bday.m = 6;
        card.bday.d = 15;
        String vcard = TheCard.exportVCard(card);
        assertNotNull(vcard);
        assertTrue(vcard.contains("BDAY:1985-06-15"));
    }

    @Test
    public void testExportVCardWithBirthdayNoYear() {
        TheCard card = new TheCard();
        card.fn = "Alice";
        card.bday = new TheCard.Birthday();
        card.bday.m = 6;
        card.bday.d = 15;
        String vcard = TheCard.exportVCard(card);
        assertNotNull(vcard);
        assertTrue(vcard.contains("BDAY:---06-15"));
    }

    @Test
    public void testImportVCardNull() {
        assertNull(TheCard.importVCard(null));
        assertNull(TheCard.importVCard(""));
    }

    @Test
    public void testImportVCardMinimal() {
        String vcard = "BEGIN:VCARD\r\nVERSION:3.0\r\nFN:Alice\r\nEND:VCARD";
        TheCard card = TheCard.importVCard(vcard);
        assertNotNull(card);
        assertEquals("Alice", card.fn);
    }

    @Test
    public void testImportVCardFull() {
        String vcard = """
                BEGIN:VCARD\r
                VERSION:3.0\r
                FN:Alice\r
                NOTE:My Note\r
                PHOTO;TYPE=JPEG;ENCODING=b:base64data\r
                TEL;TYPE=CELL:123456\r
                EMAIL;TYPE=HOME:alice@example.com\r
                IMPP;TYPE=HOME:tinode:id/usr123\r
                END:VCARD""";
        TheCard card = TheCard.importVCard(vcard);
        assertNotNull(card);
        assertEquals("Alice", card.fn);
        assertEquals("My Note", card.note);
        assertNotNull(card.photo);
        assertEquals("jpeg", card.photo.type);
        // "base64data" base64 decoded = {0x6D, (byte) 0xAB, 0x1E, (byte) 0xEB, (byte) 0x87, 0x5A, (byte) 0xB5}
        assertArrayEquals(new byte[]{0x6D, (byte) 0xAB, 0x1E, (byte) 0xEB, (byte) 0x87, 0x5A, (byte) 0xB5}, card.photo.data);
        assertEquals(Tinode.NULL_VALUE, card.photo.ref);

        boolean hasTel = false, hasEmail = false, hasTinode = false;
        for (TheCard.CommEntry entry : card.comm) {
            if (entry.proto == TheCard.CommProto.TEL && entry.value.equals("123456")) hasTel = true;
            if (entry.proto == TheCard.CommProto.EMAIL && entry.value.equals("alice@example.com")) hasEmail = true;
            if (entry.proto == TheCard.CommProto.TINODE && entry.value.equals("tinode:id/usr123")) hasTinode = true;
        }
        assertTrue(hasTel);
        assertTrue(hasEmail);
        assertTrue(hasTinode);
    }

    @Test
    public void testImportVCardWithRefPhoto() {
        String vcard = """
                BEGIN:VCARD\r
                FN:Bob\r
                PHOTO;VALUE=URI:http://example.com/photo.jpg\r
                END:VCARD""";
        TheCard card = TheCard.importVCard(vcard);
        assertNotNull(card);
        assertEquals("Bob", card.fn);
        assertNotNull(card.photo);
        assertEquals("jpeg", card.photo.type);
        assertArrayEquals(Tinode.NULL_BYTES, card.photo.data);
        assertEquals("http://example.com/photo.jpg", card.photo.ref);
    }

    @Test
    public void testImportVCardWithNOrgAndTitle() {
        String vcard = """
                BEGIN:VCARD
                VERSION:3.0
                N:Miner;Coal;Diamond;Dr.;Jr.
                FN:Dr. Coal D. Miner
                ORG:Most Evil Corp;North American Division
                TITLE:CEO
                END:VCARD""";
        TheCard card = TheCard.importVCard(vcard);
        assertNotNull(card);
        assertNotNull(card.n);
        assertEquals("Miner", card.n.surname);
        assertEquals("Coal", card.n.given);
        assertEquals("Diamond", card.n.additional);
        assertEquals("Dr.", card.n.prefix);
        assertEquals("Jr.", card.n.suffix);
        assertNotNull(card.org);
        assertEquals("Most Evil Corp", card.org.fn);
        assertEquals("CEO", card.org.title);
    }

    @Test
    public void testImportVCardUnescapeSpecialChars() {
        String vcard = """
                BEGIN:VCARD
                VERSION:3.0
                FN:John\\, Doe
                N:Doe;John\\, Jr.;;;
                NOTE:John Doe has a long and varied history\\, being documented on more police files that anyone
                ORG:Company\\, Inc.
                TITLE:CEO\\; Founder
                END:VCARD""";
        TheCard card = TheCard.importVCard(vcard);
        assertNotNull(card);
        assertEquals("John, Doe", card.fn);
        assertEquals("Doe", card.n.surname);
        assertEquals("John, Jr.", card.n.given);
        assertEquals("John Doe has a long and varied history, being documented on more police files that anyone", card.note);
        assertEquals("Company, Inc.", card.org.fn);
        assertEquals("CEO; Founder", card.org.title);
    }

    @Test
    public void testImportVCardQuotedPrintable() {
        String vcard = """
                BEGIN:VCARD
                VERSION:2.1
                FN;CHARSET=UTF-8;QUOTED-PRINTABLE:=E5=8D=81=E5=9F=8E=E7=9B=AE=E7=AE=A1=E7=90=86=E5=A4=A7=E5=9E=8B=E7=9F=A5=E5=
                 =BA=A7
                N;CHARSET=UTF-8;QUOTED-PRINTABLE:=E5=8D=81=E5=9F=8E;=E7=9B=AE=E7=AE=A1;;;
                ORG;ENCODING=QUOTED-PRINTABLE:=E4=BC=9A=E7=A4=BE
                TITLE;QUOTED-PRINTABLE:=E7=A4=BE=E9=95=B7
                NOTE;QUOTED-PRINTABLE:This is a note with=20special characters
                END:VCARD""";
        TheCard card = TheCard.importVCard(vcard);
        assertNotNull(card);
        assertEquals("十城目管理大型知座", card.fn);
        assertEquals("十城", card.n.surname);
        assertEquals("目管", card.n.given);
        assertEquals("会社", card.org.fn);
        assertEquals("社長", card.org.title);
        assertEquals("This is a note with special characters", card.note);
    }

    @Test
    public void testImportVCardBdayYYYYMMDD() {
        String vcard = "BEGIN:VCARD\nVERSION:3.0\nFN:John Doe\nBDAY:1985-06-15\nEND:VCARD";
        TheCard card = TheCard.importVCard(vcard);
        assertNotNull(card);
        assertNotNull(card.bday);
        assertEquals(Integer.valueOf(1985), card.bday.y);
        assertEquals(Integer.valueOf(6), card.bday.m);
        assertEquals(Integer.valueOf(15), card.bday.d);
    }

    @Test
    public void testImportVCardBdayYYYYMMDDNoHyphens() {
        String vcard = "BEGIN:VCARD\nVERSION:3.0\nFN:John Doe\nBDAY:19850615\nEND:VCARD";
        TheCard card = TheCard.importVCard(vcard);
        assertNotNull(card);
        assertNotNull(card.bday);
        assertEquals(Integer.valueOf(1985), card.bday.y);
        assertEquals(Integer.valueOf(6), card.bday.m);
        assertEquals(Integer.valueOf(15), card.bday.d);
    }

    @Test
    public void testImportVCardBdayYYMMDD1900() {
        String vcard = "BEGIN:VCARD\nVERSION:3.0\nFN:John Doe\nBDAY:850615\nEND:VCARD";
        TheCard card = TheCard.importVCard(vcard);
        assertNotNull(card);
        assertNotNull(card.bday);
        assertEquals(Integer.valueOf(1985), card.bday.y);
        assertEquals(Integer.valueOf(6), card.bday.m);
        assertEquals(Integer.valueOf(15), card.bday.d);
    }

    @Test
    public void testImportVCardBdayYYMMDD2000() {
        String vcard = "BEGIN:VCARD\nVERSION:3.0\nFN:John Doe\nBDAY:240315\nEND:VCARD";
        TheCard card = TheCard.importVCard(vcard);
        assertNotNull(card);
        assertNotNull(card.bday);
        assertEquals(Integer.valueOf(2024), card.bday.y);
        assertEquals(Integer.valueOf(3), card.bday.m);
        assertEquals(Integer.valueOf(15), card.bday.d);
    }

    @Test
    public void testImportVCardBdayNoYear() {
        String vcard = "BEGIN:VCARD\nVERSION:3.0\nFN:John Doe\nBDAY:--0615\nEND:VCARD";
        TheCard card = TheCard.importVCard(vcard);
        assertNotNull(card);
        assertNotNull(card.bday);
        assertNull(card.bday.y);
        assertEquals(Integer.valueOf(6), card.bday.m);
        assertEquals(Integer.valueOf(15), card.bday.d);
    }

    @Test
    public void testImportVCardBdayNoYearFourHyphens() {
        String vcard = "BEGIN:VCARD\nVERSION:3.0\nFN:John Doe\nBDAY:----0615\nEND:VCARD";
        TheCard card = TheCard.importVCard(vcard);
        assertNotNull(card);
        assertNotNull(card.bday);
        assertNull(card.bday.y);
        assertEquals(Integer.valueOf(6), card.bday.m);
        assertEquals(Integer.valueOf(15), card.bday.d);
    }

    @Test
    public void testImportVCardBdayNoYearWithHyphensSeparator() {
        String vcard = "BEGIN:VCARD\nVERSION:3.0\nFN:John Doe\nBDAY:--06-15\nEND:VCARD";
        TheCard card = TheCard.importVCard(vcard);
        assertNotNull(card);
        assertNotNull(card.bday);
        assertNull(card.bday.y);
        assertEquals(Integer.valueOf(6), card.bday.m);
        assertEquals(Integer.valueOf(15), card.bday.d);
    }

    @Test
    public void testImportVCardBdayIgnoreTime() {
        String vcard = "BEGIN:VCARD\nVERSION:3.0\nFN:John Doe\nBDAY:1985-06-15T12:30:00Z\nEND:VCARD";
        TheCard card = TheCard.importVCard(vcard);
        assertNotNull(card);
        assertNotNull(card.bday);
        assertEquals(Integer.valueOf(1985), card.bday.y);
        assertEquals(Integer.valueOf(6), card.bday.m);
        assertEquals(Integer.valueOf(15), card.bday.d);
    }

    @Test
    public void testImportVCardBdayInvalidMonth() {
        String vcard = "BEGIN:VCARD\nVERSION:3.0\nFN:John Doe\nBDAY:19851315\nEND:VCARD";
        TheCard card = TheCard.importVCard(vcard);
        assertNotNull(card);
        assertNull(card.bday);
    }

    @Test
    public void testImportVCardBdayInvalidDay() {
        String vcard = "BEGIN:VCARD\nVERSION:3.0\nFN:John Doe\nBDAY:19850632\nEND:VCARD";
        TheCard card = TheCard.importVCard(vcard);
        assertNotNull(card);
        assertNull(card.bday);
    }

    @Test
    public void testImportVCardBdayInvalidYear() {
        String vcard = "BEGIN:VCARD\nVERSION:3.0\nFN:John Doe\nBDAY:10007059\nEND:VCARD";
        TheCard card = TheCard.importVCard(vcard);
        assertNotNull(card);
        assertNull(card.bday);
    }

    @Test
    public void testGetComm() {
        TheCard card = new TheCard();
        card.comm = new TheCard.CommEntry[]{
                new TheCard.CommEntry(TheCard.CommProto.TEL,
                        new TheCard.CommDes[]{TheCard.CommDes.MOBILE}, "123"),
                new TheCard.CommEntry(TheCard.CommProto.EMAIL,
                        new TheCard.CommDes[]{TheCard.CommDes.HOME}, "a@test.com"),
                new TheCard.CommEntry(TheCard.CommProto.TEL,
                        new TheCard.CommDes[]{TheCard.CommDes.WORK}, "456")
        };
        List<TheCard.CommEntry> phones = card.getComm(TheCard.CommProto.TEL);
        assertEquals(2, phones.size());
        assertEquals("123", phones.get(0).value);
        assertEquals("456", phones.get(1).value);

        List<TheCard.CommEntry> emails = card.getComm(TheCard.CommProto.EMAIL);
        assertEquals(1, emails.size());
        assertEquals("a@test.com", emails.get(0).value);
    }

    @Test
    public void testGetCommNonExistent() {
        TheCard card = new TheCard();
        card.comm = new TheCard.CommEntry[]{
                new TheCard.CommEntry(TheCard.CommProto.TEL,
                        new TheCard.CommDes[]{TheCard.CommDes.MOBILE}, "123")
        };
        List<TheCard.CommEntry> result = card.getComm(TheCard.CommProto.EMAIL);
        assertEquals(0, result.size());
    }

    @Test
    public void testGetCommNoComm() {
        TheCard card = new TheCard();
        card.fn = "Test";
        List<TheCard.CommEntry> result = card.getComm(TheCard.CommProto.TEL);
        assertEquals(0, result.size());
    }

    @Test
    public void testImportVCardNoDuplicatesSinglePhone() {
        String vcard = "BEGIN:VCARD\r\nVERSION:3.0\r\nFN:Test\r\nTEL;TYPE=CELL:123456\r\nEND:VCARD";
        TheCard card = TheCard.importVCard(vcard);
        assertNotNull(card);
        assertEquals(1, card.comm.length);
        assertEquals(1, card.getComm(TheCard.CommProto.TEL).size());
    }

    @Test
    public void testImportVCardNoDuplicatesMultiplePhones() {
        String vcard = "BEGIN:VCARD\r\nVERSION:3.0\r\nFN:Test\r\nTEL;TYPE=CELL:111\r\nTEL;TYPE=HOME:222\r\nEND:VCARD";
        TheCard card = TheCard.importVCard(vcard);
        assertNotNull(card);
        assertEquals(2, card.comm.length);
        assertEquals(2, card.getComm(TheCard.CommProto.TEL).size());
    }

    @Test
    public void testImportVCardNoDuplicatesMixedTypes() {
        String vcard = "BEGIN:VCARD\r\nVERSION:3.0\r\nFN:Test\r\nTEL:111\r\nEMAIL:a@test.com\r\nTEL:222\r\nEND:VCARD";
        TheCard card = TheCard.importVCard(vcard);
        assertNotNull(card);
        assertEquals(3, card.comm.length);
        assertEquals(2, card.getComm(TheCard.CommProto.TEL).size());
        assertEquals(1, card.getComm(TheCard.CommProto.EMAIL).size());
    }

    @Test
    public void testImportVCardDeduplicateSamePhoneMultipleTypes() {
        String vcard = """
                BEGIN:VCARD\r
                VERSION:3.0\r
                FN:Test\r
                TEL;TYPE=WORK,VOICE:(111) 555-1212\r
                TEL;TYPE=HOME,VOICE:(111) 555-1212\r
                END:VCARD""";
        TheCard card = TheCard.importVCard(vcard);
        assertNotNull(card);
        assertEquals(1, card.comm.length);
        assertEquals(3, card.comm[0].des.length);
        assertTrue(Arrays.asList(card.comm[0].des).contains(TheCard.CommDes.WORK));
        assertTrue(Arrays.asList(card.comm[0].des).contains(TheCard.CommDes.VOICE));
        assertTrue(Arrays.asList(card.comm[0].des).contains(TheCard.CommDes.HOME));
    }

    @Test
    public void testImportVCardCommaSeparatedTypes() {
        String vcard = """
                BEGIN:VCARD\r
                VERSION:3.0\r
                FN:Test\r
                TEL;TYPE=WORK,VOICE,FAX:123-456-7890\r
                END:VCARD""";
        TheCard card = TheCard.importVCard(vcard);
        assertNotNull(card);
        assertEquals(1, card.comm.length);
        assertEquals(3, card.comm[0].des.length);
        assertTrue(Arrays.asList(card.comm[0].des).contains(TheCard.CommDes.WORK));
        assertTrue(Arrays.asList(card.comm[0].des).contains(TheCard.CommDes.VOICE));
        assertTrue(Arrays.asList(card.comm[0].des).contains(TheCard.CommDes.FAX));
    }

    @Test
    public void testExportImportCycleNoDuplication() {
        TheCard card = new TheCard();
        card.setFn("Alice Johnson");
        card.addPhone("+15551234567", TheCard.CommDes.MOBILE);
        card.addPhone("+15559876543", TheCard.CommDes.WORK);
        card.addEmail("alice@example.com", TheCard.CommDes.HOME);
        card.addEmail("alice.johnson@work.com", TheCard.CommDes.WORK);
        card.addTinodeID("tinode:id/usrAlice123", TheCard.CommDes.HOME);

        assertEquals(5, card.comm.length);
        assertEquals(2, card.getComm(TheCard.CommProto.TEL).size());
        assertEquals(2, card.getComm(TheCard.CommProto.EMAIL).size());
        assertEquals(1, card.getComm(TheCard.CommProto.TINODE).size());

        String vcardStr = TheCard.exportVCard(card);
        assertNotNull(vcardStr);

        TheCard importedCard = TheCard.importVCard(vcardStr);
        assertNotNull(importedCard);
        assertEquals(5, importedCard.comm.length);

        List<TheCard.CommEntry> phones = importedCard.getComm(TheCard.CommProto.TEL);
        List<TheCard.CommEntry> emails = importedCard.getComm(TheCard.CommProto.EMAIL);
        List<TheCard.CommEntry> tinodeIds = importedCard.getComm(TheCard.CommProto.TINODE);

        assertEquals(2, phones.size());
        assertEquals(2, emails.size());
        assertEquals(1, tinodeIds.size());

        assertTrue(phones.stream().anyMatch(p -> p.value.equals("+15551234567")));
        assertTrue(phones.stream().anyMatch(p -> p.value.equals("+15559876543")));
        assertTrue(emails.stream().anyMatch(e -> e.value.equals("alice@example.com")));
        assertTrue(emails.stream().anyMatch(e -> e.value.equals("alice.johnson@work.com")));
        assertEquals("tinode:id/usrAlice123", tinodeIds.get(0).value);
    }

    @Test
    public void testMultipleExportImportCycles() {
        TheCard card = new TheCard().setFn("Bob Smith");
        card.addPhone("+15551111111", TheCard.CommDes.MOBILE);
        card.addEmail("bob@test.com", TheCard.CommDes.HOME);

        // First cycle
        String vcardStr = TheCard.exportVCard(card);
        TheCard imported1 = TheCard.importVCard(vcardStr);
        assertNotNull(imported1);
        assertEquals(1, imported1.getComm(TheCard.CommProto.TEL).size());
        assertEquals(1, imported1.getComm(TheCard.CommProto.EMAIL).size());

        // Second cycle
        vcardStr = TheCard.exportVCard(imported1);
        TheCard imported2 = TheCard.importVCard(vcardStr);
        assertNotNull(imported2);
        assertEquals(1, imported2.getComm(TheCard.CommProto.TEL).size());
        assertEquals(1, imported2.getComm(TheCard.CommProto.EMAIL).size());

        // Third cycle
        vcardStr = TheCard.exportVCard(imported2);
        TheCard imported3 = TheCard.importVCard(vcardStr);
        assertNotNull(imported3);
        assertEquals(1, imported3.getComm(TheCard.CommProto.TEL).size());
        assertEquals(1, imported3.getComm(TheCard.CommProto.EMAIL).size());
    }

    @Test
    public void testExportImportTinodeEntries() {
        TheCard card = new TheCard();
        card.addTinodeID("tinode:id/usrTest123", TheCard.CommDes.HOME);
        card.addTinodeID("tinode:id/usrTest456", TheCard.CommDes.WORK);

        String vcardStr = TheCard.exportVCard(card);
        assertNotNull(vcardStr);
        assertTrue(vcardStr.contains("IMPP"));
        assertTrue(vcardStr.contains("usrTest123"));
        assertTrue(vcardStr.contains("usrTest456"));

        TheCard imported = TheCard.importVCard(vcardStr);
        assertNotNull(imported);
        List<TheCard.CommEntry> tinodeIds = imported.getComm(TheCard.CommProto.TINODE);
        assertEquals(2, tinodeIds.size());
        assertTrue(tinodeIds.stream().anyMatch(t -> t.value.equals("tinode:id/usrTest123")));
        assertTrue(tinodeIds.stream().anyMatch(t -> t.value.equals("tinode:id/usrTest456")));
    }

    @Test
    public void testExportImportURLEntries() {
        TheCard card = new TheCard();
        card.setFn("Test User");
        card.comm = new TheCard.CommEntry[]{
                new TheCard.CommEntry(TheCard.CommProto.HTTP,
                        new TheCard.CommDes[]{TheCard.CommDes.WORK}, "https://example.com"),
                new TheCard.CommEntry(TheCard.CommProto.HTTP,
                        new TheCard.CommDes[]{TheCard.CommDes.PERSONAL}, "https://mysite.org")
        };

        String vcardStr = TheCard.exportVCard(card);
        assertNotNull(vcardStr);
        assertTrue(vcardStr.contains("URL"));
        assertTrue(vcardStr.contains("https://example.com"));
        assertTrue(vcardStr.contains("https://mysite.org"));

        TheCard imported = TheCard.importVCard(vcardStr);
        assertNotNull(imported);
        List<TheCard.CommEntry> urls = imported.getComm(TheCard.CommProto.HTTP);
        assertEquals(2, urls.size());
        assertTrue(urls.stream().anyMatch(u -> u.value.equals("https://example.com")));
        assertTrue(urls.stream().anyMatch(u -> u.value.equals("https://mysite.org")));
    }

    @Test
    public void testImportContactsWithoutTypeParameter() {
        String vcard = """
                BEGIN:VCARD
                VERSION:3.0
                FN:Test User
                TEL:(555) 123-4567
                EMAIL:test@example.com
                IMPP:tinode:id/usrNoType
                END:VCARD""";
        TheCard card = TheCard.importVCard(vcard);
        assertNotNull(card);
        assertEquals("Test User", card.fn);
        assertEquals(3, card.comm.length);

        TheCard.CommEntry phone = null;
        TheCard.CommEntry email = null;
        TheCard.CommEntry tinode = null;
        for (TheCard.CommEntry entry : card.comm) {
            if (entry.proto == TheCard.CommProto.TEL) phone = entry;
            if (entry.proto == TheCard.CommProto.EMAIL) email = entry;
            if (entry.proto == TheCard.CommProto.TINODE) tinode = entry;
        }

        assertNotNull(phone);
        assertEquals("(555) 123-4567", phone.value);
        assertEquals(0, phone.des.length);

        assertNotNull(email);
        assertEquals("test@example.com", email.value);
        assertEquals(0, email.des.length);

        assertNotNull(tinode);
        assertEquals("tinode:id/usrNoType", tinode.value);
        assertEquals(0, tinode.des.length);
    }
*/
    // JSON Serialization/Deserialization Tests

    @Test
    public void testJsonSerializeMinimal() throws Exception {
        TheCard card = new TheCard();
        card.fn = "Alice";

        ObjectMapper mapper = Tinode.getJsonMapper();
        String json = mapper.writeValueAsString(card);

        assertNotNull(json);
        assertTrue(json.contains("\"fn\":\"Alice\""));
        // NON_DEFAULT should exclude null fields
        assertFalse(json.contains("\"note\""));
        assertFalse(json.contains("\"photo\""));
    }

    @Test
    public void testJsonDeserializeMinimal() throws Exception {
        String json = "{\"fn\":\"Bob\"}";

        ObjectMapper mapper = Tinode.getJsonMapper();
        TheCard card = mapper.readValue(json, TheCard.class);

        assertNotNull(card);
        assertEquals("Bob", card.fn);
        assertNull(card.note);
        assertNull(card.photo);
        assertNull(card.comm);
    }
/*
    @Test
    public void testJsonSerializeFull() throws Exception {
        TheCard card = new TheCard();
        card.fn = "Alice Johnson";
        card.note = "Test note";

        card.n = new TheCard.Name();
        card.n.given = "Alice";
        card.n.surname = "Johnson";

        card.org = new TheCard.Organization();
        card.org.fn = "Test Company";
        card.org.title = "Engineer";

        card.bday = new TheCard.Birthday();
        card.bday.y = 1990;
        card.bday.m = 5;
        card.bday.d = 15;

        card.photo = new TheCard.Photo();
        card.photo.type = "jpeg";
        card.photo.ref = "http://example.com/photo.jpg";
        card.photo.data = Tinode.NULL_BYTES;

        card.comm = new TheCard.CommEntry[]{
                new TheCard.CommEntry(TheCard.CommProto.TEL,
                        new TheCard.CommDes[]{TheCard.CommDes.MOBILE}, "+15551234567"),
                new TheCard.CommEntry(TheCard.CommProto.EMAIL,
                        new TheCard.CommDes[]{TheCard.CommDes.WORK}, "alice@example.com")
        };

        ObjectMapper mapper = Tinode.getJsonMapper();
        String json = mapper.writeValueAsString(card);

        assertNotNull(json);
        assertTrue(json.contains("\"fn\":\"Alice Johnson\""));
        assertTrue(json.contains("\"note\":\"Test note\""));
        assertTrue(json.contains("\"given\":\"Alice\""));
        assertTrue(json.contains("\"surname\":\"Johnson\""));
        assertTrue(json.contains("\"fn\":\"Test Company\""));
        assertTrue(json.contains("\"title\":\"Engineer\""));
        assertTrue(json.contains("\"y\":1990"));
        assertTrue(json.contains("\"m\":5"));
        assertTrue(json.contains("\"d\":15"));
        assertTrue(json.contains("\"type\":\"jpeg\""));
        assertTrue(json.contains("\"ref\":\"http://example.com/photo.jpg\""));
        assertTrue(json.contains("\"proto\":\"tel\""));
        assertTrue(json.contains("\"+15551234567\""));
        assertTrue(json.contains("\"proto\":\"email\""));
        assertTrue(json.contains("\"alice@example.com\""));
    }

    @Test
    public void testJsonDeserializeFull() throws Exception {
        String json = """
                {
                  "fn": "Alice Johnson",
                  "note": "Test note",
                  "n": {
                    "given": "Alice",
                    "surname": "Johnson"
                  },
                  "org": {
                    "fn": "Test Company",
                    "title": "Engineer"
                  },
                  "bday": {
                    "y": 1990,
                    "m": 5,
                    "d": 15
                  },
                  "photo": {
                    "type": "jpeg",
                    "ref": "http://example.com/photo.jpg"
                  },
                  "comm": [
                    {
                      "proto": "tel",
                      "des": ["mobile"],
                      "value": "+15551234567"
                    },
                    {
                      "proto": "email",
                      "des": ["work"],
                      "value": "alice@example.com"
                    }
                  ]
                }
                """;

        ObjectMapper mapper = Tinode.getJsonMapper();
        TheCard card = mapper.readValue(json, TheCard.class);

        assertNotNull(card);
        assertEquals("Alice Johnson", card.fn);
        assertEquals("Test note", card.note);

        assertNotNull(card.n);
        assertEquals("Alice", card.n.given);
        assertEquals("Johnson", card.n.surname);

        assertNotNull(card.org);
        assertEquals("Test Company", card.org.fn);
        assertEquals("Engineer", card.org.title);

        assertNotNull(card.bday);
        assertEquals(Integer.valueOf(1990), card.bday.y);
        assertEquals(Integer.valueOf(5), card.bday.m);
        assertEquals(Integer.valueOf(15), card.bday.d);

        assertNotNull(card.photo);
        assertEquals("jpeg", card.photo.type);
        assertEquals("http://example.com/photo.jpg", card.photo.ref);

        assertNotNull(card.comm);
        assertEquals(2, card.comm.length);
        assertEquals(TheCard.CommProto.TEL, card.comm[0].proto);
        assertEquals("+15551234567", card.comm[0].value);
        assertEquals(TheCard.CommProto.EMAIL, card.comm[1].proto);
        assertEquals("alice@example.com", card.comm[1].value);
    }

    @Test
    public void testJsonRoundTripPreservesData() throws Exception {
        TheCard original = new TheCard();
        original.fn = "Test User";
        original.note = "Important contact";
        original.addPhone("+15551234567", TheCard.CommDes.MOBILE);
        original.addEmail("test@example.com", TheCard.CommDes.WORK);
        original.addTinodeID("tinode:id/usr123", TheCard.CommDes.HOME);

        original.bday = new TheCard.Birthday();
        original.bday.m = 12;
        original.bday.d = 25;

        ObjectMapper mapper = Tinode.getJsonMapper();

        // Serialize
        String json = mapper.writeValueAsString(original);

        // Deserialize
        TheCard deserialized = mapper.readValue(json, TheCard.class);

        // Verify
        assertNotNull(deserialized);
        assertEquals(original.fn, deserialized.fn);
        assertEquals(original.note, deserialized.note);
        assertEquals(original.comm.length, deserialized.comm.length);
        assertEquals(original.bday.m, deserialized.bday.m);
        assertEquals(original.bday.d, deserialized.bday.d);
        assertNull(deserialized.bday.y);

        // Verify comm entries
        assertEquals(3, deserialized.comm.length);
        assertTrue(Arrays.stream(deserialized.comm)
                .anyMatch(e -> e.proto == TheCard.CommProto.TEL && e.value.equals("+15551234567")));
        assertTrue(Arrays.stream(deserialized.comm)
                .anyMatch(e -> e.proto == TheCard.CommProto.EMAIL && e.value.equals("test@example.com")));
        assertTrue(Arrays.stream(deserialized.comm)
                .anyMatch(e -> e.proto == TheCard.CommProto.TINODE && e.value.equals("tinode:id/usr123")));
    }

    @Test
    public void testJsonSerializeWithPhotoData() throws Exception {
        TheCard card = new TheCard();
        card.fn = "Photo User";
        card.photo = new TheCard.Photo();
        card.photo.type = "png";
        card.photo.data = new byte[]{1, 2, 3, 4, 5};
        card.photo.ref = Tinode.NULL_VALUE;

        ObjectMapper mapper = Tinode.getJsonMapper();
        String json = mapper.writeValueAsString(card);

        assertNotNull(json);
        assertTrue(json.contains("\"fn\":\"Photo User\""));
        assertTrue(json.contains("\"type\":\"png\""));
        // Base64 encoded [1,2,3,4,5] = "AQIDBAU="
        assertTrue(json.contains("\"data\":\"AQIDBAU=\""));
    }

    @Test
    public void testJsonDeserializeWithPhotoData() throws Exception {
        // Base64 for [1,2,3,4,5] is "AQIDBAU="
        String json = """
                {
                  "fn": "Photo User",
                  "photo": {
                    "type": "png",
                    "data": "AQIDBAU="
                  }
                }
                """;

        ObjectMapper mapper = Tinode.getJsonMapper();
        TheCard card = mapper.readValue(json, TheCard.class);

        assertNotNull(card);
        assertEquals("Photo User", card.fn);
        assertNotNull(card.photo);
        assertEquals("png", card.photo.type);
        assertArrayEquals(new byte[]{1, 2, 3, 4, 5}, card.photo.data);
    }

    @Test
    public void testJsonSerializeCommWithMultipleDesignators() throws Exception {
        TheCard card = new TheCard();
        card.fn = "Test";
        card.comm = new TheCard.CommEntry[]{
                new TheCard.CommEntry(TheCard.CommProto.TEL,
                        new TheCard.CommDes[]{TheCard.CommDes.WORK, TheCard.CommDes.VOICE, TheCard.CommDes.PREF},
                        "555-1234")
        };

        ObjectMapper mapper = Tinode.getJsonMapper();
        String json = mapper.writeValueAsString(card);

        assertNotNull(json);
        assertTrue(json.contains("\"proto\":\"tel\""));
        assertTrue(json.contains("\"work\""));
        assertTrue(json.contains("\"voice\""));
        assertTrue(json.contains("\"pref\""));
        assertTrue(json.contains("\"555-1234\""));
    }

    @Test
    public void testJsonDeserializeCommWithMultipleDesignators() throws Exception {
        String json = """
                {
                  "fn": "Test",
                  "comm": [
                    {
                      "proto": "tel",
                      "des": ["work", "voice", "pref"],
                      "value": "555-1234"
                    }
                  ]
                }
                """;

        ObjectMapper mapper = Tinode.getJsonMapper();
        TheCard card = mapper.readValue(json, TheCard.class);

        assertNotNull(card);
        assertEquals(1, card.comm.length);
        assertEquals(TheCard.CommProto.TEL, card.comm[0].proto);
        assertEquals(3, card.comm[0].des.length);
        assertTrue(Arrays.asList(card.comm[0].des).contains(TheCard.CommDes.WORK));
        assertTrue(Arrays.asList(card.comm[0].des).contains(TheCard.CommDes.VOICE));
        assertTrue(Arrays.asList(card.comm[0].des).contains(TheCard.CommDes.PREF));
        assertEquals("555-1234", card.comm[0].value);
    }

    @Test
    public void testJsonExcludesNullFieldsDueToNonDefault() throws Exception {
        TheCard card = new TheCard();
        card.fn = "Only Name";
        // All other fields are null

        ObjectMapper mapper = Tinode.getJsonMapper();
        String json = mapper.writeValueAsString(card);

        assertNotNull(json);
        assertTrue(json.contains("\"fn\":\"Only Name\""));
        // Due to @JsonInclude(NON_DEFAULT), null fields should not be included
        assertFalse(json.contains("\"note\""));
        assertFalse(json.contains("\"n\""));
        assertFalse(json.contains("\"org\""));
        assertFalse(json.contains("\"photo\""));
        assertFalse(json.contains("\"bday\""));
        assertFalse(json.contains("\"comm\""));
    }

    @Test
    public void testJsonMultipleRoundTrips() throws Exception {
        TheCard original = new TheCard();
        original.fn = "Round Trip Test";
        original.addPhone("123-456-7890", TheCard.CommDes.MOBILE);
        original.bday = new TheCard.Birthday();
        original.bday.y = 1995;
        original.bday.m = 7;
        original.bday.d = 20;

        ObjectMapper mapper = Tinode.getJsonMapper();

        // First round trip
        String json1 = mapper.writeValueAsString(original);
        TheCard card1 = mapper.readValue(json1, TheCard.class);

        // Second round trip
        String json2 = mapper.writeValueAsString(card1);
        TheCard card2 = mapper.readValue(json2, TheCard.class);

        // Third round trip
        String json3 = mapper.writeValueAsString(card2);
        TheCard card3 = mapper.readValue(json3, TheCard.class);

        // Verify data integrity after multiple round trips
        assertEquals(original.fn, card3.fn);
        assertEquals(original.comm.length, card3.comm.length);
        assertEquals(original.comm[0].value, card3.comm[0].value);
        assertEquals(original.bday.y, card3.bday.y);
        assertEquals(original.bday.m, card3.bday.m);
        assertEquals(original.bday.d, card3.bday.d);
    }

    @Test
    public void testJsonDeserializePartialBirthday() throws Exception {
        String json = """
                {
                  "fn": "Birthday Test",
                  "bday": {
                    "m": 3,
                    "d": 14
                  }
                }
                """;

        ObjectMapper mapper = Tinode.getJsonMapper();
        TheCard card = mapper.readValue(json, TheCard.class);

        assertNotNull(card);
        assertNotNull(card.bday);
        assertNull(card.bday.y);
        assertEquals(Integer.valueOf(3), card.bday.m);
        assertEquals(Integer.valueOf(14), card.bday.d);
    }

    @Test
    public void testJsonSerializeAllCommProtocols() throws Exception {
        TheCard card = new TheCard();
        card.fn = "All Protocols";
        card.comm = new TheCard.CommEntry[]{
                new TheCard.CommEntry(TheCard.CommProto.TEL,
                        new TheCard.CommDes[]{TheCard.CommDes.MOBILE}, "555-1111"),
                new TheCard.CommEntry(TheCard.CommProto.EMAIL,
                        new TheCard.CommDes[]{TheCard.CommDes.WORK}, "work@test.com"),
                new TheCard.CommEntry(TheCard.CommProto.TINODE,
                        new TheCard.CommDes[]{TheCard.CommDes.HOME}, "tinode:id/usr1"),
                new TheCard.CommEntry(TheCard.CommProto.IMPP,
                        new TheCard.CommDes[]{TheCard.CommDes.PERSONAL}, "xmpp:user@server.com"),
                new TheCard.CommEntry(TheCard.CommProto.HTTP,
                        new TheCard.CommDes[]{TheCard.CommDes.WORK}, "https://website.com")
        };

        ObjectMapper mapper = Tinode.getJsonMapper();
        String json = mapper.writeValueAsString(card);

        assertNotNull(json);
        assertTrue(json.contains("\"proto\":\"tel\""));
        assertTrue(json.contains("\"proto\":\"email\""));
        assertTrue(json.contains("\"proto\":\"tinode\""));
        assertTrue(json.contains("\"proto\":\"impp\""));
        assertTrue(json.contains("\"proto\":\"http\""));
    }

    @Test
    public void testJsonDeserializeAllCommProtocols() throws Exception {
        String json = """
                {
                  "fn": "All Protocols",
                  "comm": [
                    {"proto": "tel", "des": ["mobile"], "value": "555-1111"},
                    {"proto": "email", "des": ["work"], "value": "work@test.com"},
                    {"proto": "tinode", "des": ["home"], "value": "tinode:id/usr1"},
                    {"proto": "impp", "des": ["personal"], "value": "xmpp:user@server.com"},
                    {"proto": "http", "des": ["work"], "value": "https://website.com"}
                  ]
                }
                """;

        ObjectMapper mapper = Tinode.getJsonMapper();
        TheCard card = mapper.readValue(json, TheCard.class);

        assertNotNull(card);
        assertEquals(5, card.comm.length);
        assertEquals(TheCard.CommProto.TEL, card.comm[0].proto);
        assertEquals(TheCard.CommProto.EMAIL, card.comm[1].proto);
        assertEquals(TheCard.CommProto.TINODE, card.comm[2].proto);
        assertEquals(TheCard.CommProto.IMPP, card.comm[3].proto);
        assertEquals(TheCard.CommProto.HTTP, card.comm[4].proto);
    }

    @Test
    public void testJsonEnumsSerializeAsLowercase() throws Exception {
        TheCard card = new TheCard();
        card.fn = "Test";
        card.comm = new TheCard.CommEntry[]{
                new TheCard.CommEntry(TheCard.CommProto.TEL,
                        new TheCard.CommDes[]{TheCard.CommDes.MOBILE, TheCard.CommDes.WORK}, "555-1234")
        };

        ObjectMapper mapper = Tinode.getJsonMapper();
        String json = mapper.writeValueAsString(card);

        // Verify proto and des are lowercase
        assertTrue(json.contains("\"proto\":\"tel\""));
        assertTrue(json.contains("\"mobile\""));
        assertTrue(json.contains("\"work\""));
        // Verify they are NOT uppercase
        assertFalse(json.contains("\"proto\":\"TEL\""));
        assertFalse(json.contains("\"MOBILE\""));
        assertFalse(json.contains("\"WORK\""));
    }

    @Test
    public void testJsonEnumsDeserializeUppercaseBackwardCompatibility() throws Exception {
        // Test that uppercase enum values from older JSON still work
        String jsonUppercase = """
                {
                  "fn": "Test",
                  "comm": [
                    {"proto": "TEL", "des": ["MOBILE", "WORK"], "value": "555-1234"},
                    {"proto": "EMAIL", "des": ["HOME"], "value": "test@test.com"}
                  ]
                }
                """;

        ObjectMapper mapper = Tinode.getJsonMapper();
        TheCard card = mapper.readValue(jsonUppercase, TheCard.class);

        assertNotNull(card);
        assertEquals(2, card.comm.length);
        assertEquals(TheCard.CommProto.TEL, card.comm[0].proto);
        assertEquals(TheCard.CommProto.EMAIL, card.comm[1].proto);
        assertEquals(2, card.comm[0].des.length);
        assertTrue(Arrays.asList(card.comm[0].des).contains(TheCard.CommDes.MOBILE));
        assertTrue(Arrays.asList(card.comm[0].des).contains(TheCard.CommDes.WORK));
    }

    @Test
    public void testJsonEnumsDeserializeLowercase() throws Exception {
        // Test lowercase enum values (new format)
        String jsonLowercase = """
                {
                  "fn": "Test",
                  "comm": [
                    {"proto": "tel", "des": ["mobile", "work"], "value": "555-1234"},
                    {"proto": "email", "des": ["home"], "value": "test@test.com"}
                  ]
                }
                """;

        ObjectMapper mapper = Tinode.getJsonMapper();
        TheCard card = mapper.readValue(jsonLowercase, TheCard.class);

        assertNotNull(card);
        assertEquals(2, card.comm.length);
        assertEquals(TheCard.CommProto.TEL, card.comm[0].proto);
        assertEquals(TheCard.CommProto.EMAIL, card.comm[1].proto);
        assertEquals(2, card.comm[0].des.length);
        assertTrue(Arrays.asList(card.comm[0].des).contains(TheCard.CommDes.MOBILE));
        assertTrue(Arrays.asList(card.comm[0].des).contains(TheCard.CommDes.WORK));
    }

    @Test
    public void testJsonEnumsDeserializeMixedCase() throws Exception {
        // Test that mixed case still works (case-insensitive)
        String jsonMixed = """
                {
                  "fn": "Test",
                  "comm": [
                    {"proto": "Tel", "des": ["MoBiLe"], "value": "555-1234"}
                  ]
                }
                """;

        ObjectMapper mapper = Tinode.getJsonMapper();
        TheCard card = mapper.readValue(jsonMixed, TheCard.class);

        assertNotNull(card);
        assertEquals(1, card.comm.length);
        assertEquals(TheCard.CommProto.TEL, card.comm[0].proto);
        assertEquals(1, card.comm[0].des.length);
        assertEquals(TheCard.CommDes.MOBILE, card.comm[0].des[0]);
    }

    @Test
    public void testJsonEnumsUnknownProtocolDefaultsToIMPP() throws Exception {
        String json = """
                {
                  "fn": "Test",
                  "comm": [
                    {"proto": "unknown_proto", "des": ["home"], "value": "test"}
                  ]
                }
                """;

        ObjectMapper mapper = Tinode.getJsonMapper();
        TheCard card = mapper.readValue(json, TheCard.class);

        assertNotNull(card);
        assertEquals(1, card.comm.length);
        // Unknown protocol should default to IMPP
        assertEquals(TheCard.CommProto.UNDEFINED, card.comm[0].proto);
    }

    @Test
    public void testJsonEnumsUnknownDesignatorDefaultsToOTHER() throws Exception {
        String json = """
                {
                  "fn": "Test",
                  "comm": [
                    {"proto": "tel", "des": ["unknown_designator"], "value": "555-1234"}
                  ]
                }
                """;

        ObjectMapper mapper = Tinode.getJsonMapper();
        TheCard card = mapper.readValue(json, TheCard.class);

        assertNotNull(card);
        assertEquals(1, card.comm.length);
        assertEquals(1, card.comm[0].des.length);
        // Unknown designator should default to OTHER
        assertEquals(TheCard.CommDes.OTHER, card.comm[0].des[0]);
    }

    // JSON String Roundtrip Tests - ensuring JSON string → TheCard → JSON string preserves structure

    @Test
    public void testJsonStringRoundtripComplexContactCard() throws Exception {
        String originalJson = """
                {
                  "fn": "Dr. Sarah Johnson",
                  "n": {
                    "surname": "Johnson",
                    "given": "Sarah",
                    "prefix": "Dr."
                  },
                  "org": {
                    "fn": "Research Institute",
                    "title": "Senior Scientist"
                  },
                  "photo": {
                    "type": "jpeg",
                    "ref": "https://example.com/photo.jpg",
                    "width": 256,
                    "height": 256
                  },
                  "bday": {
                    "y": 1985,
                    "m": 11,
                    "d": 23
                  },
                  "note": "Lead researcher",
                  "comm": [
                    {
                      "proto": "tel",
                      "des": ["work", "voice"],
                      "value": "+1-555-123-4567"
                    },
                    {
                      "proto": "tel",
                      "des": ["work", "fax"],
                      "value": "+1-555-123-7654"
                    },
                    {
                      "proto": "email",
                      "des": ["work"],
                      "value": "sarah@research.org"
                    },
                    {
                      "proto": "email",
                      "des": ["home"],
                      "value": "sarah@example.com"
                    },
                    {
                      "proto": "tinode",
                      "des": ["work"],
                      "value": "tinode:id/usr_sarah"
                    }
                  ]
                }
                """;

        ObjectMapper mapper = Tinode.getJsonMapper();

        // Parse original JSON to normalized format (removes whitespace)
        Object originalParsed = mapper.readValue(originalJson, Object.class);
        String normalizedOriginal = mapper.writeValueAsString(originalParsed);

        // Convert JSON string → TheCard → JSON string
        @SuppressWarnings("unchecked")
        Map<String, Object> jsonMap = mapper.readValue(originalJson, Map.class);
        TheCard card = new TheCard(jsonMap);
        String reconstructedJson = mapper.writeValueAsString(card);

        // Compare the JSON strings (both should be normalized)
        assertEquals("JSON roundtrip failed - strings don't match", normalizedOriginal, reconstructedJson);
    }

    @Test
    public void testJsonStringRoundtripWithBase64Photo() throws Exception {
        String originalJson = """
                {
                  "fn": "Photo User",
                  "photo": {
                    "type": "png",
                    "data": "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChAGAWA8jMAAAAABJRU5ErkJggg=="
                  },
                  "comm": [
                    {
                      "proto": "email",
                      "des": ["home"],
                      "value": "photo@test.com"
                    }
                  ]
                }
                """;

        ObjectMapper mapper = Tinode.getJsonMapper();

        // Parse original JSON to normalized format
        Object originalParsed = mapper.readValue(originalJson, Object.class);
        String normalizedOriginal = mapper.writeValueAsString(originalParsed);

        // Convert JSON string → TheCard → JSON string
        @SuppressWarnings("unchecked")
        Map<String, Object> jsonMap = mapper.readValue(originalJson, Map.class);
        TheCard card = new TheCard(jsonMap);
        String reconstructedJson = mapper.writeValueAsString(card);

        // Compare the JSON strings
        assertEquals("Base64 photo JSON roundtrip failed", normalizedOriginal, reconstructedJson);
    }

    @Test
    public void testJsonStringRoundtripPartialBirthday() throws Exception {
        String originalJson = """
                {
                  "fn": "Birthday Test",
                  "bday": {
                    "m": 12,
                    "d": 25
                  },
                  "note": "Christmas birthday"
                }
                """;

        ObjectMapper mapper = Tinode.getJsonMapper();

        // Parse original JSON to normalized format
        Object originalParsed = mapper.readValue(originalJson, Object.class);
        String normalizedOriginal = mapper.writeValueAsString(originalParsed);

        // Convert JSON string → TheCard → JSON string
        @SuppressWarnings("unchecked")
        Map<String, Object> jsonMap = mapper.readValue(originalJson, Map.class);
        TheCard card = new TheCard(jsonMap);
        String reconstructedJson = mapper.writeValueAsString(card);

        // Compare the JSON strings
        assertEquals("Partial birthday JSON roundtrip failed", normalizedOriginal, reconstructedJson);
    }

    @Test
    public void testJsonStringRoundtripMinimal() throws Exception {
        String originalJson = """
                {
                  "fn": "Minimal User"
                }
                """;

        ObjectMapper mapper = Tinode.getJsonMapper();

        // Parse original JSON to normalized format
        Object originalParsed = mapper.readValue(originalJson, Object.class);
        String normalizedOriginal = mapper.writeValueAsString(originalParsed);

        // Convert JSON string → TheCard → JSON string
        @SuppressWarnings("unchecked")
        Map<String, Object> jsonMap = mapper.readValue(originalJson, Map.class);
        TheCard card = new TheCard(jsonMap);
        String reconstructedJson = mapper.writeValueAsString(card);

        // Compare the JSON strings
        assertEquals("Minimal JSON roundtrip failed", normalizedOriginal, reconstructedJson);
    }
    */
}

