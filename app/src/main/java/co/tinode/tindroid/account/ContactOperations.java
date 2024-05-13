package co.tinode.tindroid.account;

import android.content.ContentProviderOperation;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Im;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.Photo;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.RawContacts;
import android.text.TextUtils;

import co.tinode.tindroid.Const;
import co.tinode.tindroid.R;
import co.tinode.tindroid.UiUtils;
import co.tinode.tinodesdk.Tinode;

import coil.Coil;
import coil.ImageLoaders;
import coil.request.ImageRequest;
import coil.size.Scale;

/**
 * Helper class for storing data in the platform content providers.
 */
@SuppressWarnings("UnusedReturnValue")
class ContactOperations {
    @SuppressWarnings("unused")
    private static final String TAG = "ContactOperations";

    private final ContentValues mValues;
    private final BatchOperation mBatchOperation;
    private final Context mContext;
    private long mRawContactId;
    private int mBackReference;
    private boolean mIsNewContact;
    private final boolean mIsSyncContext;

    private ContactOperations(Context context, BatchOperation batchOperation, boolean isSyncContext) {
        mValues = new ContentValues();
        mContext = context;
        mBatchOperation = batchOperation;
        mIsSyncContext = isSyncContext;
    }

    // Create new RAW_CONTACT record.
    private ContactOperations(Context context, String uid, String accountName,
                              BatchOperation batchOperation, boolean isSyncContext) {
        this(context, batchOperation, isSyncContext);

        mBackReference = mBatchOperation.size();
        mIsNewContact = true;
        mValues.put(RawContacts.SOURCE_ID, uid);
        mValues.put(RawContacts.ACCOUNT_TYPE, Utils.ACCOUNT_TYPE);
        mValues.put(RawContacts.ACCOUNT_NAME, accountName);

        mBatchOperation.add(newInsertCpo(RawContacts.CONTENT_URI, mIsSyncContext).withValues(mValues).build());
    }

    private ContactOperations(Context context, long rawContactId, BatchOperation batchOperation,
                              boolean isSyncContext) {
        this(context, batchOperation, isSyncContext);
        mIsNewContact = false;
        mRawContactId = rawContactId;
    }

    /**
     * Returns an instance of ContactOperations instance for adding new contact
     * to the platform contacts provider.
     *
     * @param context     the Authenticator Activity context
     * @param uid         the unique id of the contact
     * @param accountName the username for the SyncAdapter account
     * @return instance of ContactOperations
     */
    static ContactOperations createNewContact(Context context,
                                              String uid,
                                              String accountName,
                                              BatchOperation batchOperation,
                                              boolean isSyncContext) {
        return new ContactOperations(context, uid, accountName, batchOperation, isSyncContext);
    }

    /**
     * Returns an instance of ContactOperations for updating existing contact in
     * the platform contacts provider.
     *
     * @param context      the Authenticator Activity context
     * @param rawContactId the unique Id of the existing rawContact
     * @return instance of ContactOperations
     */
    static ContactOperations updateExistingContact(Context context,
                                                   long rawContactId,
                                                   BatchOperation batchOperation,
                                                   boolean isSyncContext) {
        return new ContactOperations(context, rawContactId, batchOperation, isSyncContext);
    }

    private static ContentProviderOperation.Builder newInsertCpo(Uri uri, boolean isSyncContext) {
        return ContentProviderOperation
                .newInsert(addCallerIsSyncAdapterParameter(uri, isSyncContext))
                .withYieldAllowed(false);
    }

    private static ContentProviderOperation.Builder newUpdateCpo(Uri uri, boolean isSyncContext) {
        return ContentProviderOperation
                .newUpdate(addCallerIsSyncAdapterParameter(uri, isSyncContext))
                .withYieldAllowed(false);
    }

    static ContentProviderOperation.Builder newDeleteCpo(Uri uri, boolean isSyncContext) {
        return ContentProviderOperation
                .newDelete(addCallerIsSyncAdapterParameter(uri, isSyncContext))
                .withYieldAllowed(false);
    }

    private static Uri addCallerIsSyncAdapterParameter(Uri uri, boolean isSyncContext) {
        return uri.buildUpon()
                .appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, String.valueOf(isSyncContext))
                .build();
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
    ContactOperations addName(final String fullName, final String firstName, final String lastName) {
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
                // It's OK to add the same value again.
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
    ContactOperations addEmail(final String email) {
        mValues.clear();
        if (!TextUtils.isEmpty(email)) {
            mValues.put(Email.ADDRESS, email);
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
    ContactOperations addPhone(final String phone, int phoneType) {
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
    @SuppressWarnings("unused")
    ContactOperations addIm(final String tinode_id) {
        mValues.clear();
        if (!TextUtils.isEmpty(tinode_id)) {
            mValues.put(Im.DATA, tinode_id);
            mValues.put(Im.TYPE, Im.TYPE_OTHER);
            mValues.put(Im.MIMETYPE, Im.CONTENT_ITEM_TYPE);
            mValues.put(Im.PROTOCOL, Im.PROTOCOL_CUSTOM);
            mValues.put(Im.CUSTOM_PROTOCOL, Utils.TINODE_IM_PROTOCOL);
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
    ContactOperations addAvatar(byte[] avatar, final Tinode tinode, final String ref, final String mimeType) {
        mValues.clear();
        if (ref != null) {
            ImageRequest req = new ImageRequest.Builder(mContext)
                    .data(ref)
                    .size(Const.MAX_AVATAR_SIZE, Const.MAX_AVATAR_SIZE)
                    .scale(Scale.FILL)
                    .build();
            Drawable drw = ImageLoaders.executeBlocking(Coil.imageLoader(mContext), req).getDrawable();
            avatar = UiUtils.bitmapToBytes(UiUtils.bitmapFromDrawable(drw), mimeType);
        }

        if (avatar != null) {
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
    ContactOperations addProfileAction(final String serverId) {
        mValues.clear();
        if (!TextUtils.isEmpty(serverId)) {
            mValues.put(Data.MIMETYPE, Utils.MIME_TINODE_PROFILE);
            mValues.put(Utils.DATA_PID, serverId);
            mValues.put(Utils.DATA_SUMMARY, mContext.getString(R.string.profile_action));
            mValues.put(Utils.DATA_DETAIL, mContext.getString(R.string.tinode_message));
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
    ContactOperations updateEmail(final String email, final String existingEmail, final Uri uri) {
        mValues.clear();
        if (!TextUtils.equals(existingEmail, email)) {
            mValues.put(Email.ADDRESS, email);
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
    ContactOperations updateName(Uri uri,
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
    ContactOperations updatePhone(String existingNumber, String phone, Uri uri) {
        mValues.clear();
        if (!TextUtils.equals(phone, existingNumber)) {
            mValues.put(Phone.NUMBER, phone);
            addUpdateOp(uri);
        }
        return this;
    }

    ContactOperations updateAvatar(byte[] avatarBuffer, Uri uri) {
        mValues.clear();
        if (avatarBuffer != null) {
            mValues.put(Photo.PHOTO, avatarBuffer);
            mValues.put(Photo.MIMETYPE, Photo.CONTENT_ITEM_TYPE);
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
        ContentProviderOperation.Builder builder = newInsertCpo(Data.CONTENT_URI, mIsSyncContext).withValues(mValues);
        if (mIsNewContact) {
            builder.withValueBackReference(Data.RAW_CONTACT_ID, mBackReference);
        }

        mBatchOperation.add(builder.build());
    }

    /**
     * Adds an update operation into the batch
     */
    private void addUpdateOp(Uri uri) {
        mBatchOperation.add(newUpdateCpo(uri, mIsSyncContext).withValues(mValues).build());
    }
}