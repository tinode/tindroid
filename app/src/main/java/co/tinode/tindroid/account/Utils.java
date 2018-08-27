package co.tinode.tindroid.account;

import android.accounts.Account;
import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Data;
import android.util.SparseArray;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;

import java.util.LinkedList;
import java.util.List;
import java.util.Locale;


/**
 * Constants and misc utils
 */
public class Utils {
    private static final String TAG = "Utils";

    // Account management constants
    public static final String TOKEN_TYPE = "co.tinode.token";
    public static final String ACCOUNT_TYPE = "co.tinode.account";
    public static final String SYNC_AUTHORITY = "com.android.contacts";
    public static final String IM_PROTOCOL = "Tinode";

    // Constants for accessing shared preferences
    public static final String PREFS_HOST_NAME = "pref_hostName";
    public static final String PREFS_USE_TLS = "pref_useTLS";

    // Prefixes for various contacts
    public static final String TAG_LABEL_PHONE = "tel:";
    public static final String TAG_LABEL_EMAIL = "email:";
    public static final String TAG_LABEL_TINODE = "tinode:";

    /**
     * MIME-type used when storing a profile {@link ContactsContract.Data} entry.
     */
    public static final String MIME_PROFILE =
            "vnd.android.cursor.item/vnd.tinode.profile";
    public static final String DATA_PID = Data.DATA1;
    public static final String DATA_SUMMARY = Data.DATA2;
    public static final String DATA_DETAIL = Data.DATA3;

    public static Account createAccount(String accountName) {
        return new Account(accountName, ACCOUNT_TYPE);
    }

    public static SparseArray<ContactHolder> fetchEmailsAndPhones(ContentResolver resolver, Uri uri) {
        SparseArray<ContactHolder> map = new SparseArray<>();

        String[] projection = {
                Data.CONTACT_ID,
                Data.MIMETYPE,
                ContactsContract.CommonDataKinds.Email.DATA,
                ContactsContract.CommonDataKinds.Email.TYPE,
                ContactsContract.CommonDataKinds.Im.PROTOCOL,
                ContactsContract.CommonDataKinds.Im.CUSTOM_PROTOCOL,
        };
        String selection = Data.MIMETYPE + " in (?, ?, ?)";
        String[] selectionArgs = {
                ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE,
                ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE,
                ContactsContract.CommonDataKinds.Im.CONTENT_ITEM_TYPE,
        };

        // ok, let's work...
        Cursor cursor = resolver.query(uri, projection, selection, selectionArgs, null);
        if (cursor == null) {
            return map;
        }

        final int contactIdIdx = cursor.getColumnIndex(Data.CONTACT_ID);
        final int mimeTypeIdx = cursor.getColumnIndex(Data.MIMETYPE);
        final int dataIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.DATA);
        final int typeIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.TYPE);
        final int imProtocolIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Im.PROTOCOL);
        final int imProtocolNameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Im.CUSTOM_PROTOCOL);

        final PhoneNumberUtil phoneUtil = PhoneNumberUtil.getInstance();
        final String country = Locale.getDefault().getCountry();

        while (cursor.moveToNext()) {
            int type = cursor.getInt(typeIdx);
            int contact_id = cursor.getInt(contactIdIdx);
            String data = cursor.getString(dataIdx);
            String mimeType = cursor.getString(mimeTypeIdx);

            // Log.d(TAG, "Got id=" + contact_id + ", mime='" + mimeType +"', val='" + data + "'");

            ContactHolder holder = map.get(contact_id);
            if (holder == null) {
                holder = new ContactHolder();
                map.put(contact_id, holder);
            }

            switch (mimeType) {
                case ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE:
                    // This is an email
                    //Log.d(TAG, "Adding email '" + data + "' to contact=" + contact_id);
                    holder.putEmail(data);
                    break;
                case ContactsContract.CommonDataKinds.Im.CONTENT_ITEM_TYPE:
                    int protocol = cursor.getInt(imProtocolIdx);
                    String protocolName = cursor.getString(imProtocolNameIdx);
                    // Log.d(TAG, "Possibly adding IM '" + data + "' to contact=" + contact_id);
                    if (protocol == ContactsContract.CommonDataKinds.Im.PROTOCOL_CUSTOM &&
                            protocolName.equals(IM_PROTOCOL)) {
                        holder.putIm(data);
                        // Log.d(TAG, "Added IM '" + data + "' to contact=" + contact_id);
                    }
                    break;
                default:
                    // This is a phone number. Use mobile phones only.
                    if (type == ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE) {
                        // Log.d(TAG, "Adding mobile phone '" + data + "' to contact=" + contact_id);
                        try {
                            // Normalize phone number format
                            data = phoneUtil.format(phoneUtil.parse(data, country),
                                    PhoneNumberUtil.PhoneNumberFormat.E164);
                            holder.putPhone(data);
                        } catch (NumberParseException e) {
                            e.printStackTrace();
                        }
                    }
                    break;
            }
        }
        cursor.close();

        return map;
    }

    public static class ContactHolder {
        List<String> emails;
        List<String> phones;
        List<String> ims;

        public ContactHolder() {
            emails = null;
            phones = null;
            ims = null;
        }

        private static void Stringify(List<String> vals, StringBuilder str) {
            if (vals != null) {
                for (String entry : vals) {
                    str.append(entry);
                    str.append(",");
                }
            }
        }

        public void putEmail(String email) {
            if (emails == null) {
                emails = new LinkedList<>();
            }
            emails.add(email);
        }

        public void putPhone(String phone) {
            if (phones == null) {
                phones = new LinkedList<>();
            }
            phones.add(phone);
        }

        public void putIm(String im) {
            if (ims == null) {
                ims = new LinkedList<>();
            }
            ims.add(im);
        }

        public int getEmailCount() {
            return emails != null ? emails.size() : 0;
        }

        public int getPhoneCount() {
            return phones != null ? phones.size() : 0;
        }

        public int getImCount() {
            return ims != null ? ims.size() : 0;
        }

        public String getEmail() {
            return emails != null ? emails.get(0) : null;
        }

        public String getPhone() {
            return phones != null ? phones.get(0) : null;
        }

        public String getIm() {
            return ims != null ? ims.get(0) : null;
        }

        @Override
        public String toString() {
            StringBuilder str = new StringBuilder();
            Stringify(emails, str);
            Stringify(phones, str);
            Stringify(ims, str);
            return str.toString();
        }

        public String bestContact() {
            if (ims != null) {
                return TAG_LABEL_TINODE + ims.get(0);
            }

            if (phones != null) {
                return TAG_LABEL_PHONE + phones.get(0);
            }

            if (emails != null) {
                return TAG_LABEL_EMAIL + emails.get(0);
            }

            return "";
        }
    }
}
