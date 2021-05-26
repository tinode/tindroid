package co.tinode.tinodesdk.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.Arrays;

import co.tinode.tinodesdk.Tinode;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_DEFAULT;

@JsonInclude(NON_DEFAULT)
public class TheCard implements Serializable, Mergeable {

    public final static String TYPE_HOME = "HOME";
    public final static String TYPE_WORK = "WORK";
    public final static String TYPE_MOBILE = "MOBILE";
    public final static String TYPE_PERSONAL = "PERSONAL";
    public final static String TYPE_BUSINESS = "BUSINESS";
    public final static String TYPE_OTHER = "OTHER";
    // Full name
    public String fn;
    public Name n;
    public Organization org;
    // List of phone numbers associated with the contact.
    public Contact[] tel;
    // List of contact's email addresses.
    public Contact[] email;
    // All other communication options.
    public Contact[] comm;
    // Avatar photo. Pure java does not have a useful bitmap class, so keeping it as bits here.
    public Photo photo;
    // Birthday.
    public Birthday bday;
    // Free-form note.
    public String note;

    public TheCard() {
    }

    public TheCard(String fullName, byte[] avatarBits, String avatarImageType) {
        this.fn = fullName;
        if (avatarBits != null) {
            this.photo = new Photo(avatarBits, avatarImageType);
        }
    }

    public TheCard(String fullName, String avatarRef, String avatarImageType) {
        this.fn = fullName;
        if (avatarRef != null) {
            this.photo = new Photo(avatarRef, avatarImageType);
        }
    }

    private static String typeToString(ContactType tp) {
        if (tp == null) {
            return null;
        }

        switch (tp) {
            case HOME:
                return TYPE_HOME;
            case WORK:
                return TYPE_WORK;
            case MOBILE:
                return TYPE_MOBILE;
            case PERSONAL:
                return TYPE_PERSONAL;
            case BUSINESS:
                return TYPE_BUSINESS;
            default:
                return TYPE_OTHER;
        }
    }

    private static ContactType stringToType(String str) {
        if (str == null) {
            return null;
        }

        switch (str) {
            case TYPE_HOME:
                return ContactType.HOME;
            case TYPE_WORK:
                return ContactType.WORK;
            case TYPE_MOBILE:
                return ContactType.MOBILE;
            case TYPE_PERSONAL:
                return ContactType.PERSONAL;
            case TYPE_BUSINESS:
                return ContactType.BUSINESS;
            default:
                return ContactType.OTHER;
        }
    }

    private static boolean merge(@NotNull Field[] fields, @NotNull Mergeable dst, Mergeable src) {
        boolean updated = false;

        if (src == null) {
            return updated;
        }

        try {
            for (Field f : fields) {
                Object sf = f.get(src);
                Object df = f.get(dst);
                if (sf != null) {
                    // TODO: handle Collection / Array types.
                    // Source is provided.
                    if (df == null) {
                        // Destination is null, use source.
                        f.set(dst, sf);
                        updated = true;
                    } else if (df instanceof Mergeable) {
                        // Complex mergeable types, use merge().
                        updated = ((Mergeable) df).merge((Mergeable) sf) || updated;
                    } else if (!sf.equals(df)) {
                        if (sf instanceof String) {
                            // String, check for Tinode NULL.
                            f.set(dst, !Tinode.isNull(sf) ? sf : null);
                        } else {
                            // All other non-mergeable types: replace.
                            f.set(dst, sf);
                        }
                        updated = true;
                    }
                }
            }
        } catch (IllegalAccessException ignored) {
        }

        return updated;
    }

    @JsonIgnore
    public byte[] getPhotoBits() {
        return photo == null ? null : photo.data;
    }
    @JsonIgnore
    public String getPhotoRef() {
        return photo == null ? null : photo.ref;
    }
    @JsonIgnore
    public String getPhotoType() {
        return photo == null ? null : photo.type;
    }
    @JsonIgnore
    public void setPhotoBits(byte[] bits, String type) {
        photo = new Photo(bits, type);
    }
    @JsonIgnore
    public void setPhotoRef(String ref, String type) {
        photo = new Photo(ref, type);
    }

    public void addPhone(String phone, String type) {
        tel = Contact.append(tel, new Contact(type, phone));
    }

    public void addEmail(String addr, String type) {
        email = Contact.append(email, new Contact(type, addr));
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

    public static <T extends TheCard> T copy(T dst, TheCard src) {
        dst.fn = src.fn;
        dst.n = src.n != null ? src.n.copy() : null;
        dst.org = src.org != null ? src.org.copy() : null;
        dst.tel = Contact.copyArray(src.tel);
        dst.email = Contact.copyArray(src.email);
        dst.comm = Contact.copyArray(src.comm);
        // Shallow copy of the photo
        dst.photo = src.photo != null ? src.photo.copy() : null;

        return dst;
    }

    public TheCard copy() {
        return copy(new TheCard(), this);
    }

    @Override
    public boolean merge(Mergeable another) {
        if (!(another instanceof TheCard)) {
            return false;
        }
        return merge(this.getClass().getFields(), this, another);
    }

    public static class Name implements Serializable, Mergeable {
        public String surname;
        public String given;
        public String additional;
        public String prefix;
        public String suffix;

        public Name copy() {
            Name dst = new Name();
            dst.surname = surname;
            dst.given = given;
            dst.additional = additional;
            dst.prefix = prefix;
            dst.suffix = suffix;
            return dst;
        }

        @Override
        public boolean merge(Mergeable another) {
            if (!(another instanceof Name)) {
                return false;
            }
            return TheCard.merge(this.getClass().getFields(), this, another);
        }
    }

    public static class Organization implements Serializable, Mergeable {
        public String fn;
        public String title;

        public Organization copy() {
            Organization dst = new Organization();
            dst.fn = fn;
            dst.title = title;
            return dst;
        }

        @Override
        public boolean merge(Mergeable another) {
            if (!(another instanceof Organization)) {
                return false;
            }
            return TheCard.merge(this.getClass().getFields(), this, another);
        }
    }

    public static class Contact implements Serializable, Comparable<Contact>, Mergeable {
        public String type;
        public String name;
        public String uri;

        private ContactType tp;

        public Contact(String type, String uri) {
            this(type, null, uri);
        }

        public Contact(String type, String name, String uri) {
            this.type = type;
            this.name = name;
            this.uri = uri;
            this.tp = stringToType(type);
        }

        @JsonIgnore
        public ContactType getType() {
            if (tp != null) {
                return tp;
            }
            return stringToType(type);
        }

        public Contact copy() {
            return new Contact(type, name, uri);
        }

        static Contact[] copyArray(Contact[] src) {
            Contact[] dst = null;
            if (src != null) {
                dst = Arrays.copyOf(src, src.length);
                for (int i=0; i<src.length;i++) {
                    dst[i] = src[i].copy();
                }
            }
            return dst;
        }

        public static Contact[] append(Contact[] arr, Contact val) {
            int insertAt;
            if (arr == null) {
                arr = new Contact[1];
                arr[0] = val;
            } else if ((insertAt = Arrays.binarySearch(arr, val)) >=0) {
                if (!TYPE_OTHER.equals(val.type)) {
                    arr[insertAt].type = val.type;
                    arr[insertAt].tp = stringToType(val.type);
                }
            } else {
                arr = Arrays.copyOf(arr, arr.length + 1);
                arr[arr.length - 1] = val;
            }

            Arrays.sort(arr);

            return arr;
        }

        @Override
        public int compareTo(Contact c) {
            return uri.compareTo(c.uri);
        }

        @NotNull
        @Override
        public String toString() {
            return type + ":" + uri;
        }

        @Override
        public boolean merge(Mergeable another) {
            if (!(another instanceof Contact)) {
                return false;
            }
            return TheCard.merge(this.getClass().getFields(), this, another);
        }
    }

    /**
     * Generic container for image data.
     */
    public static class Photo implements Serializable, Mergeable {
        public byte[] data;
        public String type;
        public String ref;
        public Integer width;
        public Integer height;
        public Integer size;

        public Photo() {}

        /**
         * The main constructor.
         *
         * @param bits binary image data
         * @param type the specific part of image/ mime type, i.e. 'jpeg' or 'png'.
         */
        public Photo(byte[] bits, String type) {
            this.data = bits;
            this.type = type;
        }

        /**
         * The main constructor.
         *
         * @param ref Uri of the image.
         * @param type the specific part of image/ mime type, i.e. 'jpeg' or 'png'.
         */
        public Photo(String ref, String type) {
            this.ref = ref;
            this.type = type;
        }

        /**
         * Creates a copy of a photo instance.
         * @return new instance of Photo.
         */
        public Photo copy() {
            Photo ret = new Photo();
            ret.data = data;
            ret.type = type;
            ret.ref = ref;
            ret.width = width;
            ret.height = height;
            ret.size = size;
            return ret;
        }

        @Override
        public boolean merge(Mergeable another) {
            if (!(another instanceof Photo)) {
                return false;
            }
            return TheCard.merge(this.getClass().getFields(), this, another);
        }
    }

    public static class Birthday implements Serializable, Mergeable {
        // Year like 1975
        Integer y;
        // Month 1..12.
        Integer m;
        // Day 1..31.
        Integer d;

        public Birthday() {}

        public Birthday copy() {
            Birthday ret = new Birthday();
            ret.y = y;
            ret.m = m;
            ret.d = d;
            return ret;
        }

        @Override
        public boolean merge(Mergeable another) {
            if (!(another instanceof Birthday)) {
                return false;
            }
            return TheCard.merge(this.getClass().getFields(), this, another);
        }
    }
}

