package co.tinode.tindroid;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;

import androidx.annotation.NonNull;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.CursorLoader;
import androidx.loader.content.Loader;
import co.tinode.tindroid.account.Utils;

class ContactsLoaderCallback implements LoaderManager.LoaderCallbacks<Cursor> {
    static final String ARG_SEARCH_TERM = "searchTerm";

    private final int mID;
    private final Context mContext;
    private final CursorSwapper mAdapter;
    private String mSearchTerm;

    ContactsLoaderCallback(int loaderID, Context context, CursorSwapper adapter) {
        mID = loaderID;
        mContext = context;
        mAdapter = adapter;
    }

    @NonNull
    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        // If this is the loader for finding contacts in the Contacts Provider
        if (id == mID) {
            mSearchTerm = args != null ? args.getString(ARG_SEARCH_TERM) : null;

            String[] selectionArgs = null;
            String selection = ContactsQuery.SELECTION;
            if (mSearchTerm != null) {
                selection = ContactsQuery.SELECTION + ContactsQuery.SELECTION_FILTER;
                selectionArgs = new String[]{mSearchTerm + "%"};
            }

            return new CursorLoader(mContext,
                    ContactsQuery.CONTENT_URI,
                    ContactsQuery.PROJECTION,
                    selection,
                    selectionArgs,
                    ContactsQuery.SORT_ORDER);
        }

        throw new IllegalArgumentException("Unknown loader ID " + id);
    }

    @Override
    public void onLoadFinished(@NonNull Loader<Cursor> loader, Cursor data) {
        // This swaps the new cursor into the adapter.
        if (loader.getId() == mID) {
            mAdapter.swapCursor(data, mSearchTerm);
        }
    }

    @Override
    public void onLoaderReset(@NonNull Loader<Cursor> loader) {
        if (loader.getId() == mID) {
            // When the loader is being reset, clear the cursor from the adapter. This allows the
            // cursor resources to be freed.
            mAdapter.swapCursor(null, mSearchTerm);
        }
    }

    /**
     * This interface defines constants for the Cursor and CursorLoader, based on constants defined
     * in the {@link android.provider.ContactsContract.Contacts} class.
     */
    interface ContactsQuery {
        // A content URI for the Contacts table
        Uri CONTENT_URI = ContactsContract.Data.CONTENT_URI;

        // The selection clause for the CursorLoader query. The search criteria defined here
        // restrict results to contacts that have a display name and are linked to visible groups.
        String SELECTION = ContactsContract.Data.DISPLAY_NAME_PRIMARY + "<>'' AND " +
                ContactsContract.Data.MIMETYPE + "='" + Utils.MIME_TINODE_PROFILE + "'";

        // Search by keystrokes.
        String SELECTION_FILTER = " AND " + ContactsContract.Data.DISPLAY_NAME_PRIMARY + " LIKE ?";

        // The desired sort order for the returned Cursor.
        String SORT_ORDER = ContactsContract.Data.SORT_KEY_PRIMARY;

        // A list of columns that the Contacts Provider should return in the Cursor.
        String[] PROJECTION = {
                ContactsContract.Data._ID,
                ContactsContract.Data.LOOKUP_KEY,
                ContactsContract.Data.DISPLAY_NAME_PRIMARY,
                ContactsContract.Data.PHOTO_THUMBNAIL_URI,
                ContactsContract.Data.DATA1,

                // The sort order column for the returned Cursor, used by the AlphabetIndexer
                SORT_ORDER,
        };

        // The query column numbers which map to each value in the projection
        int ID = 0;
        // int LOOKUP_KEY = 1;
        int DISPLAY_NAME = 2;
        int PHOTO_THUMBNAIL_DATA = 3;
        int IM_ADDRESS = 4;

        int SORT_KEY = 5;
    }

    interface CursorSwapper {
        void swapCursor(Cursor cursor, String searchQuery);
    }
}
