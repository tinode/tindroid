/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package co.tinode.tindroid;

import android.Manifest;
import android.annotation.SuppressLint;
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
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.ListFragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.view.MenuItemCompat;
import android.support.v4.widget.CursorAdapter;
import android.support.v7.widget.AppCompatImageView;
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

import co.tinode.tindroid.account.PhoneEmailImLoader;
import co.tinode.tindroid.account.Utils;
import co.tinode.tindroid.widgets.LetterTileDrawable;

/**
 * This fragment displays a list of contacts stored in the Contacts Provider. Each item in the list
 * shows the contact's thumbnail photo and display name. On devices with large screens, this
 * fragment's UI appears as part of a two-pane layout, along with the UI of. On smaller screens,
 * this fragment's UI appears as a single pane.
 * <p>
 * This Fragment retrieves contacts based on a search string. If the user doesn't enter a search
 * string, then the list contains all the contacts in the Contacts Provider. If the user enters a
 * search string, then the list contains only those contacts whose data matches the string. The
 * Contacts Provider itself controls the matching algorithm, which is a "substring" search: if the
 * search string is a substring of any of the contacts data, then there is a match.
 * <p>
 * On newer API platforms, the search is implemented in a SearchView in the ActionBar; as the user
 * types the search string, the list automatically refreshes to display results ("type to filter").
 * On older platforms, the user must enter the full string and trigger the search. In response, the
 * trigger starts a new Activity which loads a fresh instance of this fragment. The resulting UI
 * displays the filtered list and disables the search feature to prevent furthering searching.
 */
public class ContactListFragment extends ListFragment {

    // Defines a tag for identifying log entries
    private static final String TAG = "ContactListFragment";

    private static final int PERMISSIONS_REQUEST_READ_CONTACTS = 100;

    // Bundle key for saving previously selected search result item
    private static final String STATE_PREVIOUSLY_SELECTED_KEY =
            "co.tinode.tindroid.SELECTED_ITEM";

    private ContactsAdapter mAdapter; // The main query adapter
    private ImageLoader mImageLoader; // Handles loading the contact image in a background thread
    private String mSearchTerm; // Stores the current search query term

    // Contact selected listener that allows the activity holding this fragment to be notified of
    // a contact being selected
    private OnContactsInteractionListener mOnContactSelectedListener;

    // Callback which receives notifications of contacts loding status;
    private ContactsLoaderCallback mContactsLoaderCallback;
    // Callback for handling notifications of Phone, Email, IM contact loading;
    private PhEmImLoaderCallback mPhEmImLoaderCallback;
    private SparseArray<Utils.ContactHolder> mPhEmImData;

    // Stores the previously selected search item so that on a configuration change the same item
    // can be reselected again
    private int mPreviouslySelectedSearchItem = 0;

    // Observer to receive notifications while the fragment is active
    private ContentObserver mContactsObserver;

    /**
     * Fragments require an empty constructor.
     */
    public ContactListFragment() {
    }

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
            mPreviouslySelectedSearchItem =
                    savedInstanceState.getInt(STATE_PREVIOUSLY_SELECTED_KEY, 0);
        }

        mContactsLoaderCallback = new ContactsLoaderCallback();
        mPhEmImLoaderCallback = new PhEmImLoaderCallback();

        FragmentActivity activity = getActivity();
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
        mImageLoader = new ImageLoader(activity, UiUtils.getListPreferredItemHeight(this),
                activity.getSupportFragmentManager()) {
            @Override
            protected Bitmap processBitmap(Object data) {
                // This gets called in a background thread and passed the data from
                // ImageLoader.loadImage().
                return UiUtils.loadContactPhotoThumbnail(ContactListFragment.this, (String) data, getImageSize());
            }
        };

        // Set a placeholder loading image for the image loader
        mImageLoader.setLoadingImage(R.drawable.ic_person_circle);

        mContactsObserver = new ContentObserver(null) {
            @Override
            public void onChange(boolean selfChange) {
                // Content changed, refresh data
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        getLoaderManager().initLoader(ContactsQuery.PHEMIM_QUERY_ID, null, mPhEmImLoaderCallback);
                    }
                });
            }
        };
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the list fragment layout
        return inflater.inflate(R.layout.fragment_contact_list, container, false);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        // Set up ListView, create and assign adapter and set some listeners.
        assignAdapter();

        getListView().setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                handleItemClick((ContactsAdapter.ViewHolder) view.getTag());
            }
        });

        getListView().setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView absListView, int scrollState) {
                // Pause image loader to ensure smoother scrolling when flinging
                if (scrollState == AbsListView.OnScrollListener.SCROLL_STATE_FLING) {
                    mImageLoader.setPauseWork(true);
                } else {
                    mImageLoader.setPauseWork(false);
                }
            }

            @Override
            public void onScroll(AbsListView absListView, int i, int i1, int i2) {
            }
        });

        //if (mIsTwoPaneLayout) {
        // In a two-pane layout, set choice mode to single as there will be two panes
        // when an item in the ListView is selected it should remain highlighted while
        // the content shows in the second pane.
        //    getListView().setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        //}
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

                if (!done && ((address = holder.getPhone()) != null)) {
                    // Send an SMS with an invitation
                    Uri uri = Uri.fromParts("smsto", address, null);
                    Intent it = new Intent(Intent.ACTION_SENDTO, uri);
                    it.putExtra("sms_body", getString(R.string.tinode_invite_body));
                    startActivity(it);
                    done = true;
                }
                if (!done && ((address = holder.getEmail()) != null)) {
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

    /**
     * A wrapper which ensures that run-time permission to access contacts is granted.
     */
    private void assignAdapter() {
        // Check the SDK version and whether the permission is already granted or not.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                getActivity().checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.READ_CONTACTS}, PERMISSIONS_REQUEST_READ_CONTACTS);
            //After this point you wait for callback in onRequestPermissionsResult(int, String[], int[]) overriden method
        } else {
            // Create the main contacts adapter
            mAdapter = new ContactsAdapter(getActivity());
            setListAdapter(mAdapter);
            // mAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == PERMISSIONS_REQUEST_READ_CONTACTS) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission is granted
                assignAdapter();
            } else {
                ((TextView) getActivity().findViewById(android.R.id.empty)).setText(R.string.contacts_permission_denied);
            }
        }
    }

    @Override
    public void onAttach(Context context) {
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

        Activity activity = getActivity();
        if (activity == null) {
            return;
        }

        try {
            // Receive updates when the Contacts db is changed
            activity.getContentResolver().registerContentObserver(ContactsQuery.CONTENT_URI, true, mContactsObserver);
            // Refresh data
            getLoaderManager().initLoader(ContactsQuery.PHEMIM_QUERY_ID, null, mPhEmImLoaderCallback);
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

        // Stop receiving update for changes to Contacts DB
        getActivity().getContentResolver().unregisterContentObserver(mContactsObserver);
    }

    /**
     * Called when ListView selection is cleared, for example
     * when search mode is finished and the currently selected
     * contact should no longer be selected.
     */
    private void onSelectionCleared() {
        // Uses callback to notify activity this contains this fragment
        mOnContactSelectedListener.onSelectionCleared();

        // Clears currently checked item
        getListView().clearChoices();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        menu.clear();

        // Inflate the menu items
        inflater.inflate(R.menu.menu_contacts, menu);

        // Locate the search item
        MenuItem searchItem = menu.findItem(R.id.action_search);

        // Retrieves the system search manager service
        final SearchManager searchManager =
                (SearchManager) getActivity().getSystemService(Context.SEARCH_SERVICE);

        // Retrieves the SearchView from the search menu item
        final SearchView searchView = (SearchView) searchItem.getActionView();
        // searchView.setFocusable(true);

        // Assign searchable info to SearchView
        searchView.setSearchableInfo(
                searchManager.getSearchableInfo(getActivity().getComponentName()));

        // Set listeners for SearchView
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String queryText) {
                // Nothing needs to happen when the user submits the search string
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
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
                getLoaderManager().restartLoader(ContactsQuery.CORE_QUERY_ID, null, mContactsLoaderCallback);
                return true;
            }
        });

        MenuItemCompat.setOnActionExpandListener(searchItem, new MenuItemCompat.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(MenuItem menuItem) {
                Log.d(TAG, "EXPAND onMenuItemActionCollapse");
                searchView.setIconified(false);
                searchView.requestFocusFromTouch();
                return true;
            }

            @Override
            public boolean onMenuItemActionCollapse(MenuItem menuItem) {
                Log.d(TAG, "COLLAPSE onMenuItemActionCollapse");
                searchView.clearFocus();
                // When the user collapses the SearchView the current search string is
                // cleared and the loader restarted.
                if (!TextUtils.isEmpty(mSearchTerm)) {
                    onSelectionCleared();
                }
                mSearchTerm = null;
                getLoaderManager().restartLoader(ContactsQuery.CORE_QUERY_ID, null, mContactsLoaderCallback);
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
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (!TextUtils.isEmpty(mSearchTerm)) {
            // Saves the current search string
            outState.putString(SearchManager.QUERY, mSearchTerm);

            // Saves the currently selected contact
            outState.putInt(STATE_PREVIOUSLY_SELECTED_KEY, getListView().getCheckedItemPosition());
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            // Sends a request to the People app to display the create contact screen
            case R.id.action_add_contact:
                final Intent intent = new Intent(Intent.ACTION_INSERT, Contacts.CONTENT_URI);
                startActivity(intent);
                return true;

            // For platforms earlier than Android 3.0, triggers the search activity
            case R.id.action_search:
                return false;

            case R.id.action_settings:
                ((ContactsActivity) getActivity()).showAccountInfoFragment();
                break;

            case R.id.action_about:
                DialogFragment about = new AboutDialogFragment();
                about.show(getFragmentManager(), "about");
                return true;
        }
        return false;
    }

    /**
     * Refresh single row in the list view. Row is identified by the item ID
     *
     * @param id id of the row to refresh
     * @return true if it was refreshed, false otherwise (i.e. the item
     * with the give ID does not exist or is not visible)
     */
    public boolean refreshItemById(long id) {
        boolean result = false;
        if (id > 0) {
            ListView list = getListView();

            int start = list.getFirstVisiblePosition();
            for (int i = start, j = list.getLastVisiblePosition(); i <= j; i++) {
                if (id == list.getItemIdAtPosition(i)) {
                    View view = list.getChildAt(i - start);
                    list.getAdapter().getView(i, view, list);
                    result = true;
                    break;
                }
            }
        }
        return result;
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
    public interface ContactsQuery {

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
        @SuppressLint("InlinedApi")
        String SELECTION = Contacts.DISPLAY_NAME_PRIMARY + "<>'' AND " + Contacts.IN_VISIBLE_GROUP + "=1";

        // The desired sort order for the returned Cursor. In Android 3.0 and later, the primary
        // sort key allows for localization. In earlier versions. use the display name as the sort
        // key.
        String SORT_ORDER = Contacts.SORT_KEY_PRIMARY;

        // The projection for the CursorLoader query. This is a list of columns that the Contacts
        // Provider should return in the Cursor.
        @SuppressLint("InlinedApi")
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
    private class ContactsAdapter extends CursorAdapter implements SectionIndexer {
        private LayoutInflater mInflater; // Stores the layout inflater
        private AlphabetIndexer mAlphabetIndexer; // Stores the AlphabetIndexer instance
        private TextAppearanceSpan highlightTextSpan; // Stores the highlight text appearance style

        /**
         * Instantiates a new Contacts Adapter.
         *
         * @param context A context that has access to the app's layout.
         */
        public ContactsAdapter(Context context) {
            super(context, null, 0);

            // Stores inflater for use later
            mInflater = LayoutInflater.from(context);

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
        public View newView(Context context, Cursor cursor, ViewGroup viewGroup) {
            // Inflates the list item layout.
            final View itemLayout =
                    mInflater.inflate(R.layout.contact_invite, viewGroup, false);

            // Creates a new ViewHolder in which to store handles to each view resource. This
            // allows bindView() to retrieve stored references instead of calling findViewById for
            // each instance of the layout.
            final ViewHolder holder = new ViewHolder();
            holder.text1 = (TextView) itemLayout.findViewById(android.R.id.text1);
            holder.text2 = (TextView) itemLayout.findViewById(android.R.id.text2);
            holder.icon = (AppCompatImageView) itemLayout.findViewById(android.R.id.icon);
            holder.inviteButton = itemLayout.findViewById(R.id.buttonInvite);
            holder.inviteButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    View item = ((View) v.getParent());
                    handleItemClick((ViewHolder) item.getTag());
                }
            });
            // Stores the resourceHolder instance in itemLayout. This makes resourceHolder
            // available to bindView and other methods that receive a handle to the item view.
            itemLayout.setTag(holder);

            // Returns the item layout view
            return itemLayout;
        }

        /**
         * Binds data from the Cursor to the provided view.
         */
        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            // Gets handles to individual view resources
            final ViewHolder holder = (ViewHolder) view.getTag();

            // ID of the contact
            holder.contact_id = cursor.getInt(ContactsQuery.ID);

            // Get the thumbnail image Uri from the current Cursor row.
            final String photoUri = cursor.getString(ContactsQuery.PHOTO_THUMBNAIL_DATA);

            final String displayName = cursor.getString(ContactsQuery.DISPLAY_NAME);

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
            mImageLoader.loadImage(photoUri, holder.icon);
        }

        /**
         * Overrides swapCursor to move the new Cursor into the AlphabetIndex as well as the
         * CursorAdapter.
         */
        @Override
        public Cursor swapCursor(Cursor newCursor) {
            // Update the AlphabetIndexer with new cursor as well
            mAlphabetIndexer.setCursor(newCursor);
            return super.swapCursor(newCursor);
        }

        /**
         * An override of getCount that simplifies accessing the Cursor. If the Cursor is null,
         * getCount returns zero. As a result, no test for Cursor == null is needed.
         */
        @Override
        public int getCount() {
            if (getCursor() == null) {
                return 0;
            }
            return super.getCount();
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
            if (getCursor() == null) {
                return 0;
            }
            return mAlphabetIndexer.getPositionForSection(i);
        }

        /**
         * Defines the SectionIndexer.getSectionForPosition() interface.
         */
        @Override
        public int getSectionForPosition(int i) {
            if (getCursor() == null) {
                return 0;
            }
            return mAlphabetIndexer.getSectionForPosition(i);
        }

        /**
         * A class that defines fields for each resource ID in the list item layout. This allows
         * ContactsAdapter.newView() to store the IDs once, when it inflates the layout, instead of
         * calling findViewById in each iteration of bindView.
         */
        private class ViewHolder {
            int contact_id;
            TextView text1;
            TextView text2;
            AppCompatImageView icon;
            View inviteButton;
        }
    }

    private class ContactsLoaderCallback implements LoaderManager.LoaderCallbacks<Cursor> {
        @Override
        public Loader<Cursor> onCreateLoader(int id, Bundle args) {

            // If this is the loader for finding contacts in the Contacts Provider
            if (id == ContactsQuery.CORE_QUERY_ID) {
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
                return new CursorLoader(getActivity(),
                        contentUri,
                        ContactsQuery.PROJECTION,
                        ContactsQuery.SELECTION,
                        null,
                        ContactsQuery.SORT_ORDER);
            }
            return null;
        }

        @Override
        public void onLoadFinished(Loader<Cursor> loader, Cursor data) {

            // This swaps the new cursor into the adapter.
            if (loader.getId() == ContactsQuery.CORE_QUERY_ID) {
                mAdapter.swapCursor(data);
            }
        }

        @Override
        public void onLoaderReset(Loader<Cursor> loader) {
            if (loader.getId() == ContactsQuery.CORE_QUERY_ID) {
                // When the loader is being reset, clear the cursor from the adapter. This allows the
                // cursor resources to be freed.
                mAdapter.swapCursor(null);
            }
        }
    }

    class PhEmImLoaderCallback implements LoaderManager.LoaderCallbacks<SparseArray<Utils.ContactHolder>> {

        @Override
        public Loader<SparseArray<Utils.ContactHolder>> onCreateLoader(int id, Bundle args) {
            if (id == ContactsQuery.PHEMIM_QUERY_ID) {
                return new PhoneEmailImLoader(getContext());
            }
            return null;
        }

        @Override
        public void onLoadFinished(Loader<SparseArray<Utils.ContactHolder>> loader,
                                   SparseArray<Utils.ContactHolder> data) {
            mPhEmImData = data;

            // If there's a previously selected search item from a saved state then don't bother
            // initializing the loader as it will be restarted later when the query is populated into
            // the action bar search view (see onQueryTextChange() in onCreateOptionsMenu()).
            if (mPreviouslySelectedSearchItem == 0) {
                //Initialize the loader, and create a loader identified by ContactsQuery.QUERY_ID
                getLoaderManager().initLoader(ContactsQuery.CORE_QUERY_ID, null, mContactsLoaderCallback);
            }

        }

        @Override
        public void onLoaderReset(Loader<SparseArray<Utils.ContactHolder>> loader) {
            mPhEmImData = null;
        }
    }
}

