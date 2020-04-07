package co.tinode.tindroid.account;

import android.accounts.Account;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.Photo;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.RawContacts;
import android.provider.ContactsContract.Settings;

import java.util.Collection;
import java.util.Date;

import co.tinode.tindroid.R;
import co.tinode.tindroid.media.VxCard;
import co.tinode.tinodesdk.model.Subscription;
import co.tinode.tinodesdk.model.VCard;

/**
 * Class for managing contacts sync related mOperations
 */
public class ContactsManager {
    @SuppressWarnings("unused")
    private static final String TAG = "ContactManager";

    private static final int BATCH_SIZE = 50;

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
    static synchronized Date updateContacts(Context context, Account account,
                                            Collection<Subscription<VxCard,?>> rawContacts,
                                            Date lastSyncMarker,
                                            // It's a false positive.
                                            @SuppressWarnings("SameParameterValue") boolean isSyncContext) {
        Date currentSyncMarker = lastSyncMarker;
        final ContentResolver resolver = context.getContentResolver();
        final BatchOperation batchOperation = new BatchOperation(resolver);
        for (Subscription<VxCard,?> rawContact : rawContacts) {

            // The server returns a timestamp with each record. On the next sync we can just
            // ask for changes that have occurred since that most-recent change.
            if (currentSyncMarker == null || (rawContact.updated != null && rawContact.updated.after(currentSyncMarker))) {
                currentSyncMarker = rawContact.updated;

                // Send updated contact to database.
                processContact(context, resolver, account, rawContact, batchOperation, isSyncContext);

                // A sync adapter should batch operations on multiple contacts,
                // because it will make a dramatic performance difference.
                // (UI updates, etc)
                if (batchOperation.size() >= BATCH_SIZE) {
                    batchOperation.execute();
                }
            }
        }
        batchOperation.execute();

        return currentSyncMarker;
    }

    /**
     * Take a list of updated contacts and apply those changes to the
     * contacts database. Typically this list of contacts would have been
     * returned from the server, and we want to apply those changes locally.
     *
     * @param context        The context for getting resources.
     * @param resolver       Content resolver to access Contacts provider.
     * @param account        The username for the account.
     * @param rawContact     The contact to update.
     * @param batchOperation Optional batch to add operation to.
     */
    public static synchronized void processContact(Context context,
                                                   ContentResolver resolver,
                                                   Account account,
                                                   Subscription<VxCard,?> rawContact,
                                                   BatchOperation batchOperation,
                                                   boolean isSyncContext) {
        boolean noBatching = false;
        if (batchOperation == null) {
            batchOperation = new BatchOperation(resolver);
            noBatching = true;
        }
         // Check if we have this contact in the database.
        long rawContactId = lookupRawContact(resolver, rawContact.getUnique());
        if (rawContact.deleted != null) {
            if (rawContactId > 0) {
                deleteContact(rawContactId, batchOperation, isSyncContext);
            }
        } else {
            if (rawContactId > 0) {
                // Contact already exists

                if (rawContact.pub != null) {
                    updateContact(context, resolver, rawContact, rawContactId, batchOperation, isSyncContext);
                }
            } else {
                // New contact. Don't allow new contacts without a name.
                if (rawContact.pub == null) {
                    rawContact.pub = new VxCard();
                    rawContact.pub.fn = context.getString(R.string.default_contact_name, rawContact.getUnique());
                }

                addContact(context, account, rawContact, batchOperation, isSyncContext);
            }
        }

        if (noBatching) {
            batchOperation.execute();
        }
    }

    /**
     * Adds a single contact to the platform contacts provider.
     * This can be used to respond to a new contact found as part
     * of sync information returned from the server, or because a
     * user added a new contact.
     *
     * @param context        the Authenticator Activity context
     * @param account        the account the contact belongs to
     * @param rawContact     the User object
     * @param batchOperation allow us to batch together multiple operations
     *                       into a single provider call
     */
    private static void addContact(Context context, Account account, Subscription<VxCard,?> rawContact,
                                  BatchOperation batchOperation, boolean isSyncContext) {

        // Initiate adding data to contacts provider.

        // Create new RAW_CONTACTS record.
        final ContactOperations contactOp = ContactOperations.createNewContact(
                context, rawContact.getUnique(), account.name, batchOperation, isSyncContext);

        contactOp.addName(rawContact.pub.fn,
                rawContact.pub.n != null ? rawContact.pub.n.given : null,
                rawContact.pub.n != null ? rawContact.pub.n.surname : null)
                .addAvatar(rawContact.pub.photo != null ? rawContact.pub.photo.data : null);

        // Process private field, de-dupe emails and phones into VxCard.
        dedupe(rawContact);

        if (rawContact.pub.email != null) {
            for (VCard.Contact email: rawContact.pub.email) {
                contactOp.addEmail(email.uri);
            }
        }
        if (rawContact.pub.tel != null) {
            for (VCard.Contact phone: rawContact.pub.tel) {
                contactOp.addPhone(phone.uri, vcardTypeToDbType(phone.getType()));
            }
        }

        // This seems to be useless.
        // contactOp.addIm(rawContact.getUnique());

        // Actually create the profile.
        contactOp.addProfileAction(rawContact.getUnique());
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
     * @param rawContact     Tinode subscription
     * @param rawContactId   the unique Id for this rawContact in contacts
     *                       provider
     * @param batchOperation to allow to batch together multiple operations
     *                       into a single provider call
     */
    private static void updateContact(Context context, ContentResolver resolver, Subscription<VxCard,?> rawContact,
                                     long rawContactId, BatchOperation batchOperation, boolean isSyncContext) {

        boolean existingCellPhone = false;
        boolean existingHomePhone = false;
        boolean existingWorkPhone = false;
        boolean existingEmail = false;
        boolean existingAvatar = false;

        final ContactOperations contactOp = ContactOperations.updateExistingContact(context,
                rawContactId, batchOperation, isSyncContext);

        final Cursor c = resolver.query(DataQuery.CONTENT_URI, DataQuery.PROJECTION, DataQuery.SELECTION,
                new String[]{String.valueOf(rawContactId)}, null);
        if (c == null) {
            return;
        }

        // Add matches from .priv to (VCard).pub
        dedupe(rawContact);

        try {
            // Iterate over the existing rows of data, and update each one
            // with the information we received from the server.
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
                                rawContact.pub.n != null ? rawContact.pub.n.given : null,
                                rawContact.pub.n != null ? rawContact.pub.n.surname : null,
                                rawContact.pub.fn);
                        break;
                    case Phone.CONTENT_ITEM_TYPE:
                        final int type = c.getInt(DataQuery.COLUMN_PHONE_TYPE);
                        if (type == Phone.TYPE_MOBILE) {
                            existingCellPhone = true;
                            contactOp.updatePhone(c.getString(DataQuery.COLUMN_PHONE_NUMBER),
                                    rawContact.pub.getPhoneByType(VxCard.TYPE_MOBILE), uri);
                        } else if (type == Phone.TYPE_HOME) {
                            existingHomePhone = true;
                            contactOp.updatePhone(c.getString(DataQuery.COLUMN_PHONE_NUMBER),
                                    rawContact.pub.getPhoneByType(VxCard.TYPE_HOME), uri);
                        } else if (type == Phone.TYPE_WORK) {
                            existingWorkPhone = true;
                            contactOp.updatePhone(c.getString(DataQuery.COLUMN_PHONE_NUMBER),
                                    rawContact.pub.getPhoneByType(VxCard.TYPE_BUSINESS), uri);
                        }
                        break;
                    case Email.CONTENT_ITEM_TYPE:
                        existingEmail = true;
                        contactOp.updateEmail(rawContact.pub.email != null && rawContact.pub.email.length > 0 ?
                                        rawContact.pub.email[0].uri : null,
                                c.getString(DataQuery.COLUMN_EMAIL_ADDRESS), uri);
                        break;
                    case Photo.CONTENT_ITEM_TYPE:
                        existingAvatar = true;
                        contactOp.updateAvatar(rawContact.pub.photo != null ?
                                rawContact.pub.photo.data : null, uri);
                        break;

                }
            } // while
        } finally {
            c.close();
        }

        // Add the cell phone, if present and not updated above
        if (!existingCellPhone) {
            contactOp.addPhone(rawContact.pub.getPhoneByType(VxCard.TYPE_MOBILE), Phone.TYPE_MOBILE);
        }
        // Add the home phone, if present and not updated above
        if (!existingHomePhone) {
            contactOp.addPhone(rawContact.pub.getPhoneByType(VxCard.TYPE_HOME), Phone.TYPE_HOME);
        }
        // Add the work phone, if present and not updated above
        if (!existingWorkPhone) {
            contactOp.addPhone(rawContact.pub.getPhoneByType(VxCard.TYPE_WORK), Phone.TYPE_WORK);
        }
        // Add the email address, if present and not updated above
        if (!existingEmail) {
            contactOp.addEmail(rawContact.pub.email != null && rawContact.pub.email.length > 0 ?
                    rawContact.pub.email[0].uri : null);
        }
        // Add the avatar if we didn't update the existing avatar
        if (!existingAvatar) {
            contactOp.addAvatar(rawContact.pub.photo != null ? rawContact.pub.photo.data : null);
        }

        // If we don't have a status profile, then create one.  This could
        // happen for contacts that were created on the client - we don't
        // create the status profile until after the first sync...
        final long profileId = lookupProfile(resolver, rawContact.getUnique());
        if (profileId <= 0) {
            contactOp.addProfileAction(rawContact.getUnique());
        }
    }

    /**
     * Deletes a contact from the platform contacts provider. This method is used
     * both for contacts that were deleted locally and then that deletion was synced
     * to the server, and for contacts that were deleted on the server and the
     * deletion was synced to the client.
     *
     * @param id     the unique Id for this rawContact in contacts provider, locally issued
     */
    private static void deleteContact(long id, BatchOperation batchOperation, boolean isSyncContext) {
        batchOperation.add(ContactOperations.newDeleteCpo(
                ContentUris.withAppendedId(RawContacts.CONTENT_URI, id), isSyncContext).build());
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
    static void makeAccountContactsVisibile(Context context, Account account) {
        ContentValues values = new ContentValues();
        values.put(RawContacts.ACCOUNT_NAME, account.name);
        values.put(RawContacts.ACCOUNT_TYPE, Utils.ACCOUNT_TYPE);
        values.put(Settings.UNGROUPED_VISIBLE, 1);
        context.getContentResolver().insert(Settings.CONTENT_URI, values);
    }


    /**
     * Returns the RawContact id for a contact, or 0 if the user isn't found.
     *
     * @param resolver the content resolver to use
     * @param contact the contact value to lookup
     * @return the RawContact id, or 0 if not found
     */
    private static long lookupRawContact(final ContentResolver resolver, final String contact) {
        long rawContactId = 0;
        final Cursor c = resolver.query(
                UserIdQuery.CONTENT_URI,
                UserIdQuery.PROJECTION,
                UserIdQuery.SELECTION,
                new String[]{contact},
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
     * Returns the Data id a contact's profile row, or 0 if the user isn't found.
     *
     * @param resolver a content resolver
     * @param uid      server-issued unique ID of the contact
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
     * Returns the Lookup Key for a contact, or null if user isn't found.
     *
     * @param resolver a content resolver
     * @param uid      server-issued unique ID of the contact
     * @return the profile Data row id, or 0 if not found
     */
    public static String getLookupKey(ContentResolver resolver, String uid) {
        final Cursor c = resolver.query(Data.CONTENT_URI, ProfileQuery.PROJECTION, ProfileQuery.SELECTION,
                new String[]{uid}, null);

        if (c == null) {
            return null;
        }

        String lookupKey = null;
        try {
            if (c.moveToFirst()) {
                lookupKey = c.getString(ProfileQuery.COLUMN_LOOKUP_KEY);
            }
        } finally {
            c.close();
        }

        return lookupKey;
    }

    // Process Private field, add emails and phones to Public.
    private static void dedupe(Subscription<VxCard,?> rawContact) {
        if (rawContact.priv instanceof String[]) {
            Utils.ContactHolder ch = new Utils.ContactHolder((String[]) rawContact.priv);
            if (ch.emails != null || ch.phones != null) {
                if (rawContact.pub == null) {
                    rawContact.pub = new VxCard();
                }

                if (ch.emails != null) {
                    for (String email : ch.emails) {
                        rawContact.pub.addEmail(email, VCard.TYPE_OTHER);
                    }
                }

                if (ch.phones != null) {
                    for (String phone : ch.phones) {
                        rawContact.pub.addPhone(phone, VCard.TYPE_OTHER);
                    }
                }
            }
        }
    }

    private static int vcardTypeToDbType(VxCard.ContactType tp) {
        switch (tp) {
            case MOBILE:
                return Phone.TYPE_MOBILE;
            case HOME:
            case PERSONAL:
                return Phone.TYPE_HOME;
            case WORK:
            case BUSINESS:
                return Phone.TYPE_WORK;
        }
        return Phone.TYPE_OTHER;
    }

    /**
     * Constants for a query to find a contact given a user ID.
     */
    final private static class ProfileQuery {
        static final String[] PROJECTION = new String[]{Data._ID, Data.LOOKUP_KEY};
        static final int COLUMN_ID = 0;
        static final int COLUMN_LOOKUP_KEY = 1;
        static final String SELECTION =
                Data.MIMETYPE + "='" + Utils.MIME_TINODE_PROFILE + "' AND "
                        + Utils.DATA_PID + "=?";
    }

    /**
     * Constants for a query to find a contact given a user ID.
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