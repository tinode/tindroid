package co.tinode.tindroid.account;


import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.OperationApplicationException;
import android.net.Uri;
import android.os.RemoteException;
import android.provider.ContactsContract;

import java.util.ArrayList;

public class ContactsManager {

    public static void addContact(ContentResolver resolver, TinodeAccount contact){
        ArrayList<ContentProviderOperation> ops = new ArrayList<>();

        ops.add(ContentProviderOperation
                .newInsert(addCallerIsSyncAdapterParameter(ContactsContract.RawContacts.CONTENT_URI, true))
                .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, contact.Login)
                .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, Utils.ACCOUNT_TYPE)
                .withValue(ContactsContract.RawContacts.AGGREGATION_MODE,
                        ContactsContract.RawContacts.AGGREGATION_MODE_DEFAULT)
                .build());

        ops.add(ContentProviderOperation
                .newInsert(addCallerIsSyncAdapterParameter(ContactsContract.Data.CONTENT_URI, true))
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(ContactsContract.Data.MIMETYPE,
                        ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, contact.Name)
                .build());

        ops.add(ContentProviderOperation
                .newInsert(addCallerIsSyncAdapterParameter(ContactsContract.Data.CONTENT_URI, true))
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Im.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.Im.DATA, contact.Uid)
                .withValue(ContactsContract.CommonDataKinds.Im.PROTOCOL,
                        ContactsContract.CommonDataKinds.Im.PROTOCOL_CUSTOM)
                .withValue(ContactsContract.CommonDataKinds.Im.CUSTOM_PROTOCOL, Utils.PROTOCOL)
                .build());

        try {
            resolver.applyBatch(ContactsContract.AUTHORITY, ops);
        } catch (OperationApplicationException | RemoteException e) {
            e.printStackTrace();
        }

    }

    private static Uri addCallerIsSyncAdapterParameter(Uri uri, boolean isSyncOperation) {
        if (isSyncOperation) {
            return uri.buildUpon()
                    .appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER,
                            "true").build();
        }
        return uri;
    }
}