package co.tinode.tindroid.account;

import android.accounts.Account;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Im;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.Photo;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.Groups;
import android.provider.ContactsContract.RawContacts;
import android.provider.ContactsContract.Settings;
import android.provider.ContactsContract.StatusUpdates;
import android.util.Log;

import java.util.Collection;
import java.util.Date;
import java.util.List;

import co.tinode.tindroid.R;
import co.tinode.tindroid.media.AvatarPhoto;
import co.tinode.tindroid.media.VxCard;
import co.tinode.tinodesdk.model.Subscription;

/**
 * Class for managing contacts sync related mOperations
 */
public class ContactsManager {
    private static final String TAG = "ContactManager";

    /**
     * Take a list of updated contacts and apply those changes to the
     * contacts database. Typically this list of contacts would have been
     * returned from the server, and we want to apply those changes locally.
     *
     * @param context        The context of Authenticator Activity
     * @param account        The username for the account
     * @param rawContacts    The list of contacts to update
     * @param lastSyncMarker The previous server sync-state
     * @return the server syncState that should be used in our next
     * sync request.
     */
    public static synchronized Date updateContacts(Context context, Account account,
                                                   Collection<Subscription<VxCard,?>> rawContacts,
                                                   Date lastSyncMarker) {
        Log.d(TAG, "ContactsManager got batch, count=" + rawContacts.size());

        Date currentSyncMarker = lastSyncMarker;
        final ContentResolver resolver = context.getContentResolver();
        final BatchOperation batchOperation = new BatchOperation(resolver);
        for (final Subscription<VxCard,?> rawContact : rawContacts) {

            // The server returns a timestamp with each record. On the next sync we can just
            // ask for changes that have occurred since that most-recent change.
            if (currentSyncMarker == null ||
                    (rawContact.updated != null && rawContact.updated.after(currentSyncMarker))) {
                currentSyncMarker = rawContact.updated;
            }

            long rawContactId = lookupRawContact(resolver, rawContact.user);
            if (rawContact.deleted != null) {
                if (rawContactId > 0) {
                    deleteContact(rawContactId, batchOperation);
                }
            } else {
                // Contact already exists
                if (rawContactId > 0) {
                    updateContact(context, resolver, rawContact, rawContactId, batchOperation);
                } else {
                    addContact(context, account, rawContact, batchOperation);
                }
            }
            // A sync adapter should batch operations on multiple contacts,
            // because it will make a dramatic performance difference.
            // (UI updates, etc)
            if (batchOperation.size() >= 50) {
                batchOperation.execute();
            }
        }
        batchOperation.execute();

        return currentSyncMarker;
    }

    /**
     * Update the status messages for a list of users.  This is typically called
     * for contacts we've just added to the system, since we can't monkey with
     * the contact's status until they have a profileId.
     *
     * @param context     The context of Authenticator Activity
     * @param rawContacts The list of users we want to update
     */
    public static void updateStatusMessages(Context context, List<Subscription> rawContacts) {
        final BatchOperation batchOperation = new BatchOperation(context.getContentResolver());
        for (Subscription rawContact : rawContacts) {
            updateContactStatus(context, rawContact, batchOperation);
        }
        batchOperation.execute();
    }

    /**
     * Adds a single contact to the platform contacts provider.
     * This can be used to respond to a new contact found as part
     * of sync information returned from the server, or because a
     * user added a new contact.
     *
     * @param context        the Authenticator Activity context
     * @param account        the account the contact belongs to
     * @param rawContact     the sample SyncAdapter User object
     * @param batchOperation allow us to batch together multiple operations
     *                       into a single provider call
     */
    public static void addContact(Context context, Account account, Subscription<VxCard,?> rawContact,
                                  BatchOperation batchOperation) {
        if (rawContact.pub == null) {
            return;
        }

        VxCard vc;
        try {
            vc = rawContact.pub;
        } catch (ClassCastException e) {
            return;
        }

        // Initiate adding data to contacts provider
        final ContactOperations contactOp = ContactOperations.createNewContact(
                context, rawContact.user, account.name, batchOperation);

        contactOp.addName(vc.fn, vc.n != null ? vc.n.given : null, vc.n != null ? vc.n.surname : null)
                .addEmail(vc.email != null && vc.email.length > 0 ? vc.email[0].uri : null)
                .addPhone(vc.getPhoneByType(VxCard.ContactType.MOBILE), Phone.TYPE_MOBILE)
                .addPhone(vc.getPhoneByType(VxCard.ContactType.HOME), Phone.TYPE_HOME)
                .addPhone(vc.getPhoneByType(VxCard.ContactType.WORK), Phone.TYPE_WORK)
                .addIm(rawContact.user)
                .addAvatar(vc.photo != null? vc.photo.data : null);

        // Actually create our status profile.
        contactOp.addProfileAction(rawContact.user);
    }

    /**
     * Updates a single contact to the platform contacts provider.
     * This method can be used to update a contact from a sync
     * operation or as a result of a user editing a contact
     * record.
     * <p>
     * This operation is actually relatively complex.  We query
     * the database to find all the rows of info that already
     * exist for this Contact. For rows that exist (and thus we're
     * modifying existing fields), we create an update operation
     * to change that field.  But for fields we're adding, we create
     * "add" operations to create new rows for those fields.
     *
     * @param context        the Authenticator Activity context
     * @param resolver       the ContentResolver to use
     * @param rawContact     the sample SyncAdapter contact object
     * @param rawContactId   the unique Id for this rawContact in contacts
     *                       provider
     * @param batchOperation allow us to batch together multiple operations
     *                       into a single provider call
     */
    public static void updateContact(Context context, ContentResolver resolver, Subscription<VxCard,?> rawContact,
                                     long rawContactId, BatchOperation batchOperation) {
        boolean existingCellPhone = false;
        boolean existingHomePhone = false;
        boolean existingWorkPhone = false;
        boolean existingEmail = false;
        boolean existingAvatar = false;

        final Cursor c = resolver.query(DataQuery.CONTENT_URI, DataQuery.PROJECTION, DataQuery.SELECTION,
                new String[]{String.valueOf(rawContactId)}, null);
        final ContactOperations contactOp = ContactOperations.updateExistingContact(context,
                rawContactId, batchOperation);
        if (c == null) {
            return;
        }

        VxCard vc;
        try {
            vc = rawContact.pub;
        } catch (ClassCastException e) {
            return;
        }

        try {
            // Iterate over the existing rows of data, and update each one
            // with the information we received from the server.
            // IM is not updated because it cannot change.
            while (c.moveToNext()) {
                final long id = c.getLong(DataQuery.COLUMN_ID);
                final String mimeType = c.getString(DataQuery.COLUMN_MIMETYPE);
                final Uri uri = ContentUris.withAppendedId(Data.CONTENT_URI, id);
                switch (mimeType) {
                    case StructuredName.CONTENT_ITEM_TYPE:
                        contactOp.updateName(uri,
                                c.getString(DataQuery.COLUMN_GIVEN_NAME),
                                c.getString(DataQuery.COLUMN_FAMILY_NAME),
                                c.getString(DataQuery.COLUMN_FULL_NAME),
                                vc.n != null ? vc.n.given : null,
                                vc.n != null ? vc.n.surname : null,
                                vc.fn);
                        break;
                    case Phone.CONTENT_ITEM_TYPE:
                        final int type = c.getInt(DataQuery.COLUMN_PHONE_TYPE);
                        if (type == Phone.TYPE_MOBILE) {
                            existingCellPhone = true;
                            contactOp.updatePhone(c.getString(DataQuery.COLUMN_PHONE_NUMBER),
                                    vc.getPhoneByType(VxCard.TYPE_MOBILE), uri);
                        } else if (type == Phone.TYPE_HOME) {
                            existingHomePhone = true;
                            contactOp.updatePhone(c.getString(DataQuery.COLUMN_PHONE_NUMBER),
                                    vc.getPhoneByType(VxCard.TYPE_HOME), uri);
                        } else if (type == Phone.TYPE_WORK) {
                            existingWorkPhone = true;
                            contactOp.updatePhone(c.getString(DataQuery.COLUMN_PHONE_NUMBER),
                                    vc.getPhoneByType(VxCard.TYPE_BUSINESS), uri);
                        }
                        break;
                    case Email.CONTENT_ITEM_TYPE:
                        existingEmail = true;
                        contactOp.updateEmail(vc.email != null && vc.email.length > 0 ? vc.email[0].uri : null,
                                c.getString(DataQuery.COLUMN_EMAIL_ADDRESS), uri);
                        break;
                    case Photo.CONTENT_ITEM_TYPE:
                        existingAvatar = true;
                        contactOp.updateAvatar(vc.photo != null ? vc.photo.data : null, uri);
                        break;

                }
            } // while
        } finally {
            c.close();
        }
        // Add the cell phone, if present and not updated above
        if (!existingCellPhone) {
            contactOp.addPhone(vc.getPhoneByType(VxCard.TYPE_MOBILE), Phone.TYPE_MOBILE);
        }
        // Add the home phone, if present and not updated above
        if (!existingHomePhone) {
            contactOp.addPhone(vc.getPhoneByType(VxCard.TYPE_HOME), Phone.TYPE_HOME);
        }
        // Add the work phone, if present and not updated above
        if (!existingWorkPhone) {
            contactOp.addPhone(vc.getPhoneByType(VxCard.TYPE_WORK), Phone.TYPE_WORK);
        }
        // Add the email address, if present and not updated above
        if (!existingEmail) {
            contactOp.addEmail(vc.email != null && vc.email.length > 0 ? vc.email[0].uri : null);
        }
        // Add the avatar if we didn't update the existing avatar
        if (!existingAvatar) {
            contactOp.addAvatar(vc.photo != null ? vc.photo.data : null);
        }

        // If we don't have a status profile, then create one.  This could
        // happen for contacts that were created on the client - we don't
        // create the status profile until after the first sync...
        final String serverId = rawContact.user;
        final long profileId = lookupProfile(resolver, serverId);
        if (profileId <= 0) {
            contactOp.addProfileAction(serverId);
        }
    }

    /**
     * When we first add a sync adapter to the system, the contacts from that
     * sync adapter will be hidden unless they're merged/grouped with an existing
     * contact.  But typically we want to actually show those contacts, so we
     * need to mess with the Settings table to get them to show up.
     *
     * @param context the Authenticator Activity context
     * @param account the Account who's visibility we're changing
     */
    public static void makeAccountContactsVisibile(Context context, Account account) {
        ContentValues values = new ContentValues();
        values.put(RawContacts.ACCOUNT_NAME, account.name);
        values.put(RawContacts.ACCOUNT_TYPE, Utils.ACCOUNT_TYPE);
        values.put(Settings.UNGROUPED_VISIBLE, 1);
        context.getContentResolver().insert(Settings.CONTENT_URI, values);
    }

    /**
     * Create a contacts group to store group topics as contacts. This will make them invisible to the in the
     * user in contact applications but they can still be fetched by name when needed.
     *
     * @param context the Authenticator Activity context
     * @param account the Account which will own the group
     */
    public static long createInvisibleTinodeGroup(Context context, Account account) {
        ContentValues values = new ContentValues();
        values.put(Groups.TITLE, "INVISIBLE");
        values.put(Groups.ACCOUNT_NAME, account.name);
        values.put(Groups.ACCOUNT_TYPE, Utils.ACCOUNT_TYPE);
        values.put(Groups.GROUP_VISIBLE, false);
        Uri result = context.getContentResolver().insert(Groups.CONTENT_URI, values);
        if (result != null) {
            return Long.parseLong(result.getLastPathSegment());
        }
        return -1;
    }

    public static Subscription<VxCard,?> getStoredSubscription(ContentResolver resolver, String uid) {
        if (uid == null) {
            return null;
        }

        long id = lookupRawContact(resolver, uid);
        Log.d(TAG, "getStoredSubscription for '" + uid + "' lookupRawContact returned " + id);
        return getRawContact(resolver, id);
    }

    /**
     * Return a Subscription object with data extracted from a contact stored
     * in the local contacts database.
     * <p>
     * Because a contact is actually stored over several rows in the
     * database, our query will return those multiple rows of information.
     * We then iterate over the rows and build the User structure from
     * what we find.
     *
     * @param resolver     ContentResolver to use to access the database
     * @param rawContactId the unique ID for the local contact
     * @return a User object containing info on that contact
     */
    private static Subscription<VxCard,?> getRawContact(ContentResolver resolver, long rawContactId) {
        if (rawContactId <= 0) {
            return null;
        }

        VxCard vcard = new VxCard();

        final Cursor c = resolver.query(DataQuery.CONTENT_URI, DataQuery.PROJECTION, DataQuery.SELECTION,
                new String[]{String.valueOf(rawContactId)}, null);
        if (c == null) {
            return null;
        }

        try {
            while (c.moveToNext()) {
                switch (c.getString(DataQuery.COLUMN_MIMETYPE)) {
                    case StructuredName.CONTENT_ITEM_TYPE:
                        if (vcard.n == null) {
                            vcard.n = new VxCard.Name();
                        }
                        vcard.n.surname = c.getString(DataQuery.COLUMN_FAMILY_NAME);
                        vcard.n.given = c.getString(DataQuery.COLUMN_GIVEN_NAME);
                        vcard.fn = c.getString(DataQuery.COLUMN_FULL_NAME);
                        break;
                    case Phone.CONTENT_ITEM_TYPE:
                        final int type = c.getInt(DataQuery.COLUMN_PHONE_TYPE);
                        if (type == Phone.TYPE_MOBILE) {
                            vcard.addPhone(c.getString(DataQuery.COLUMN_PHONE_NUMBER), VxCard.TYPE_MOBILE);
                        } else if (type == Phone.TYPE_HOME) {
                            vcard.addPhone(c.getString(DataQuery.COLUMN_PHONE_NUMBER), VxCard.TYPE_HOME);
                        } else if (type == Phone.TYPE_WORK) {
                            vcard.addPhone(c.getString(DataQuery.COLUMN_PHONE_NUMBER), VxCard.TYPE_WORK);
                        }
                        break;
                    case Email.CONTENT_ITEM_TYPE:
                        vcard.addEmail(c.getString(DataQuery.COLUMN_EMAIL_ADDRESS), VxCard.TYPE_OTHER);
                        break;
                    case Photo.CONTENT_ITEM_TYPE:
                        vcard.setAvatar(new AvatarPhoto(c.getBlob(DataQuery.COLUMN_AVATAR_IMAGE)));
                        break;
                }
            } // while
        } finally {
            c.close();
        }

        Subscription<VxCard,?> rawContact = new Subscription<>();
        rawContact.pub = vcard;
        return rawContact;
    }

    /**
     * Update the status message associated with the specified user.  The status
     * message would be something that is likely to be used by IM or social
     * networking sync providers, and less by a straightforward contact provider.
     * But it's a useful demo to see how it's done.
     *
     * @param context        the Authenticator Activity context
     * @param rawContact     the contact whose status we should update
     * @param batchOperation allow us to batch together multiple operations
     */
    private static void updateContactStatus(Context context, Subscription rawContact,
                                            BatchOperation batchOperation) {
        final ContentValues values = new ContentValues();
        final ContentResolver resolver = context.getContentResolver();
        final String uid = rawContact.user;

        // Look up the user's data row
        final long profileId = lookupProfile(resolver, uid);
        // Insert the activity into the stream
        if (profileId > 0) {
            values.put(StatusUpdates.DATA_ID, profileId);
            // values.put(StatusUpdates.STATUS, status);
            values.put(StatusUpdates.PROTOCOL, Im.PROTOCOL_CUSTOM);
            values.put(StatusUpdates.CUSTOM_PROTOCOL, Utils.IM_PROTOCOL);
            values.put(StatusUpdates.IM_ACCOUNT, uid);
            values.put(StatusUpdates.IM_HANDLE, uid);
            values.put(StatusUpdates.STATUS_RES_PACKAGE, context.getPackageName());
            values.put(StatusUpdates.STATUS_ICON, R.mipmap.ic_launcher);
            //values.put(StatusUpdates.STATUS_LABEL, R.string.label);
            batchOperation.add(ContactOperations.newInsertCpo(StatusUpdates.CONTENT_URI, false)
                    .withValues(values).build());
        }
    }

    /**
     * Deletes a contact from the platform contacts provider. This method is used
     * both for contacts that were deleted locally and then that deletion was synced
     * to the server, and for contacts that were deleted on the server and the
     * deletion was synced to the client.
     *
     * @param uid     the unique Id for this rawContact in contacts provider, locally issued
     */
    private static void deleteContact(long uid, BatchOperation batchOperation) {
        batchOperation.add(ContactOperations.newDeleteCpo(
                ContentUris.withAppendedId(RawContacts.CONTENT_URI, uid), true).build());
    }

    /**
     * Returns the RawContact id for a sample SyncAdapter contact, or 0 if the
     * sample SyncAdapter user isn't found.
     *
     * @param resolver the content resolver to use
     * @param uid      the sample SyncAdapter user ID to lookup
     * @return the RawContact id, or 0 if not found
     */
    private static long lookupRawContact(ContentResolver resolver, String uid) {
        long rawContactId = 0;
        final Cursor c = resolver.query(
                UserIdQuery.CONTENT_URI,
                UserIdQuery.PROJECTION,
                UserIdQuery.SELECTION,
                new String[]{uid},
                null);

        if (c != null) {
            try {
                if (c.moveToFirst()) {
                    rawContactId = c.getLong(UserIdQuery.COLUMN_RAW_CONTACT_ID);
                }
            } finally {
                c.close();
            }
        }

        return rawContactId;
    }

    /**
     * Returns the Data id for a sample SyncAdapter contact's profile row, or 0
     * if the sample SyncAdapter user isn't found.
     *
     * @param resolver a content resolver
     * @param uid      server-issued unique iD of the contact
     * @return the profile Data row id, or 0 if not found
     */
    private static long lookupProfile(ContentResolver resolver, String uid) {
        final Cursor c = resolver.query(Data.CONTENT_URI, ProfileQuery.PROJECTION, ProfileQuery.SELECTION,
                new String[]{uid}, null);

        if (c == null) {
            return 0;
        }

        long profileId = 0;
        try {
            if (c.moveToFirst()) {
                profileId = c.getLong(ProfileQuery.COLUMN_ID);
            }
        } finally {
            c.close();
        }

        return profileId;
    }


    /**
     * Constants for a query to find a contact given a sample SyncAdapter user
     * ID.
     */
    final private static class ProfileQuery {
        static final String[] PROJECTION = new String[]{Data._ID};
        static final int COLUMN_ID = 0;
        static final String SELECTION =
                Data.MIMETYPE + "='" + Utils.MIME_PROFILE + "' AND "
                        + Utils.DATA_PID + "=?";

        private ProfileQuery() {
        }
    }

    /**
     * Constants for a query to find a contact given a sample SyncAdapter user
     * ID.
     */
    final private static class UserIdQuery {
        static final String[] PROJECTION = new String[]{
                RawContacts._ID,
                RawContacts.CONTACT_ID
        };
        static final int COLUMN_RAW_CONTACT_ID = 0;
        static final int COLUMN_LINKED_CONTACT_ID = 1;
        static final Uri CONTENT_URI = RawContacts.CONTENT_URI;
        static final String SELECTION =
                RawContacts.ACCOUNT_TYPE + "='" + Utils.ACCOUNT_TYPE + "' AND " + RawContacts.SOURCE_ID + "=?";

        private UserIdQuery() {
        }
    }

    /**
     * Constants for a query to get contact data for a given rawContactId
     */
    final private static class DataQuery {
        static final String[] PROJECTION =
                new String[]{Data._ID, RawContacts.SOURCE_ID, Data.MIMETYPE, Data.DATA1,
                        Data.DATA2, Data.DATA3, Data.DATA15, Data.SYNC1};
        static final int COLUMN_ID = 0;
        static final int COLUMN_SERVER_ID = 1;
        static final int COLUMN_MIMETYPE = 2;
        static final int COLUMN_DATA1 = 3;
        static final int COLUMN_DATA2 = 4;
        static final int COLUMN_DATA3 = 5;
        static final int COLUMN_DATA15 = 6;
        static final int COLUMN_SYNC1 = 7;
        static final Uri CONTENT_URI = Data.CONTENT_URI;
        static final int COLUMN_PHONE_NUMBER = COLUMN_DATA1;
        static final int COLUMN_PHONE_TYPE = COLUMN_DATA2;
        static final int COLUMN_EMAIL_ADDRESS = COLUMN_DATA1;
        static final int COLUMN_EMAIL_TYPE = COLUMN_DATA2;
        static final int COLUMN_FULL_NAME = COLUMN_DATA1;
        static final int COLUMN_GIVEN_NAME = COLUMN_DATA2;
        static final int COLUMN_FAMILY_NAME = COLUMN_DATA3;
        static final int COLUMN_AVATAR_IMAGE = COLUMN_DATA15;
        static final int COLUMN_NOTE = COLUMN_DATA1;
        static final int COLUMN_SYNC_DIRTY = COLUMN_SYNC1;
        static final String SELECTION = Data.RAW_CONTACT_ID + "=?";

        private DataQuery() {
        }
    }
}