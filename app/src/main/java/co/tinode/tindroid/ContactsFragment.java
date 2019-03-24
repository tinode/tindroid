package co.tinode.tindroid;

import android.Manifest;
import android.app.Activity;
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.ContactsContract.Contacts;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.CursorLoader;
import androidx.loader.content.Loader;

import android.text.TextUtils;
import android.util.Log;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SearchView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import co.tinode.tindroid.account.PhoneEmailImLoader;
import co.tinode.tindroid.account.Utils;

public class ContactsFragment extends Fragment {

    // Defines a tag for identifying log entries
    private static final String TAG = "ContactListFragment";

    private static final int PERMISSIONS_REQUEST_READ_CONTACTS = 100;

    private ContactsAdapter mAdapter; // The main query adapter
    private ImageLoader mImageLoader; // Handles loading the contact image in a background thread
    private String mSearchTerm; // Stores the current search query term

    // Contact selected listener that allows the activity holding this fragment to be notified of
    // a contact being selected
    private OnContactsInteractionListener mOnContactSelectedListener;

    // Callback which receives notifications of contacts loading status;
    private ContactsLoaderCallback mContactsLoaderCallback;
    // Callback for handling notifications of Phone, Email, IM contact loading;
    private PhEmImLoaderCallback mPhEmImLoaderCallback;
    private SparseArray<Utils.ContactHolder> mPhEmImData;

    // Observer to receive notifications while the fragment is active
    private ContentObserver mContactsObserver;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Let this fragment contribute menu items
        setHasOptionsMenu(true);

        // Check if this fragment is part of a two-pane set up or a single pane by reading a
        // boolean from the application resource directories. This lets allows us to easily specify
        // which screen sizes should use a two-pane layout by setting this boolean in the
        // corresponding resource size-qualified directory.
        // mIsTwoPaneLayout = getResources().getBoolean(R.bool.has_two_panes);

        if (savedInstanceState != null) {
            // If we're restoring state after this fragment was recreated then
            // retrieve previous search term and previously selected search
            // result.
            mSearchTerm = savedInstanceState.getString(SearchManager.QUERY);
        }

        mContactsLoaderCallback = new ContactsLoaderCallback();
        mPhEmImLoaderCallback = new PhEmImLoaderCallback();

        final FragmentActivity activity = getActivity();
        if (activity == null) {
            return;
        }

        /*
         * An ImageLoader object loads and resizes an image in the background and binds it to the
         * each item layout of the ListView. ImageLoader implements memory caching for each image,
         * which substantially improves refreshes of the ListView as the user scrolls through it.
         *
         * http://developer.android.com/training/displaying-bitmaps/
         */
        mImageLoader = new ImageLoader(UiUtils.getListPreferredItemHeight(this),
                activity.getSupportFragmentManager()) {
            @Override
            protected Bitmap processBitmap(Object data) {
                // This gets called in a background thread and passed the data from
                // ImageLoader.loadImage().
                return UiUtils.loadContactPhotoThumbnail(ContactsFragment.this, (String) data, getImageSize());
            }
        };

        // Set a placeholder loading image for the image loader
        mImageLoader.setLoadingImage(activity, R.drawable.ic_person_circle);

        mContactsObserver = new ContentObserver(null) {
            @Override
            public void onChange(boolean selfChange) {
                // Content changed, refresh data
                final FragmentActivity activity = getActivity();
                if (activity == null) {
                    return;
                }
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        final View fragment = getView();
                        if (fragment != null) {
                            fragment.findViewById(android.R.id.empty)
                                    .setVisibility(mAdapter.getItemCount() > 0 ? View.GONE : View.VISIBLE);
                        }
                        LoaderManager.getInstance(activity)
                                .restartLoader(ContactsQuery.PHEMIM_QUERY_ID, null, mPhEmImLoaderCallback);
                    }
                });
            }
        };
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the list fragment layout
        return inflater.inflate(R.layout.fragment_contacts, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        final Activity activity = getActivity();
        if (activity == null) {
            return;
        }

        RecyclerView rv = view.findViewById(R.id.contact_list);
        rv.setLayoutManager(new LinearLayoutManager(activity));
        rv.setHasFixedSize(true);
        rv.addItemDecoration(new DividerItemDecoration(activity, DividerItemDecoration.VERTICAL));
        mAdapter = new ContactsAdapter(activity, null, null);
        rv.setAdapter(mAdapter);

        rv.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView rv, int scrollState) {
                // Pause image loader to ensure smoother scrolling when flinging
                if (scrollState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    mImageLoader.setPauseWork(true);
                } else {
                    mImageLoader.setPauseWork(false);
                }
            }
        });

        // Check for access to Contacts.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                activity.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.READ_CONTACTS}, PERMISSIONS_REQUEST_READ_CONTACTS);
        }
    }

    private void handleItemClick(final ContactsAdapter.ViewHolder tag) {
        boolean done = false;
        if (mPhEmImData != null) {
            Utils.ContactHolder holder = mPhEmImData.get(tag.contact_id);
            if (holder != null) {
                String address = holder.getIm();
                if (address != null) {
                    Intent it = new Intent(getActivity(), MessageActivity.class);
                    it.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    it.putExtra("topic", address);
                    startActivity(it);
                    done = true;
                }
            }
        }

        if (!done) {
            Toast.makeText(getContext(), R.string.failed_to_invite, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == PERMISSIONS_REQUEST_READ_CONTACTS) {
            final Activity activity = getActivity();
            if (activity == null) {
                return;
            }

            if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
                ((TextView) activity.findViewById(android.R.id.empty)).setText(R.string.contacts_permission_denied);
            }
        }
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);

        try {
            // Assign callback listener which the holding activity must implement. This is used
            // so that when a contact item is interacted with (selected by the user) the holding
            // activity will be notified and can take further action such as populating the contact
            // detail pane (if in multi-pane layout) or starting a new activity with the contact
            // details (single pane layout).
            mOnContactSelectedListener = (OnContactsInteractionListener) context;
        } catch (ClassCastException e) {
            throw new ClassCastException(context.toString()
                    + " must implement OnContactsInteractionListener");
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        View view = getView();
        if (view != null) {
            view.findViewById(android.R.id.empty)
                    .setVisibility(mAdapter.getItemCount() > 0 ? View.GONE : View.VISIBLE);
        }

        FragmentActivity activity = getActivity();
        if (activity == null) {
            return;
        }

        try {
            // Receive updates when the Contacts db is changed
            activity.getContentResolver().registerContentObserver(ContactsQuery.CONTENT_URI,
                    true, mContactsObserver);
            // Refresh data
            LoaderManager.getInstance(activity).initLoader(ContactsQuery.PHEMIM_QUERY_ID,
                    null, mPhEmImLoaderCallback);
        } catch (SecurityException ex) {
            Log.d(TAG, "Missing permission", ex);
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        // In the case onPause() is called during a fling the image loader is
        // un-paused to let any remaining background work complete.
        mImageLoader.setPauseWork(false);

        final Activity activity = getActivity();
        if (activity == null) {
            return;
        }

        // Stop receiving update for changes to Contacts DB
        activity.getContentResolver().unregisterContentObserver(mContactsObserver);
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        menu.clear();
        inflater.inflate(R.menu.menu_contacts, menu);

        final Activity activity = getActivity();
        if (activity == null) {
            return;
        }

        // Locate the search item
        MenuItem searchItem = menu.findItem(R.id.action_search);

        // Retrieves the system search manager service
        final SearchManager searchManager =
                (SearchManager) activity.getSystemService(Context.SEARCH_SERVICE);

        // Retrieves the SearchView from the search menu item
        final SearchView searchView = (SearchView) searchItem.getActionView();
        // searchView.setFocusable(true);

        // Assign searchable info to SearchView
        searchView.setSearchableInfo(
                searchManager.getSearchableInfo(activity.getComponentName()));

        // Set listeners for SearchView
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String queryText) {
                // Nothing needs to happen when the user submits the search string
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                final FragmentActivity activity = getActivity();
                if (activity == null) {
                    return true;
                }

                // Called when the action bar search text has changed.  Updates
                // the search filter, and restarts the loader to do a new query
                // using the new search string.
                String newFilter = !TextUtils.isEmpty(newText) ? newText : null;

                // Don't do anything if the filter is empty
                if (mSearchTerm == null && newFilter == null) {
                    return true;
                }

                // Don't do anything if the new filter is the same as the current filter
                if (mSearchTerm != null && mSearchTerm.equals(newFilter)) {
                    return true;
                }

                // Updates current filter to new filter
                mSearchTerm = newFilter;

                // Restarts the loader. This triggers onCreateLoader(), which builds the
                // necessary content Uri from mSearchTerm.
                LoaderManager.getInstance(activity).restartLoader(ContactsQuery.CORE_QUERY_ID,
                        null, mContactsLoaderCallback);
                return true;
            }
        });

        searchItem.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(MenuItem menuItem) {
                searchView.setIconified(false);
                searchView.requestFocusFromTouch();
                return true;
            }

            @Override
            public boolean onMenuItemActionCollapse(MenuItem menuItem) {
                final FragmentActivity activity = getActivity();
                if (activity == null) {
                    return true;
                }

                searchView.clearFocus();

                // When the user collapses the SearchView the current search string is
                // cleared and the loader restarted.
                if (!TextUtils.isEmpty(mSearchTerm)) {
                    mOnContactSelectedListener.onSelectionCleared();
                }
                mSearchTerm = null;
                LoaderManager.getInstance(activity).restartLoader(ContactsQuery.CORE_QUERY_ID,
                        null, mContactsLoaderCallback);
                return true;
            }
        });


        if (mSearchTerm != null) {
            // If search term is already set here then this fragment is
            // being restored from a saved state and the search menu item
            // needs to be expanded and populated again.

            // Stores the search term (as it will be wiped out by
            // onQueryTextChange() when the menu item is expanded).
            final String savedSearchTerm = mSearchTerm;

            // Expands the search menu item
            searchItem.expandActionView();

            // Sets the SearchView to the previous search string
            searchView.setQuery(savedSearchTerm, false);
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        Activity activity = getActivity();
        if (activity == null) {
            return false;
        }

        if (item.getItemId() == R.id.action_add_contact) {
            // Sends a request to the People app to display the create contact screen
            final Intent intent = new Intent(Intent.ACTION_INSERT, Contacts.CONTENT_URI);
            startActivity(intent);
            return true;
        }
        return false;
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (!TextUtils.isEmpty(mSearchTerm)) {
            // Saves the current search string
            outState.putString(SearchManager.QUERY, mSearchTerm);
        }
    }

    /**
     * This interface must be implemented by any activity that loads this fragment. When an
     * interaction occurs, such as touching an item from the ListView, these callbacks will
     * be invoked to communicate the event back to the activity.
     */
    public interface OnContactsInteractionListener {
        /**
         * Called when a contact is selected from the ListView.
         *
         * @param contactUri The contact Uri.
         */
        void onContactSelected(Uri contactUri);

        /**
         * Called when the ListView selection is cleared like when
         * a contact search is taking place or is finishing.
         */
        void onSelectionCleared();
    }

    /**
     * This interface defines constants for the Cursor and CursorLoader, based on constants defined
     * in the {@link android.provider.ContactsContract.Contacts} class.
     */
    interface ContactsQuery {

        // An identifier for the base loader -- just contact names
        int CORE_QUERY_ID = 1;
        // ID of the loader for fetching emails, phones, and Tinode IM handles
        int PHEMIM_QUERY_ID = 2;

        // A content URI for the Contacts table
        Uri CONTENT_URI = Contacts.CONTENT_URI;
        // Uri CONTENT_URI = ContactsContract.Data.CONTENT_URI;

        // The search/filter query Uri
        Uri FILTER_URI = Contacts.CONTENT_FILTER_URI;

        // The selection clause for the CursorLoader query. The search criteria defined here
        // restrict results to contacts that have a display name and are linked to visible groups.
        // Notice that the search on the string provided by the user is implemented by appending
        // the search string to CONTENT_FILTER_URI.
        String SELECTION = Contacts.DISPLAY_NAME_PRIMARY + "<>'' AND " + Contacts.IN_VISIBLE_GROUP + "=1";

        // The desired sort order for the returned Cursor. In Android 3.0 and later, the primary
        // sort key allows for localization. In earlier versions. use the display name as the sort
        // key.
        String SORT_ORDER = Contacts.SORT_KEY_PRIMARY;

        // The projection for the CursorLoader query. This is a list of columns that the Contacts
        // Provider should return in the Cursor.
        String[] PROJECTION = {

                // The contact's row id
                Contacts._ID,

                // A pointer to the contact that is guaranteed to be more permanent than _ID. Given
                // a contact's current _ID value and LOOKUP_KEY, the Contacts Provider can generate
                // a "permanent" contact URI.
                Contacts.LOOKUP_KEY,

                // In platform version 3.0 and later, the Contacts table contains
                // DISPLAY_NAME_PRIMARY, which either contains the contact's displayable name or
                // some other useful identifier such as an email address.
                Contacts.DISPLAY_NAME_PRIMARY,

                // In Android 3.0 and later, the thumbnail image is pointed to by
                // PHOTO_THUMBNAIL_URI.
                Contacts.PHOTO_THUMBNAIL_URI,

                // The sort order column for the returned Cursor, used by the AlphabetIndexer
                SORT_ORDER,
        };

        // The query column numbers which map to each value in the projection
        int ID = 0;
        int LOOKUP_KEY = 1;
        int DISPLAY_NAME = 2;
        int PHOTO_THUMBNAIL_DATA = 3;
        int SORT_KEY = 4;
    }

    private class ContactsLoaderCallback implements LoaderManager.LoaderCallbacks<Cursor> {
        @NonNull
        @Override
        public Loader<Cursor> onCreateLoader(int id, Bundle args) {
            final Activity activity = getActivity();
            // If this is the loader for finding contacts in the Contacts Provider
            if (id == ContactsQuery.CORE_QUERY_ID && activity != null) {
                Uri contentUri;

                // There are two types of searches, one which displays all contacts and
                // one which filters contacts by a search query. If mSearchTerm is set
                // then a search query has been entered and the latter should be used.

                if (mSearchTerm == null) {
                    // Since there's no search string, use the content URI that searches the entire
                    // Contacts table
                    contentUri = ContactsQuery.CONTENT_URI;
                } else {
                    // Since there's a search string, use the special content Uri that searches the
                    // Contacts table. The URI consists of a base Uri and the search string.
                    contentUri = Uri.withAppendedPath(ContactsQuery.FILTER_URI, Uri.encode(mSearchTerm));
                }

                // Returns a new CursorLoader for querying the Contacts table. No arguments are used
                // for the selection clause. The search string is either encoded onto the content URI,
                // or no contacts search string is used. The other search criteria are constants. See
                // the ContactsQuery interface.
                return new CursorLoader(activity,
                        contentUri,
                        ContactsQuery.PROJECTION,
                        ContactsQuery.SELECTION,
                        null,
                        ContactsQuery.SORT_ORDER);
            }

            throw new IllegalArgumentException("Unknown loader ID "+id);
        }

        @Override
        public void onLoadFinished(@NonNull Loader<Cursor> loader, Cursor data) {
            // This swaps the new cursor into the adapter.
            if (loader.getId() == ContactsQuery.CORE_QUERY_ID) {
                mAdapter.resetContent(data, mSearchTerm);
            }
        }

        @Override
        public void onLoaderReset(@NonNull Loader<Cursor> loader) {
            if (loader.getId() == ContactsQuery.CORE_QUERY_ID) {
                // When the loader is being reset, clear the cursor from the adapter. This allows the
                // cursor resources to be freed.
                mAdapter.resetContent(null, mSearchTerm);
            }
        }
    }

    class PhEmImLoaderCallback implements LoaderManager.LoaderCallbacks<SparseArray<Utils.ContactHolder>> {

        @NonNull
        @Override
        public Loader<SparseArray<Utils.ContactHolder>> onCreateLoader(int id, Bundle args) {
            return new PhoneEmailImLoader(getContext());
        }

        @Override
        public void onLoadFinished(@NonNull Loader<SparseArray<Utils.ContactHolder>> loader,
                                   SparseArray<Utils.ContactHolder> data) {
            mPhEmImData = data;

            final FragmentActivity activity = getActivity();
            if (activity == null) {
                return;
            }

            // Restart the main contacts loader.
            LoaderManager.getInstance(activity).restartLoader(ContactsQuery.CORE_QUERY_ID,
                    null, mContactsLoaderCallback);
        }

        @Override
        public void onLoaderReset(@NonNull Loader<SparseArray<Utils.ContactHolder>> loader) {
            mPhEmImData = null;
        }
    }
}

