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
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.ListFragment;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.CursorLoader;
import androidx.loader.content.Loader;
import androidx.cursoradapter.widget.CursorAdapter;
import androidx.appcompat.widget.AppCompatImageView;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.TextAppearanceSpan;
import android.util.Log;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.AlphabetIndexer;
import android.widget.ListView;
import android.widget.SearchView;
import android.widget.SectionIndexer;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import co.tinode.tindroid.account.PhoneEmailImLoader;
import co.tinode.tindroid.account.Utils;
import co.tinode.tindroid.widgets.LetterTileDrawable;

public class ContactListFragment extends Fragment {

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
                return UiUtils.loadContactPhotoThumbnail(ContactListFragment.this, (String) data, getImageSize());
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
        return inflater.inflate(R.layout.fragment_contact_list, container, false);
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
        mAdapter = new ContactsAdapter(activity, null);
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

    private void handleItemClick(final ViewHolder tag) {
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
                } else if ((address = holder.getPhone()) != null) {
                    // Send an SMS with an invitation
                    Uri uri = Uri.fromParts("smsto", address, null);
                    Intent it = new Intent(Intent.ACTION_SENDTO, uri);
                    it.putExtra("sms_body", getString(R.string.tinode_invite_body));
                    startActivity(it);
                    done = true;
                } else if ((address = holder.getEmail()) != null) {
                    Uri uri = Uri.fromParts("mailto", address, null);
                    Intent it = new Intent(Intent.ACTION_SENDTO, uri);
                    it.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.tinode_invite_subject));
                    it.putExtra(Intent.EXTRA_TEXT, getString(R.string.tinode_invite_body));
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
        final Activity activity = getActivity();
        if (activity == null) {
            return;
        }

        menu.clear();

        // Inflate the menu items
        inflater.inflate(R.menu.menu_contacts, menu);

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
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (!TextUtils.isEmpty(mSearchTerm)) {
            // Saves the current search string
            outState.putString(SearchManager.QUERY, mSearchTerm);
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

    /**
     * This is a subclass of CursorAdapter that supports binding Cursor columns to a view layout.
     * If those items are part of search results, the search string is marked by highlighting the
     * query text. An {@link AlphabetIndexer} is used to allow quicker navigation up and down the
     * ListView.
     */
    private class ContactsAdapter extends RecyclerView.Adapter<ViewHolder> implements SectionIndexer {
        private AlphabetIndexer mAlphabetIndexer; // Stores the AlphabetIndexer instance
        private TextAppearanceSpan highlightTextSpan; // Stores the highlight text appearance style

        private Cursor mCursor;

        /**
         * Instantiates a new Contacts Adapter.
         *
         * @param context A context that has access to the app's layout.
         */
        ContactsAdapter(Context context, Cursor cursor) {
            setHasStableIds(true);
            swapCursor(cursor);

            // Loads a string containing the English alphabet. To fully localize the app, provide a
            // strings.xml file in res/values-<x> directories, where <x> is a locale. In the file,
            // define a string with android:name="alphabet" and contents set to all of the
            // alphabetic characters in the language in their proper sort order, in upper case if
            // applicable.
            final String alphabet = context.getString(R.string.alphabet);

            // Instantiates a new AlphabetIndexer bound to the column used to sort contact names.
            // The cursor is left null, because it has not yet been retrieved.
            mAlphabetIndexer = new AlphabetIndexer(null, ContactsQuery.SORT_KEY, alphabet);

            // Defines a span for highlighting the part of a display name that matches the search
            // string
            highlightTextSpan = new TextAppearanceSpan(getActivity(), R.style.searchTextHighlight);
        }

        /**
         * Identifies the start of the search string in the display name column of a Cursor row.
         * E.g. If displayName was "Adam" and search query (mSearchTerm) was "da" this would
         * return 1.
         *
         * @param displayName The contact display name.
         * @return The starting position of the search string in the display name, 0-based. The
         * method returns -1 if the string is not found in the display name, or if the search
         * string is empty or null.
         */
        private int indexOfSearchQuery(String displayName) {
            if (!TextUtils.isEmpty(mSearchTerm)) {
                return displayName.toLowerCase(Locale.getDefault()).indexOf(
                        mSearchTerm.toLowerCase(Locale.getDefault()));
            }

            return -1;
        }

        /**
         * Overrides newView() to inflate the list item views.
         */
        @Override
        @NonNull
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            return new ViewHolder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.contact_invite, parent, false));
        }

        /**
         * Binds data from the Cursor to the provided view.
         */
        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            if (!mCursor.moveToPosition(position)) {
                throw new IllegalArgumentException("Invalid cursor position " + position);
            }

            // ID of the contact
            holder.contact_id = mCursor.getInt(ContactsQuery.ID);

            // Get the thumbnail image Uri from the current Cursor row.
            final String photoUri = mCursor.getString(ContactsQuery.PHOTO_THUMBNAIL_DATA);

            final String displayName = mCursor.getString(ContactsQuery.DISPLAY_NAME);

            final int startIndex = indexOfSearchQuery(displayName);

            Utils.ContactHolder extra = (mPhEmImData != null ? mPhEmImData.get(holder.contact_id) : null);

            if (extra != null && extra.getImCount() > 0) {
                holder.inviteButton.setVisibility(View.GONE);
            } else {
                holder.inviteButton.setVisibility(View.VISIBLE);
            }

            String line2 = (extra != null) ? extra.bestContact() : null;

            if (startIndex == -1) {
                // If the user didn't do a search, or the search string didn't match a display
                // name, show the display name without highlighting
                holder.text1.setText(displayName);

                if (TextUtils.isEmpty(mSearchTerm)) {
                    if (TextUtils.isEmpty(line2)) {
                        // Search string is empty and we have no contacts to show
                        holder.text2.setVisibility(View.GONE);
                    } else {
                        holder.text2.setText(line2);
                        holder.text2.setVisibility(View.VISIBLE);
                    }
                } else {
                    // Shows a second line of text that indicates the search string matched
                    // something other than the display name
                    holder.text2.setVisibility(View.VISIBLE);
                }
            } else {
                // If the search string matched the display name, applies a SpannableString to
                // highlight the search string with the displayed display name

                // Wraps the display name in the SpannableString
                final SpannableString highlightedName = new SpannableString(displayName);

                // Sets the span to start at the starting point of the match and end at "length"
                // characters beyond the starting point
                highlightedName.setSpan(highlightTextSpan, startIndex,
                        startIndex + mSearchTerm.length(), 0);

                // Binds the SpannableString to the display name View object
                holder.text1.setText(highlightedName);

                // Since the search string matched the name, this hides the secondary message
                holder.text2.setVisibility(View.GONE);
            }

            // Clear the icon then load the thumbnail from photoUri in a background worker thread
            LetterTileDrawable tile = new LetterTileDrawable(requireContext())
                    .setIsCircular(true)
                    .setLetterAndColor(displayName, line2)
                    .setContactTypeAndColor(LetterTileDrawable.TYPE_PERSON);
            holder.icon.setImageDrawable(tile);
            mImageLoader.loadImage(getContext(), photoUri, holder.icon);
        }

        /**
         * Overrides swapCursor to move the new Cursor into the AlphabetIndex as well as the
         * CursorAdapter.
         */
        void swapCursor(Cursor newCursor) {
            if (newCursor == mCursor) {
                return;
            }

            final Cursor oldCursor = mCursor;

            // Update the AlphabetIndexer with new cursor as well
            mAlphabetIndexer.setCursor(newCursor);

            mCursor = newCursor;
            if (oldCursor != null) {
                oldCursor.close();
            }

            if (newCursor != null) {
                // notify the observers about the new cursor
                notifyDataSetChanged();
            } else {
                notifyItemRangeRemoved(0, getItemCount());
            }

            Activity activity = getActivity();
            if (activity != null) {
                activity.findViewById(android.R.id.empty)
                        .setVisibility(mAdapter.getItemCount() > 0 ? View.GONE : View.VISIBLE);
            }
        }

        @Override
        public int getItemCount() {
            if (mCursor == null) {
                return 0;
            }
            return mCursor.getCount();
        }

        @Override
        public long getItemId(int pos) {
            if (mCursor == null) {
                throw new IllegalStateException("Cursor is null.");
            }
            if (!mCursor.moveToPosition(pos)) {
                throw new IllegalStateException("Failed to move cursor to position " + pos);
            }

            return mCursor.getLong(ContactsQuery.ID);
        }

        /**
         * Defines the SectionIndexer.getSections() interface.
         */
        @Override
        public Object[] getSections() {
            return mAlphabetIndexer.getSections();
        }

        /**
         * Defines the SectionIndexer.getPositionForSection() interface.
         */
        @Override
        public int getPositionForSection(int i) {
            if (mCursor == null) {
                return 0;
            }
            return mAlphabetIndexer.getPositionForSection(i);
        }

        /**
         * Defines the SectionIndexer.getSectionForPosition() interface.
         */
        @Override
        public int getSectionForPosition(int i) {
            if (mCursor == null) {
                return 0;
            }
            return mAlphabetIndexer.getSectionForPosition(i);
        }
    }

    private class ViewHolder extends RecyclerView.ViewHolder {
        int contact_id;
        TextView text1;
        TextView text2;
        AppCompatImageView icon;
        View inviteButton;

        ViewHolder(@NonNull final View view) {
            super(view);

            text1 = view.findViewById(android.R.id.text1);
            text2 = view.findViewById(android.R.id.text2);
            icon = view.findViewById(android.R.id.icon);
            inviteButton = view.findViewById(R.id.buttonInvite);
            inviteButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    handleItemClick(ViewHolder.this);
                }
            });
            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    handleItemClick(ViewHolder.this);
                }
            });
        }
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
                mAdapter.swapCursor(data);
            }
        }

        @Override
        public void onLoaderReset(@NonNull Loader<Cursor> loader) {
            if (loader.getId() == ContactsQuery.CORE_QUERY_ID) {
                // When the loader is being reset, clear the cursor from the adapter. This allows the
                // cursor resources to be freed.
                mAdapter.swapCursor(null);
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

