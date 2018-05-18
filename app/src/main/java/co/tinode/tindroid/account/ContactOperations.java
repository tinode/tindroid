package co.tinode.tindroid.account;

import android.content.ContentProviderOperation;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.GroupMembership;
import android.provider.ContactsContract.CommonDataKinds.Im;
import android.provider.ContactsContract.CommonDataKinds.Note;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.Photo;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.RawContacts;
import android.text.TextUtils;

import co.tinode.tindroid.R;

/**
 * Helper class for storing data in the platform content providers.
 */
public class ContactOperations {
    private final ContentValues mValues;
    private final BatchOperation mBatchOperation;
    private final Context mContext;
    private long mRawContactId;
    private int mBackReference;
    private boolean mIsNewContact;
    /**
     * Since we're sending a lot of contact provider operations in a single
     * batched operation, we want to make sure that we "yield" periodically
     * so that the Contact Provider can write changes to the DB, and can
     * open a new transaction.  This prevents ANR (application not responding)
     * errors.  The recommended time to specify that a yield is permitted is
     * with the first operation on a particular contact.  So if we're updating
     * multiple fields for a single contact, we make sure that we call
     * withYieldAllowed(true) on the first field that we update. We use
     * mIsYieldAllowed to keep track of what value we should pass to
     * withYieldAllowed().
     */
    private boolean mIsYieldAllowed;

    /**
     * Returns an instance of ContactOperations instance for adding new contact
     * to the platform contacts provider.
     *
     * @param context     the Authenticator Activity context
     * @param uid         the unique id of the contact
     * @param accountName the username for the SyncAdapter account
     * @return instance of ContactOperations
     */
    public static ContactOperations createNewContact(Context context, String uid,
                                                     String accountName, BatchOperation batchOperation) {
        return new ContactOperations(context, uid, accountName, batchOperation);
    }

    /**
     * Returns an instance of ContactOperations for updating existing contact in
     * the platform contacts provider.
     *
     * @param context      the Authenticator Activity context
     * @param rawContactId the unique Id of the existing rawContact
     * @return instance of ContactOperations
     */
    public static ContactOperations updateExistingContact(Context context, long rawContactId,
                                                          BatchOperation batchOperation) {
        return new ContactOperations(context, rawContactId, batchOperation);
    }

    public ContactOperations(Context context, BatchOperation batchOperation) {
        mValues = new ContentValues();
        mIsYieldAllowed = true;
        mContext = context;
        mBatchOperation = batchOperation;
    }

    public ContactOperations(Context context, String uid, String accountName,
                             BatchOperation batchOperation) {
        this(context, batchOperation);
        mBackReference = mBatchOperation.size();
        mIsNewContact = true;
        mValues.put(RawContacts.SOURCE_ID, uid);
        mValues.put(RawContacts.ACCOUNT_TYPE, Utils.ACCOUNT_TYPE);
        mValues.put(RawContacts.ACCOUNT_NAME, accountName);

        ContentProviderOperation.Builder builder =
                newInsertCpo(RawContacts.CONTENT_URI, true).withValues(mValues);
        mBatchOperation.add(builder.build());
    }

    public ContactOperations(Context context, long rawContactId, BatchOperation batchOperation) {
        this(context, batchOperation);
        mIsNewContact = false;
        mRawContactId = rawContactId;
    }

    /**
     * Adds a contact name. We can take either a full name ("Bob Smith") or separated
     * first-name and last-name ("Bob" and "Smith").
     *
     * @param fullName  The full name of the contact - typically from an edit form
     *                  Can be null if firstName/lastName are specified.
     * @param firstName The first name of the contact - can be null if fullName
     *                  is specified.
     * @param lastName  The last name of the contact - can be null if fullName
     *                  is specified.
     * @return instance of ContactOperations
     */
    public ContactOperations addName(String fullName, String firstName, String lastName) {
        mValues.clear();
        if (!TextUtils.isEmpty(fullName)) {
            mValues.put(StructuredName.DISPLAY_NAME, fullName);
            mValues.put(StructuredName.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE);
        } else {
            if (!TextUtils.isEmpty(firstName)) {
                mValues.put(StructuredName.GIVEN_NAME, firstName);
                mValues.put(StructuredName.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE);
            }
            if (!TextUtils.isEmpty(lastName)) {
                mValues.put(StructuredName.FAMILY_NAME, lastName);
                mValues.put(StructuredName.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE);
            }
        }
        if (mValues.size() > 0) {
            addInsertOp();
        }
        return this;
    }

    /**
     * Adds an email
     *
     * @param email address we're adding
     * @return instance of ContactOperations
     */
    public ContactOperations addEmail(String email) {
        mValues.clear();
        if (!TextUtils.isEmpty(email)) {
            mValues.put(Email.DATA, email);
            mValues.put(Email.TYPE, Email.TYPE_OTHER);
            mValues.put(Email.MIMETYPE, Email.CONTENT_ITEM_TYPE);
            addInsertOp();
        }
        return this;
    }

    /**
     * Adds a phone number
     *
     * @param phone     new phone number for the contact
     * @param phoneType the type: cell, home, etc.
     * @return instance of ContactOperations
     */
    public ContactOperations addPhone(String phone, int phoneType) {
        mValues.clear();
        if (!TextUtils.isEmpty(phone)) {
            mValues.put(Phone.NUMBER, phone);
            mValues.put(Phone.TYPE, phoneType);
            mValues.put(Phone.MIMETYPE, Phone.CONTENT_ITEM_TYPE);
            addInsertOp();
        }
        return this;
    }

    /**
     * Adds a tinode im address
     *
     * @param tinode_id address we're adding
     * @return instance of ContactOperations
     */
    public ContactOperations addIm(String tinode_id) {
        mValues.clear();
        if (!TextUtils.isEmpty(tinode_id)) {
            mValues.put(Im.DATA, tinode_id);
            mValues.put(Im.TYPE, Email.TYPE_OTHER);
            mValues.put(Im.MIMETYPE, Im.CONTENT_ITEM_TYPE);
            mValues.put(Im.PROTOCOL, Im.PROTOCOL_CUSTOM);
            mValues.put(Im.CUSTOM_PROTOCOL, Utils.IM_PROTOCOL);
            addInsertOp();
        }
        return this;
    }

    /**
     * Add avatar to profile
     *
     * @param avatar avatar image serialized into byte array
     * @return instance of ContactOperations
     */
    public ContactOperations addAvatar(byte[] avatar) {
        if (avatar != null) {
            mValues.clear();
            mValues.put(Photo.PHOTO, avatar);
            mValues.put(Photo.MIMETYPE, Photo.CONTENT_ITEM_TYPE);
            addInsertOp();
        }
        return this;
    }

    /**
     * Adds a profile action
     *
     * @param serverId the uid of the topic object
     * @return instance of ContactOperations
     */
    public ContactOperations addProfileAction(String serverId) {
        mValues.clear();
        if (!TextUtils.isEmpty(serverId)) {
            mValues.put(Data.MIMETYPE, Utils.MIME_PROFILE);
            mValues.put(Utils.DATA_PID, serverId);
            mValues.put(Utils.DATA_SUMMARY, mContext.getString(R.string.profile_action));
            mValues.put(Utils.DATA_DETAIL, mContext.getString(R.string.tinode_message));
            addInsertOp();
        }
        return this;
    }

    /**
     * Adds a profile action
     *
     * @param groupId id of the group to add to
     * @return instance of ContactOperations
     */
    public ContactOperations addToInvisibleGroup(long groupId) {
        mValues.clear();
        if (groupId >= 0) {
            mValues.put(GroupMembership.GROUP_ROW_ID, groupId);
            mValues.put(GroupMembership.MIMETYPE, GroupMembership.CONTENT_ITEM_TYPE);
            addInsertOp();
        }
        return this;
    }

    /**
     * Updates contact's email
     *
     * @param email email id of the sample SyncAdapter user
     * @param uri   Uri for the existing raw contact to be updated
     * @return instance of ContactOperations
     */
    public ContactOperations updateEmail(String email, String existingEmail, Uri uri) {
        if (!TextUtils.equals(existingEmail, email)) {
            mValues.clear();
            mValues.put(Email.DATA, email);
            addUpdateOp(uri);
        }
        return this;
    }

    /**
     * Updates contact's name. The caller can either provide first-name
     * and last-name fields or a full-name field.
     *
     * @param uri               Uri for the existing raw contact to be updated
     * @param existingFirstName the first name stored in provider
     * @param existingLastName  the last name stored in provider
     * @param existingFullName  the full name stored in provider
     * @param firstName         the new first name to store
     * @param lastName          the new last name to store
     * @param fullName          the new full name to store
     * @return instance of ContactOperations
     */
    public ContactOperations updateName(Uri uri,
                                        String existingFirstName,
                                        String existingLastName,
                                        String existingFullName,
                                        String firstName,
                                        String lastName,
                                        String fullName) {
        mValues.clear();
        if (TextUtils.isEmpty(fullName)) {
            if (!TextUtils.equals(existingFirstName, firstName)) {
                mValues.put(StructuredName.GIVEN_NAME, firstName);
            }
            if (!TextUtils.equals(existingLastName, lastName)) {
                mValues.put(StructuredName.FAMILY_NAME, lastName);
            }
        } else {
            if (!TextUtils.equals(existingFullName, fullName)) {
                mValues.put(StructuredName.DISPLAY_NAME, fullName);
            }
        }
        if (mValues.size() > 0) {
            addUpdateOp(uri);
        }
        return this;
    }

    /**
     * Updates contact's phone
     *
     * @param existingNumber phone number stored in contacts provider
     * @param phone          new phone number for the contact
     * @param uri            Uri for the existing raw contact to be updated
     * @return instance of ContactOperations
     */
    public ContactOperations updatePhone(String existingNumber, String phone, Uri uri) {
        if (!TextUtils.equals(phone, existingNumber)) {
            mValues.clear();
            mValues.put(Phone.NUMBER, phone);
            addUpdateOp(uri);
        }
        return this;
    }

    public ContactOperations updateAvatar(byte[] avatarBuffer, Uri uri) {
        if (avatarBuffer != null) {
            mValues.clear();
            mValues.put(Photo.PHOTO, avatarBuffer);
            mValues.put(Photo.MIMETYPE, Photo.CONTENT_ITEM_TYPE);
            addUpdateOp(uri);
        }
        return this;
    }

    /**
     * Updates contact's note
     *
     * @param note note of the SyncAdapter user
     * @param uri  Uri for the existing raw contact to be updated
     * @return instance of ContactOperations
     */
    public ContactOperations updateNote(String note, String oldNote, Uri uri) {
        if (!TextUtils.equals(note, oldNote)) {
            mValues.clear();
            mValues.put(Note.NOTE, note);
            addUpdateOp(uri);
        }
        return this;
    }

    /**
     * Adds an insert operation into the batch
     */
    private void addInsertOp() {
        if (!mIsNewContact) {
            mValues.put(Phone.RAW_CONTACT_ID, mRawContactId);
        }
        ContentProviderOperation.Builder builder =
                newInsertCpo(Data.CONTENT_URI, mIsYieldAllowed);
        builder.withValues(mValues);
        if (mIsNewContact) {
            builder.withValueBackReference(Data.RAW_CONTACT_ID, mBackReference);
        }
        mIsYieldAllowed = false;
        mBatchOperation.add(builder.build());
    }

    /**
     * Adds an update operation into the batch
     */
    private void addUpdateOp(Uri uri) {
        ContentProviderOperation.Builder builder =
                newUpdateCpo(uri, mIsYieldAllowed).withValues(mValues);
        mIsYieldAllowed = false;
        mBatchOperation.add(builder.build());
    }

    public static ContentProviderOperation.Builder newInsertCpo(Uri uri, boolean isYieldAllowed) {
        return ContentProviderOperation
                .newInsert(addCallerIsSyncAdapterParameter(uri))
                .withYieldAllowed(isYieldAllowed);
    }

    public static ContentProviderOperation.Builder newUpdateCpo(Uri uri, boolean isYieldAllowed) {
        return ContentProviderOperation
                .newUpdate(addCallerIsSyncAdapterParameter(uri))
                .withYieldAllowed(isYieldAllowed);
    }

    public static ContentProviderOperation.Builder newDeleteCpo(Uri uri, boolean isYieldAllowed) {
        return ContentProviderOperation
                .newDelete(addCallerIsSyncAdapterParameter(uri))
                .withYieldAllowed(isYieldAllowed);
    }

    private static Uri addCallerIsSyncAdapterParameter(Uri uri) {
        return uri.buildUpon()
                .appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true")
                .build();
    }
}