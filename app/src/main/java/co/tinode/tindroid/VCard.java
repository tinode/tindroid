package co.tinode.tindroid;

import android.graphics.Bitmap;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.Arrays;

/**
 * VCard - contact descriptor.
 */
public class VCard {
    public final static String TYPE_HOME = "HOME";
    public final static String TYPE_WORK = "WORK";
    public final static String TYPE_MOBILE = "MOBILE";
    public final static String TYPE_PERSONAL = "PERSONAL";
    public final static String TYPE_BUSINESS = "BUSINESS";
    public final static String TYPE_OTHER = "OTHER";
    // Full name
    public String fn;
    public Name n;
    public String org;
    public String title;
    // List of phone numbers associated with the contact
    public Contact[] tel;
    // List of contact's email addresses
    public Contact[] email;
    public Contact[] impp;
    // Avatar photo
    public AvatarPhoto photo;
    public VCard() {
    }

    public VCard(String fullName, Bitmap avatar) {
        this.fn = fullName;
        this.photo = new AvatarPhoto(avatar);
    }

    public static String typeToString(ContactType tp) {
        String str = null;
        switch (tp) {
            case HOME:
                str = TYPE_HOME;
                break;
            case WORK:
                str = TYPE_WORK;
                break;
            case MOBILE:
                str = TYPE_MOBILE;
                break;
            case PERSONAL:
                str = TYPE_PERSONAL;
                break;
            case BUSINESS:
                str = TYPE_BUSINESS;
                break;
            case OTHER:
                str = TYPE_OTHER;
                break;
        }

        return str;
    }

    @JsonIgnore
    public Bitmap getBitmap() {
        return (photo != null) ? photo.getBitmap() : null;
    }

    public boolean constructBitmap() {
        return photo != null && photo.constructBitmap();
    }

    public void addPhone(String phone, ContactType type) {
        addPhone(phone, typeToString(type));
    }

    public void addPhone(String phone, String type) {
        if (tel == null) {
            tel = new Contact[1];
        } else {
            tel = Arrays.copyOf(tel, tel.length + 1);
        }
        tel[tel.length - 1] = new Contact(phone, type);
    }

    public void addEmail(String addr, String type) {
        if (email == null) {
            email = new Contact[1];
        } else {
            email = Arrays.copyOf(email, email.length + 1);
        }
        email[email.length - 1] = new Contact(addr, type);
    }

    @JsonIgnore
    public String getPhoneByType(String type) {
        String phone = null;
        if (tel != null) {
            for (Contact tt : tel) {
                if (tt.type != null && tt.type.equals(type)) {
                    phone = tt.uri;
                    break;
                }
            }
        }
        return phone;
    }

    @JsonIgnore
    public String getPhoneByType(ContactType type) {
        return getPhoneByType(typeToString(type));
    }

    public enum ContactType {HOME, WORK, MOBILE, PERSONAL, BUSINESS, OTHER}

    public static class Name {
        public String surname;
        public String given;
        public String additional;
        public String prefix;
        public String suffix;
    }

    public static class Contact {
        public String type;
        public String uri;

        public Contact(String type, String uri) {
            this.type = type;
            this.uri = uri;
        }
    }
}
