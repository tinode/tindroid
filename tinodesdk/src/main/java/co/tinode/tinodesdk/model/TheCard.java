package co.tinode.tinodesdk.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import co.tinode.tinodesdk.Tinode;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_DEFAULT;

/**
 * Definition of a contact card, similar to vCard.
 * TheCard class represents a contact card with various fields such as full name,
 * photo, organization, communication methods, and birthday.
 *
 * @copyright 2025-2026 Tinode LLC.
 */
@JsonInclude(NON_DEFAULT)
public class TheCard implements Serializable, Mergeable {

    public static final String CONTENT_TYPE = "text/x-the-card";

    public enum CommDes {
        HOME, WORK, PREF, MOBILE, PERSONAL, BUSINESS, VOICE, VIDEO, FAX, OTHER;

        @JsonValue
        public String toValue() {
            return name().toLowerCase();
        }

        @JsonCreator
        public static CommDes fromValue(String value) {
            if (value == null) {
                return null;
            }
            try {
                return valueOf(value.toUpperCase());
            } catch (IllegalArgumentException e) {
                return OTHER;
            }
        }
    }

    public enum CommProto {
        TEL, EMAIL, TINODE, IMPP, HTTP, UNDEFINED;

        @JsonValue
        public String toValue() {
            return name().toLowerCase();
        }

        @JsonCreator
        public static CommProto fromValue(String value) {
            if (value == null) {
                return null;
            }
            try {
                return valueOf(value.toUpperCase());
            } catch (IllegalArgumentException e) {
                return UNDEFINED;
            }
        }
    }

    // Full formatted name
    public String fn;
    // Name components.
    public Name n;
    public Organization org;
    // Avatar photo.
    public Photo photo;
    // Birthday.
    public Birthday bday;
    // Free-form description.
    public String note;
    // Communication channels - unified array for all communication methods
    public CommEntry[] comm;

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

    public TheCard(Map<String, Object> json) {
        if (json == null) {
            return;
        }

        // Full name
        Object fnObj = json.get("fn");
        if (fnObj instanceof String str) {
            this.fn = str;
        }

        // Name components
        Object nObj = json.get("n");
        if (nObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> nMap = (Map<String, Object>) nObj;
            Name name = new Name();
            if (nMap.get("surname") instanceof String surname) {
                name.surname = surname;
            }
            if (nMap.get("given") instanceof String given) {
                name.given = given;
            }
            if (nMap.get("additional") instanceof String additional) {
                name.additional = additional;
            }
            if (nMap.get("prefix") instanceof String prefix) {
                name.prefix = prefix;
            }
            if (nMap.get("suffix") instanceof String suffix) {
                name.suffix = suffix;
            }
            this.n = name;
        }

        // Organization
        Object orgObj = json.get("org");
        if (orgObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> orgMap = (Map<String, Object>) orgObj;
            Organization org = new Organization();
            if (orgMap.get("fn") instanceof String orgName) {
                org.fn = orgName;
            }
            if (orgMap.get("title") instanceof String title) {
                org.title = title;
            }
            this.org = org;
        }

        // Photo
        if (json.get("photo") instanceof Map map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> photoMap = (Map<String, Object>) map;
            Photo photo = new Photo();

            // Handle binary data (may be base64 string or byte array)
            Object dataObj = photoMap.get("data");
            if (photoMap.get("data") instanceof String) {
                try {
                    photo.data = Base64.getDecoder().decode((String) dataObj);
                } catch (IllegalArgumentException e) {
                    // Invalid base64, skip
                    photo.data = null;
                }
            } else if (dataObj instanceof byte[]) {
                photo.data = (byte[]) dataObj;
            }

            // Type (e.g., 'png', 'jpeg')
            if (photoMap.get("type") instanceof String typeStr) {
                if (typeStr.startsWith("image/")) {
                    photo.type = typeStr.substring(6);
                } else {
                    photo.type = typeStr;
                }
            }

            // Reference URL
            if (photoMap.get("ref") instanceof String ref) {
                photo.ref = ref;
            }

            // Width
            Object widthObj = photoMap.get("width");
            if (widthObj instanceof Integer) {
                photo.width = (Integer) widthObj;
            } else if (widthObj instanceof Number) {
                photo.width = ((Number) widthObj).intValue();
            }

            // Height
            Object heightObj = photoMap.get("height");
            if (heightObj instanceof Integer) {
                photo.height = (Integer) heightObj;
            } else if (heightObj instanceof Number) {
                photo.height = ((Number) heightObj).intValue();
            }

            // Size
            Object sizeObj = photoMap.get("size");
            if (sizeObj instanceof Integer) {
                photo.size = (Integer) sizeObj;
            } else if (sizeObj instanceof Number) {
                photo.size = ((Number) sizeObj).intValue();
            }

            this.photo = photo;
        }

        // Birthday
        if (json.get("bday") instanceof Map map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> bdayMap = (Map<String, Object>) map;
            Birthday bday = new Birthday();

            Object yObj = bdayMap.get("y");
            if (yObj instanceof Integer) {
                bday.y = (Integer) yObj;
            } else if (yObj instanceof Number) {
                bday.y = ((Number) yObj).intValue();
            }

            Object mObj = bdayMap.get("m");
            if (mObj instanceof Integer) {
                bday.m = (Integer) mObj;
            } else if (mObj instanceof Number) {
                bday.m = ((Number) mObj).intValue();
            }

            Object dObj = bdayMap.get("d");
            if (dObj instanceof Integer) {
                bday.d = (Integer) dObj;
            } else if (dObj instanceof Number) {
                bday.d = ((Number) dObj).intValue();
            }

            this.bday = bday;
        }

        // Note
        if (json.get("note") instanceof String newNote) {
            this.note = newNote;
        }

        // Communication entries
        Object commObj = json.get("comm");
        if (commObj instanceof List) {
            @SuppressWarnings("unchecked")
            List<Object> commList = (List<Object>) commObj;
            List<CommEntry> commEntries = new ArrayList<>();

            for (Object entry : commList) {
                if (entry instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> commMap = (Map<String, Object>) entry;
                    CommEntry commEntry = new CommEntry();

                    // Protocol
                    if (commMap.get("proto") instanceof String protoStr) {
                        commEntry.proto = CommProto.fromValue(protoStr);
                    }

                    // Value
                    if (commMap.get("value") instanceof String valueStr) {
                        commEntry.value = valueStr;
                    }

                    // Descriptors (can be array or single value)
                    Object desObj = commMap.get("des");
                    if (desObj instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<Object> desList = (List<Object>) desObj;
                        List<CommDes> desValues = new ArrayList<>();
                        for (Object des : desList) {
                            if (des instanceof String) {
                                CommDes desVal = CommDes.fromValue((String) des);
                                if (desVal != null) {
                                    desValues.add(desVal);
                                }
                            }
                        }
                        if (!desValues.isEmpty()) {
                            commEntry.des = desValues.toArray(new CommDes[0]);
                        }
                    } else if (desObj instanceof String) {
                        CommDes desVal = CommDes.fromValue((String) desObj);
                        if (desVal != null) {
                            commEntry.des = new CommDes[]{desVal};
                        }
                    }

                    if (commEntry.proto != null && commEntry.value != null) {
                        commEntries.add(commEntry);
                    }
                }
            }

            if (!commEntries.isEmpty()) {
                this.comm = commEntries.toArray(new CommEntry[0]);
            }
        } else if (commObj instanceof Object[] commArray) {
            // Handle array format as well
            List<CommEntry> commEntries = new ArrayList<>();
            for (Object entry : commArray) {
                if (entry instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> commMap = (Map<String, Object>) entry;
                    CommEntry commEntry = new CommEntry();

                    Object protoObj = commMap.get("proto");
                    if (protoObj instanceof String) {
                        commEntry.proto = CommProto.fromValue((String) protoObj);
                    }

                    Object valueObj = commMap.get("value");
                    if (valueObj instanceof String) {
                        commEntry.value = (String) valueObj;
                    }

                    Object desObj = commMap.get("des");
                    if (desObj instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<Object> desList = (List<Object>) desObj;
                        List<CommDes> desValues = new ArrayList<>();
                        for (Object des : desList) {
                            if (des instanceof String) {
                                CommDes desVal = CommDes.fromValue((String) des);
                                if (desVal != null) {
                                    desValues.add(desVal);
                                }
                            }
                        }
                        if (!desValues.isEmpty()) {
                            commEntry.des = desValues.toArray(new CommDes[0]);
                        }
                    } else if (desObj instanceof String) {
                        CommDes desVal = CommDes.fromValue((String) desObj);
                        if (desVal != null) {
                            commEntry.des = new CommDes[]{desVal};
                        }
                    }

                    if (commEntry.proto != null && commEntry.value != null) {
                        commEntries.add(commEntry);
                    }
                }
            }

            if (!commEntries.isEmpty()) {
                this.comm = commEntries.toArray(new CommEntry[0]);
            }
        }
    }

    /**
     * Merge another card into this one.
     * @param that Card object to merge.
     */
    public boolean merge(TheCard that) {
        if (that == null || that == this) {
            return false;
        }
        boolean changed = false;
        if (that.fn != null && !that.fn.equals(this.fn)) {
            this.fn = that.fn;
            changed = true;
        }
        if (this.n != null) {
            changed |= this.n.merge(that.n);
        } else {
            this.n = that.n;
            changed = true;
        }
        if (this.org != null) {
            changed |= this.org.merge(that.org);
        } else {
            this.org = that.org;
            changed = true;
        }
        if (that.comm != null) this.comm = that.comm;
        if (that.photo != null) this.photo = that.photo;
        if (this.bday != null) {
            changed |= this.bday.merge(that.bday);
        } else {
            this.bday = that.bday;
            changed = true;
        }
        if (that.note != null && !that.note.equals(this.note)) {
            this.note = that.note;
            changed = true;
        }
        return changed;
    }

    public static <T extends TheCard> T copy(T dst, TheCard src) {
        dst.fn = src.fn;
        dst.n = src.n != null ? src.n.copy() : null;
        dst.org = src.org != null ? src.org.copy() : null;
        dst.comm = CommEntry.copyArray(src.comm);
        // Shallow copy of the photo
        dst.photo = src.photo != null ? src.photo.copy() : null;

        return dst;
    }

    public TheCard copy() {
        return copy(new TheCard(), this);
    }

    /**
     * Get the size of the card in bytes when serialized as JSON.
     * @return Size in bytes.
     */
    @JsonIgnore
    public int getSize() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.writeValueAsString(this).length();
        } catch (JsonProcessingException e) {
            return 0;
        }
    }

    /**
     * Set the full name on a card.
     * @param fn Full name to set.
     * @return The updated card.
     */
    public TheCard setFn(String fn) {
        if (fn == null || fn.trim().isEmpty()) {
            this.fn = null;
        } else {
            this.fn = fn.trim();
        }
        return this;
    }

    /**
     * Get the full name from a card.
     * @return The full name or null if not set.
     */
    @Nullable
    public String getFn() {
        return fn;
    }

    @JsonIgnore
    public String getFirstName() {
        if (n != null) {
            return n.given;
        }
        return null;
    }

    @JsonIgnore
    public String getLastName() {
        if (n != null) {
            return n.surname;
        }
        return null;
    }
    /**
     * Set notes on a card.
     * @param note Notes to set.
     * @return The updated card.
     */
    public TheCard setNote(String note) {
        if (note == null) {
            this.note = null;
        } else {
            this.note = note.trim();
            if (this.note.isEmpty()) {
                this.note = null;
            }
        }
        return this;
    }

    public TheCard setPhoto(String ref, String mimeType) {
        if (ref == null) {
            this.photo = null;
        } else {
            String type = mimeType;
            if (type == null && ref.contains(".")) {
                // Infer from file extension
                String ext = ref.substring(ref.lastIndexOf('.') + 1).toLowerCase();
                if (ext.equals("jpg")) {
                    type = "jpeg";
                } else if (ext.matches("png|gif|bmp|webp|svg")) {
                    type = ext;
                } else {
                    type = "jpeg"; // Default
                }
            } else if (type != null && type.startsWith("image/")) {
                // Extract type from MIME type (e.g., "image/png" -> "png")
                type = type.substring(6);
            } else if (type == null) {
                type = "jpeg"; // Default
            }
            this.photo = new Photo(ref, type);
        }
        return this;
    }

    public TheCard setPhoto(byte[] bits, String mimeType) {
        if (bits == null || bits.length == 0) {
            this.photo = null;
        } else {
            // Extract type from MIME type (e.g., "image/png" -> "png")
            String type = mimeType;
            if (mimeType != null && mimeType.startsWith("image/")) {
                type = mimeType.substring(6);
            }
            this.photo = new Photo(bits, type);
        }
        return this;
    }

    /**
     * Get the photo data as a byte array.
     * @return Photo data or null if not set.
     */
    @JsonIgnore
    public byte[] getPhotoBits() {
        return photo == null ? null : photo.data;
    }
    @JsonIgnore
    public boolean isPhotoRef() {
        return photo != null && photo.ref != null;
    }
    @JsonIgnore
    public String getPhotoRef() {
        return photo != null ? photo.ref : null;
    }
    @JsonIgnore
    public String getPhotoMimeType() {
        return photo == null ? null : ("image/" + photo.type);
    }
    /**
     * @return Array with photo ref URL if available.
     */
    @JsonIgnore
    public String[] getPhotoRefs() {
        if (photo != null && photo.ref != null && !Tinode.isNull(photo.ref)) {
            return new String[] { photo.ref };
        }
        return null;
    }

    /**
     * Set a phone number on a card, replacing any existing phone with the same type.
     * @param phone Phone number.
     * @param type Type of phone number (e.g., 'voice', 'mobile', 'work').
     * @return The updated card.
     */
    @SuppressWarnings("UnusedReturnValue")
    @JsonIgnore
    public TheCard setPhone(String phone, CommDes type) {
        return addOrSetComm(this, CommProto.TEL, phone,
                type != null ? type : CommDes.VOICE, true);
    }

    /**
     * Set an email address on a card, replacing any existing email with the same type.
     * @param email Email address.
     * @param type Type of email (e.g., 'home', 'work').
     * @return The updated card.
     */
    @SuppressWarnings("UnusedReturnValue")
    @JsonIgnore
    public TheCard setEmail(String email, CommDes type) {
        return addOrSetComm(this, CommProto.EMAIL, email,
                type != null ? type : CommDes.HOME, true);
    }

    /**
     * Set a Tinode ID on a card, replacing any existing ID with the same type.
     * @param tinodeID Tinode user ID.
     * @param type Type of ID.
     * @return The updated card.
     */
    @SuppressWarnings("UnusedReturnValue")
    @JsonIgnore
    public TheCard setTinodeID(String tinodeID, CommDes type) {
        return addOrSetComm(this, CommProto.TINODE, tinodeID,
                type != null ? type : CommDes.HOME, true);
    }

    /**
     * Add a phone number to a card without replacing existing ones.
     * @param phone Phone number.
     * @param type Type of phone number.
     * @return The updated card.
     */
    public  TheCard addPhone(String phone, CommDes type) {
        return addOrSetComm(this, CommProto.TEL, phone,
                type != null ? type : CommDes.VOICE, false);
    }

    /**
     * Add an email address to a card without replacing existing ones.
     * @param email Email address.
     * @param type Type of email.
     * @return The updated card.
     */
    @SuppressWarnings("UnusedReturnValue")
    public TheCard addEmail(String email, CommDes type) {
        return addOrSetComm(this, CommProto.EMAIL,
                email, type != null ? type : CommDes.HOME, false);
    }

    /**
     * Add a Tinode ID to a card without replacing existing ones.
     * @param tinodeID Tinode user ID.
     * @param type Type of ID.
     * @return The updated card.
     */
    @SuppressWarnings("UnusedReturnValue")
    public TheCard addTinodeID(String tinodeID, CommDes type) {
        return addOrSetComm(this, CommProto.TINODE,
                tinodeID, type != null ? type : CommDes.HOME, false);
    }

    /**
     * Remove phone number(s) from a card.
     * @param phone Phone number to remove (optional).
     * @param type Type of phone to remove (optional).
     * @return The updated card.
     */
    @SuppressWarnings("UnusedReturnValue")
    public TheCard clearPhone(String phone, CommDes type) {
        return clearComm(this, CommProto.TEL, phone, type);
    }

    /**
     * Remove email address(es) from a card.
     * @param email Email to remove (optional).
     * @param type Type of email to remove (optional).
     * @return The updated card.
     */
    @SuppressWarnings("UnusedReturnValue")
    public TheCard clearEmail(String email, CommDes type) {
        return clearComm(this, CommProto.EMAIL, email, type);
    }

    /**
     * Remove Tinode ID(s) from a card.
     * @param tinodeID Tinode ID to remove (optional).
     * @param type Type of ID to remove (optional).
     * @return The updated card.
     */
    @SuppressWarnings("UnusedReturnValue")
    public TheCard clearTinodeID(String tinodeID, CommDes type) {
        return clearComm(this, CommProto.TINODE, tinodeID, type);
    }

    /**
     * Get all communication methods of a specific protocol from a card.
     * @param proto Protocol ('tel', 'email', 'tinode', 'http').
     * @return List of communication entries matching the protocol.
     */
    @JsonIgnore
    @NotNull
    public List<CommEntry> getComm(CommProto proto) {
        List<CommEntry> result = new ArrayList<>();
        if (comm != null) {
            for (CommEntry entry : comm) {
                if (entry.proto == proto) {
                    result.add(entry);
                }
            }
        }
        return result;
    }

    public CommEntry getComm(CommProto proto, CommDes des) {
        if (comm == null) {
            return null;
        }
        for (CommEntry entry : comm) {
            if (entry.proto == proto && Arrays.asList(entry.des).contains(des)) {
                return entry;
            }
        }
        return null;
    }

    /**
     * Get all email addresses from a card.
     * @return List of email addresses.
     */
    @JsonIgnore
    public List<CommEntry> getEmails() {
        return new ArrayList<>(getComm(CommProto.EMAIL));
    }

    /**
     * Get all phone numbers from a card.
     * @return List of phone numbers.
     */
    @JsonIgnore
    public List<CommEntry> getPhones() {
        return new ArrayList<>(getComm(CommProto.TEL));
    }

    // Extract 'value' from each member of array
    public static String[] getCommValues(List<CommEntry> comms) {
        String[] values = new String[comms.size()];
        for (int i=0; i<comms.size(); i++) {
            String val = comms.get(i).value;
            if (val == null) {
                continue;
            }
            val = val.replaceAll("\\s", "");
            if (!val.isEmpty()) {
                values[i] = val;
            }
        }
        return values;
    }
    /**
     * Get the first Tinode ID from a card.
     * @return The first Tinode ID or null if none.
     */
    @Nullable
    @JsonIgnore
    public String getFirstTinodeID() {
        List<CommEntry> comms = getComm(CommProto.TINODE);
        if (!comms.isEmpty()) {
            return comms.get(0).value;
        }
        return null;
    }

    public static String parseTinodeID(String tinodeID) {
        if (tinodeID == null) {
            return null;
        }
        if (tinodeID.startsWith("tinode:")) {
            String[] path = tinodeID.substring(7).trim()
                    .split("/");
            if (path.length == 0) {
                return null;
            }
            if (path.length == 1) {
                return path[0];
            }
            return path[path.length - 1];
        }
        return null;
    }

    /**
     * Add or set a communication entry.
     */
    private static TheCard addOrSetComm(TheCard card, CommProto proto, String value, CommDes type, boolean setOnly) {
        final String finalValue = value != null ? value.trim() : null;
        if (proto != null && finalValue != null && !finalValue.isEmpty()) {
            if (card == null) {
                card = new TheCard();
            }

            List<CommEntry> commList = card.comm != null ?
                    new ArrayList<>(Arrays.asList(card.comm)) : new ArrayList<>();

            if (setOnly) {
                // Remove existing entries with the same proto and type
                commList.removeIf(c -> c.proto == proto && Arrays.asList(c.des).contains(type));
            }

            CommEntry entry = new CommEntry();
            entry.proto = proto;
            entry.des = new CommDes[]{type};
            entry.value = finalValue;
            commList.add(entry);

            card.comm = commList.toArray(new CommEntry[0]);
        }
        return card;
    }

    /**
     * Clear communication entries.
     */
    private static TheCard clearComm(TheCard card, CommProto proto, String value, CommDes type) {
        if (card != null && card.comm != null) {
            List<CommEntry> commList = new ArrayList<>(Arrays.asList(card.comm));
            commList.removeIf(c -> {
                if (c.proto != proto) return false;
                if (value != null && !c.value.equals(value)) return false;
                if (type != null) {
                    return Arrays.asList(c.des).contains(type);
                }
                return true;
            });
            card.comm = commList.toArray(new CommEntry[0]);
        }
        return card;
    }

    /**
     * Check if a file type or name is a supported vCard format.
     * @param type MIME type of the file.
     * @param name File name.
     * @return True if the file is a vCard format.
     */
    public static boolean isFileSupported(String type, String name) {
        return "text/vcard".equals(type) || "text/x-vcard".equals(type) ||
               (name != null && (name.endsWith(".vcf") || name.endsWith(".vcard")));
    }

    @Override
    public boolean merge(Mergeable another) {
        if (!(another instanceof TheCard that)) {
            return false;
        }
        return merge(that);
    }

    // Nested Classes

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
            if (!(another instanceof Name that)) {
                return false;
            }

            surname = that.surname;
            given = that.given;
            additional = that.additional;
            prefix = that.prefix;
            suffix = that.suffix;
            return true;
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
            if (!(another instanceof Organization that)) {
                return false;
            }
            fn = that.fn;
            title = that.title;
            return true;
        }
    }

    /**
     * Communication entry for phone, email, etc.
     */
    public static class CommEntry implements Serializable, Mergeable {
        // Protocol: 'tel', 'email', 'tinode', 'http'
        public CommProto proto;
        // Descriptors/types like ['home'], ['work', 'voice']
        public CommDes[] des;
        // The actual value (phone number, email address, etc.)
        public String value;

        public CommEntry() {
        }

        public CommEntry(CommProto proto, CommDes[] des, String value) {
            this.proto = proto;
            this.des = des != null ? Arrays.copyOf(des, des.length) : null;
            this.value = value;
        }

        public CommEntry copy() {
            CommEntry copy = new CommEntry();
            copy.proto = proto;
            copy.des = des != null ? Arrays.copyOf(des, des.length) : null;
            copy.value = value;
            return copy;
        }

        @Override
        public boolean merge(Mergeable another) {
            if (!(another instanceof CommEntry that)) {
                return false;
            }
            proto = that.proto;
            des = that.des;
            value = that.value;
            return true;
        }

        static CommEntry[] copyArray(CommEntry[] src) {
            CommEntry[] dst = null;
            if (src != null) {
                dst = Arrays.copyOf(src, src.length);
                for (int i=0; i<src.length;i++) {
                    dst[i] = src[i].copy();
                }
            }
            return dst;
        }
    }

    /**
     * Generic container for image data.
     */
    public static class Photo implements Serializable, Mergeable {
        // Binary image data (decoded from base64)
        public byte[] data;
        // Second component of image mime type, i.e. 'png' for 'image/png'.
        public String type;
        // URL of the image or NULL_VALUE
        public String ref;
        // Intended dimensions of the full image
        public Integer width, height;
        // Size of the full image in bytes.
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
            if (type != null) {
                if (type.startsWith("image/")) {
                    type = type.substring(6);
                }
                this.type = type;
            }
            this.ref = Tinode.NULL_VALUE;
        }

        /**
         * The main constructor.
         *
         * @param ref Uri of the image.
         * @param type the specific part of image/ mime type, i.e. 'jpeg' or 'png'.
         */
        public Photo(String ref, String type) {
            this.ref = ref;
            if (type != null) {
                if (type.startsWith("image/")) {
                    type = type.substring(6);
                }
                this.type = type;
            }
            this.data = Tinode.NULL_BYTES;
        }

        public Photo copy() {
            Photo dst = new Photo();
            dst.data = data;
            dst.type = type;
            dst.ref = ref;
            dst.width = width;
            dst.height = height;
            dst.size = size;
            return dst;
        }

        @Override
        public boolean merge(Mergeable another) {
            if (!(another instanceof Photo that)) {
                return false;
            }

            // Direct copy. No need to check for nulls.
            data = that.data;
            type = that.type;
            ref  = that.ref;
            width = that.width;
            height = that.height;
            size = that.size;
            return true;
        }
    }

    public static class Birthday implements Serializable, Mergeable {
        // Year like 1975
        public Integer y;
        // Month 1..12.
        public Integer m;
        // Day 1..31.
        public Integer d;

        public Birthday() {}

        @Override
        public boolean merge(Mergeable another) {
            if (!(another instanceof Birthday that)) {
                return false;
            }

            y = that.y;
            m = that.m;
            d = that.d;
            return true;
        }
    }

    /**
     * Export a card as vCard 3.0 format string.
     * @param card The card object to export.
     * @return vCard formatted string or null if card is empty.
     */
    @Nullable
    public static String exportVCard(TheCard card) {
        if (card == null) {
            return null;
        }

        StringBuilder vcard = new StringBuilder("BEGIN:VCARD\r\nVERSION:3.0\r\n");

        if (card.fn != null) {
            vcard.append("FN:").append(card.fn).append("\r\n");
        }

        if (card.n != null) {
            vcard.append("N:")
                .append(card.n.surname != null ? card.n.surname : "").append(";")
                .append(card.n.given != null ? card.n.given : "").append(";")
                .append(card.n.additional != null ? card.n.additional : "").append(";")
                .append(card.n.prefix != null ? card.n.prefix : "").append(";")
                .append(card.n.suffix != null ? card.n.suffix : "")
                .append("\r\n");
        }

        if (card.org != null) {
            if (card.org.fn != null) {
                vcard.append("ORG:").append(card.org.fn).append("\r\n");
            }
            if (card.org.title != null) {
                vcard.append("TITLE:").append(card.org.title).append("\r\n");
            }
        }

        if (card.note != null && !Tinode.isNull(card.note)) {
            vcard.append("NOTE:").append(card.note).append("\r\n");
        }

        if (card.bday != null && card.bday.m != null && card.bday.d != null) {
            // Format as YYYY-MM-DD or --MM-DD if no year
            String year = card.bday.y != null ? String.format(Locale.US, "%04d", card.bday.y) : "--";
            String month = String.format(Locale.US, "%02d", card.bday.m);
            String day = String.format(Locale.US, "%02d", card.bday.d);
            vcard.append("BDAY:").append(year).append("-").append(month).append("-").append(day).append("\r\n");
        }

        if (card.photo != null) {
            if (card.photo.ref != null && !Tinode.isNull(card.photo.ref)) {
                vcard.append("PHOTO;VALUE=URI:").append(card.photo.ref).append("\r\n");
            } else if (card.photo.data != null) {
                // Encode byte array to base64 string
                String base64Data = Base64.getEncoder().encodeToString(card.photo.data);
                vcard.append("PHOTO;TYPE=").append(card.photo.type.toUpperCase())
                    .append(";ENCODING=b:").append(base64Data).append("\r\n");
            }
        }

        if (card.comm != null) {
            for (CommEntry comm : card.comm) {
                if (comm.value == null) {
                    continue;
                }
                String val = comm.value;
                switch (comm.proto) {
                    case TEL:
                        vcard.append("TEL");
                        break;
                    case EMAIL:
                        vcard.append("EMAIL");
                        break;
                    case TINODE:
                        vcard.append("IMPP");
                        // Prepend "tinode:" prefix if not already present
                        val = val.startsWith("tinode:") ? val : "tinode:" + comm.value;
                        break;
                    case HTTP:
                        vcard.append("URL");
                        break;
                }
                if (comm.des != null) {
                    vcard.append(";TYPE=");
                    for (int i = 0; i < comm.des.length; i++) {
                        if (i > 0) vcard.append(",");
                        vcard.append(comm.des[i]);
                    }
                }
                vcard.append(":").append(val).append("\r\n");
            }
        }

        vcard.append("END:VCARD\r\n");
        return vcard.toString();
    }

    /**
     * Import a vCard formatted string and convert it to a card object.
     * Supports vCard 2.1 and 3.0 formats with various field encodings.
     * @param vcardStr vCard formatted string.
     * @return Parsed card object or null if invalid.
     */
    @Nullable
    public static TheCard importVCard(String vcardStr) {
        if (vcardStr == null || vcardStr.isEmpty()) {
            return null;
        }

        // Handle line folding and Quoted-Printable soft line breaks
        List<String> lines = getStrings(vcardStr);

        TheCard card = new TheCard();
        // Map to collect and dedupe comm entries: key="proto|value", value=Set of types
        Map<String, List<String>> commMap = new HashMap<>();

        for (String line : lines) {
            int colonIndex = line.indexOf(':');
            if (colonIndex == -1) continue;

            String keyPart = line.substring(0, colonIndex);
            String value = line.substring(colonIndex + 1);

            String[] keyParams = keyPart.split(";");
            String key = keyParams[0].trim().toUpperCase();

            // Check if QUOTED-PRINTABLE encoding is specified
            boolean isQuotedPrintable = false;
            for (String param : keyParams) {
                String trimmed = param.trim().toUpperCase();
                if (trimmed.equals("QUOTED-PRINTABLE") || trimmed.equals("ENCODING=QUOTED-PRINTABLE")) {
                    isQuotedPrintable = true;
                    break;
                }
            }

            if (isQuotedPrintable) {
                value = decodeQuotedPrintable(value);
            }

            switch (key) {
                case "FN":
                    card.fn = unescapeValue(value);
                    break;
                case "N":
                    String[] parts = value.split(";");
                    card.n = new Name();
                    if (parts.length > 0 && !parts[0].isEmpty()) card.n.surname = unescapeValue(parts[0]);
                    if (parts.length > 1 && !parts[1].isEmpty()) card.n.given = unescapeValue(parts[1]);
                    if (parts.length > 2 && !parts[2].isEmpty()) card.n.additional = unescapeValue(parts[2]);
                    if (parts.length > 3 && !parts[3].isEmpty()) card.n.prefix = unescapeValue(parts[3]);
                    if (parts.length > 4 && !parts[4].isEmpty()) card.n.suffix = unescapeValue(parts[4]);
                    break;
                case "ORG":
                    String[] orgParts = value.split(";");
                    if (orgParts.length > 0 && !orgParts[0].isEmpty()) {
                        if (card.org == null) card.org = new Organization();
                        card.org.fn = unescapeValue(orgParts[0]);
                    }
                    break;
                case "TITLE":
                    if (!value.isEmpty()) {
                        if (card.org == null) card.org = new Organization();
                        card.org.title = unescapeValue(value);
                    }
                    break;
                case "NOTE":
                    card.note = unescapeValue(value);
                    break;
                case "BDAY":
                    parseBirthday(card, value);
                    break;
                case "PHOTO":
                    parsePhoto(card, keyParams, value);
                    break;
                case "TEL":
                    parseCommEntry(commMap, CommProto.TEL, keyParams, value);
                    break;
                case "EMAIL":
                    parseCommEntry(commMap, CommProto.EMAIL, keyParams, value);
                    break;
                case "IMPP":
                    CommProto proto = CommProto.IMPP;
                    if (value.startsWith("tinode:")) {
                        proto = CommProto.TINODE;
                    }
                    parseCommEntry(commMap, proto, keyParams, value);
                    break;
                case "URL":
                    parseCommEntry(commMap, CommProto.HTTP, keyParams, value);
                    break;
            }
        }

        // Convert commMap to comm array
        if (!commMap.isEmpty()) {
            List<CommEntry> commList = new ArrayList<>();
            for (Map.Entry<String, List<String>> entry : commMap.entrySet()) {
                String[] parts = entry.getKey().split("\\|", 2);
                CommEntry commEntry = new CommEntry();

                // Convert String to CommProto enum
                try {
                    commEntry.proto = CommProto.valueOf(parts[0].toUpperCase());
                } catch (IllegalArgumentException e) {
                    // Discard unknown protocol.
                    continue;
                }

                commEntry.value = parts[1];

                // Use LinkedHashSet to deduplicate while preserving order
                LinkedHashSet<String> uniqueTypes = new LinkedHashSet<>(entry.getValue());

                // Convert HashSet<String> to CommDes[] array
                List<CommDes> desList = new ArrayList<>();
                for (String typeStr : uniqueTypes) {
                    try {
                        desList.add(CommDes.valueOf(typeStr.toUpperCase()));
                    } catch (IllegalArgumentException e) {
                        // Default to OTHER if unknown type
                        desList.add(CommDes.OTHER);
                    }
                }
                commEntry.des = desList.toArray(new CommDes[0]);

                commList.add(commEntry);
            }
            card.comm = commList.toArray(new CommEntry[0]);
        }

        return card;
    }

    private static List<String> getStrings(String vcardStr) {
        String[] rawLines = vcardStr.split("\r\n|\n");
        List<String> lines = new ArrayList<>();
        StringBuilder currentLine = new StringBuilder();

        for (String line : rawLines) {
            if (line.startsWith(" ") || line.startsWith("\t")) {
                // vCard continuation - strip leading space/tab
                String continuation = line.substring(1);
                // Check if previous line ended with '=' (QP soft line break)
                if (currentLine.length() > 0 && currentLine.charAt(currentLine.length() - 1) == '=') {
                    // Remove the trailing '=' (QP soft line break) before appending
                    currentLine.setLength(currentLine.length() - 1);
                }
                currentLine.append(continuation);
            } else if (currentLine.toString().endsWith("=")) {
                // Quoted-Printable soft line break (when next line is NOT a vCard continuation)
                currentLine.setLength(currentLine.length() - 1);
                currentLine.append(line);
            } else {
                if (currentLine.length() > 0) {
                    lines.add(currentLine.toString());
                }
                currentLine = new StringBuilder(line);
            }
        }
        if (currentLine.length() > 0) {
            lines.add(currentLine.toString());
        }
        return lines;
    }

    private static void parseCommEntry(Map<String, List<String>> commMap, CommProto proto, 
                                       String[] keyParams, String value) {
        Set<String> types = new HashSet<>();
        for (String param : keyParams) {
            String trimmed = param.trim().toUpperCase();
            if (trimmed.startsWith("TYPE=")) {
                String typeValue = param.substring(param.indexOf('=') + 1);
                String[] typeList = typeValue.split(",");
                for (String t : typeList) {
                    String cleaned = t.trim().toLowerCase();
                    if (cleaned.startsWith("type=")) {
                        cleaned = cleaned.substring(5);
                    }
                    if (!cleaned.equals("internet")) {
                        types.add(cleaned);
                    }
                }
            }
        }

        String mapKey = proto + "|" + value;
        commMap.computeIfAbsent(mapKey, k -> new ArrayList<>()).addAll(types);
    }

    private static void parsePhoto(TheCard card, String[] keyParams, String value) {
        String type = "jpeg";
        String encoding = null;

        for (String param : keyParams) {
            String trimmed = param.substring(param.indexOf('=') + 1).trim().toLowerCase();
            if (param.trim().toUpperCase().startsWith("TYPE=")) {
                type = trimmed;
            } else if (param.trim().toUpperCase().startsWith("ENCODING=")) {
                encoding = trimmed;
            }
        }

        card.photo = new Photo();
        card.photo.type = type;
        if ("b".equals(encoding)) {
            // Decode base64-encoded image data to byte array
            try {
                card.photo.data = Base64.getDecoder().decode(value);
            } catch (IllegalArgumentException e) {
                // If base64 decoding fails, set to null
                card.photo.data = null;
            }
            card.photo.ref = Tinode.NULL_VALUE;
        } else {
            card.photo.ref = value;
            card.photo.data = Tinode.NULL_BYTES;
        }
    }

    private static void parseBirthday(TheCard card, String dateStr) {
        // Strip time if present
        dateStr = dateStr.split("[T ]")[0].trim();

        Integer year = null;
        Integer month = null;
        Integer day = null;

        String noHyphens = dateStr.replace("-", "");

        if (noHyphens.length() == 6 && noHyphens.matches("\\d{6}")) {
            // YYMMDD format
            int yy = Integer.parseInt(noHyphens.substring(0, 2));
            month = Integer.parseInt(noHyphens.substring(2, 4));
            day = Integer.parseInt(noHyphens.substring(4, 6));
            year = yy >= 35 ? 1900 + yy : 2000 + yy;
        } else if (dateStr.startsWith("--") || dateStr.startsWith("----")) {
            // --MMDD or ----MMDD format (no year)
            String cleaned = dateStr.replaceFirst("^-+", "");
            if (cleaned.length() == 4 && cleaned.matches("\\d{4}")) {
                month = Integer.parseInt(cleaned.substring(0, 2));
                day = Integer.parseInt(cleaned.substring(2, 4));
            } else if (cleaned.contains("-")) {
                String[] parts = cleaned.split("-");
                if (parts.length >= 2) {
                    month = Integer.parseInt(parts[0]);
                    day = Integer.parseInt(parts[1]);
                }
            }
        } else if (noHyphens.length() == 8 && noHyphens.matches("\\d{8}")) {
            // YYYYMMDD format
            year = Integer.parseInt(noHyphens.substring(0, 4));
            month = Integer.parseInt(noHyphens.substring(4, 6));
            day = Integer.parseInt(noHyphens.substring(6, 8));
        } else if (dateStr.contains("-")) {
            // YYYY-MM-DD format
            String[] parts = dateStr.split("-");
            if (parts[0] != null && !parts[0].isEmpty() && !parts[0].matches("^-+$")) {
                year = Integer.parseInt(parts[0]);
            }
            if (parts.length >= 2) {
                month = Integer.parseInt(parts[1]);
            }
            if (parts.length >= 3) {
                day = Integer.parseInt(parts[2]);
            }
        }

        // Basic validation
        boolean isValidMonth = month != null && month >= 1 && month <= 12;
        boolean isValidDay = day != null && day >= 1 && day <= 31;
        boolean isValidYear = year == null || (year >= 1800 && year <= 2200);

        if (isValidMonth && isValidDay && isValidYear) {
            card.bday = new Birthday();
            card.bday.m = month;
            card.bday.d = day;
            if (year != null) {
                card.bday.y = year;
            }
        }
    }

    private static String unescapeValue(String val) {
        Pattern pattern = Pattern.compile("\\\\([,;\\\\n])");
        Matcher matcher = pattern.matcher(val);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String char_ = matcher.group(1);
            if (char_ != null && char_.equals("n")) {
                matcher.appendReplacement(result, "\n");
            } else if (char_ != null) {
                matcher.appendReplacement(result, char_);
            }
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static String decodeQuotedPrintable(String val) {
        // Collect bytes first
        List<Byte> bytes = new ArrayList<>();
        int i = 0;
        while (i < val.length()) {
            if (val.charAt(i) == '=' && i + 2 < val.length()) {
                String hex = val.substring(i + 1, i + 3);
                if (hex.matches("[0-9A-Fa-f]{2}")) {
                    bytes.add((byte) Integer.parseInt(hex, 16));
                    i += 3;
                } else {
                    bytes.add((byte) val.charAt(i));
                    i++;
                }
            } else {
                bytes.add((byte) val.charAt(i));
                i++;
            }
        }

        // Convert list to byte array
        byte[] byteArray = new byte[bytes.size()];
        for (int j = 0; j < bytes.size(); j++) {
            byteArray[j] = bytes.get(j);
        }
        return new String(byteArray, StandardCharsets.UTF_8);
    }
}

