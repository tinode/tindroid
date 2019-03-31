package co.tinode.tindroid;

import android.Manifest;
import android.app.Activity;
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.os.Build;
import android.os.Bundle;
import android.provider.ContactsContract.Contacts;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.loader.app.LoaderManager;

import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SearchView;
import android.widget.TextView;

import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;


public class ContactsFragment extends Fragment {

    // Defines a tag for identifying log entries
    private static final String TAG = "ContactsFragment";

    private static final int PERMISSIONS_REQUEST_READ_CONTACTS = 100;
    private static final int LOADER_ID = 101;

    private ContactsAdapter mAdapter; // The main query adapter
    private ImageLoader mImageLoader; // Handles loading the contact image in a background thread
    private String mSearchTerm; // Stores the current search query term

    // Callback which receives notifications of contacts loading status;
    private ContactsLoaderCallback mContactsLoaderCallback;

    // Observer to receive notifications while the fragment is active.
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

        final FragmentActivity activity = getActivity();
        if (activity == null) {
            return;
        }

        mImageLoader = UiUtils.getImageLoaderInstance(this);

        mContactsObserver = new ContentObserver(null) {
            @Override
            public void onChange(boolean selfChange) {
                // Content changed, refresh data
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        restartLoader();
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
    public void onViewCreated(@NonNull final View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        final Activity activity = getActivity();
        if (activity == null) {
            return;
        }

        RecyclerView rv = view.findViewById(R.id.contact_list);
        rv.setLayoutManager(new LinearLayoutManager(activity));
        rv.setHasFixedSize(true);
        rv.addItemDecoration(new DividerItemDecoration(activity, DividerItemDecoration.VERTICAL));
        mAdapter = new ContactsAdapter(activity, mImageLoader, new ContactsAdapter.ClickListener() {
            @Override
            public void onClick(String topicName, ContactsAdapter.ViewHolder holder) {
                Intent it = new Intent(activity, MessageActivity.class);
                it.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                it.putExtra("topic", topicName);
                startActivity(it);
            }
        });
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

        mContactsLoaderCallback = new ContactsLoaderCallback(LOADER_ID, activity, mAdapter);

        // Check for access to Contacts.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                activity.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.READ_CONTACTS}, PERMISSIONS_REQUEST_READ_CONTACTS);
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
    public void onResume() {
        super.onResume();

        FragmentActivity activity = getActivity();
        if (activity == null) {
            return;
        }

        try {
            // Receive updates when the Contacts db is changed
            activity.getContentResolver().registerContentObserver(ContactsLoaderCallback.ContactsQuery.CONTENT_URI,
                    true, mContactsObserver);

            // Refresh data
            restartLoader();

        } catch (SecurityException ex) {
            Log.i(TAG, "Missing permission", ex);
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

                restartLoader();

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
                searchView.clearFocus();

                if (mSearchTerm != null) {
                    mSearchTerm = null;
                    restartLoader();
                }
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

    // Restarts the loader. This triggers onCreateLoader(), which builds the
    // necessary content Uri from mSearchTerm.
    private void restartLoader() {
        final FragmentActivity activity = getActivity();
        if (activity == null) {
            return;
        }

        Bundle args = new Bundle();
        args.putString(ContactsLoaderCallback.ARG_SEARCH_TERM, mSearchTerm);
        LoaderManager.getInstance(activity).restartLoader(LOADER_ID, args, mContactsLoaderCallback);
    }
}

