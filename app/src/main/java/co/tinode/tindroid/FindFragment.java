package co.tinode.tindroid;

import android.Manifest;
import android.app.Activity;
import android.app.SearchManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.provider.ContactsContract;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SearchView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.ShareActionProvider;
import androidx.core.view.MenuItemCompat;
import androidx.fragment.app.Fragment;
import androidx.loader.app.LoaderManager;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import co.tinode.tindroid.media.VxCard;
import co.tinode.tindroid.widgets.CircleProgressView;
import co.tinode.tinodesdk.FndTopic;
import co.tinode.tinodesdk.NotConnectedException;
import co.tinode.tinodesdk.NotSynchronizedException;
import co.tinode.tinodesdk.PromisedReply;
import co.tinode.tinodesdk.Tinode;
import co.tinode.tinodesdk.model.MetaSetDesc;
import co.tinode.tinodesdk.model.MsgGetMeta;
import co.tinode.tinodesdk.model.MsgSetMeta;
import co.tinode.tinodesdk.model.ServerMessage;
import co.tinode.tinodesdk.model.Subscription;

/**
 * FindFragment contains a RecyclerView with results from searching local Contacts and remote 'fnd' topic.
 */
public class FindFragment extends Fragment implements UiUtils.ProgressIndicator {

    private static final String TAG = "FindFragment";

    // Delay in milliseconds between the last keystroke and time when the query is sent to the server.
    private static final int SEARCH_REQUEST_DELAY = 1000;

    private static final int LOADER_ID = 104;

    // Minimum allowed length of a search tag (server-enforced).
    private static final int MIN_TAG_LENGTH = 4;

    private FndTopic<VxCard> mFndTopic;
    private FndListener mFndListener;

    private String mSearchTerm; // Stores the current search query term
    private ImageLoader mImageLoader; // Handles loading the contact image in a background thread
    private FindAdapter mAdapter = null;

    // Callback which receives notifications of contacts loading status;
    private ContactsLoaderCallback mContactsLoaderCallback;

    private CircleProgressView mProgress = null;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mFndTopic = Cache.getTinode().getOrCreateFndTopic();
        mFndListener = new FndListener();

        if (savedInstanceState != null) {
            mSearchTerm = savedInstanceState.getString(SearchManager.QUERY);
        }

        mImageLoader = UiUtils.getImageLoaderInstance(this);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        setHasOptionsMenu(true);

        return inflater.inflate(R.layout.fragment_contacts, container, false);
    }

    @Override
    public void onViewCreated(@NonNull final View fragment, Bundle savedInstanceState) {
        final AppCompatActivity activity = (AppCompatActivity) getActivity();
        if (activity == null) {
            return;
        }

        RecyclerView rv = fragment.findViewById(R.id.chat_list);
        rv.setLayoutManager(new LinearLayoutManager(activity));
        rv.setHasFixedSize(true);
        rv.addItemDecoration(new DividerItemDecoration(activity, DividerItemDecoration.VERTICAL));
        mAdapter = new FindAdapter(activity, mImageLoader, new FindAdapter.ClickListener() {
            @Override
            public void onCLick(final String topicName) {
                Intent intent = new Intent(activity, MessageActivity.class);
                // See discussion here: https://github.com/tinode/tindroid/issues/39
                intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                intent.putExtra("topic", topicName);
                activity.startActivity(intent);
                // Remove StartChatActivity from stack.
                activity.finish();
            }
        });

        mContactsLoaderCallback = new ContactsLoaderCallback(LOADER_ID, activity, mAdapter);

        mAdapter.swapCursor(null, mSearchTerm);
        mAdapter.setContactsPermission(UiUtils.isPermissionGranted(activity, Manifest.permission.READ_CONTACTS));
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

        mProgress = fragment.findViewById(R.id.progressCircle);
    }

    @Override
    public void onResume() {
        super.onResume();

        final View fragment = getView();
        if (fragment == null) {
            return;
        }

        try {
            Cache.attachFndTopic(mFndListener)
                    .thenApply(new PromisedReply.SuccessListener<ServerMessage>() {
                        @Override
                        public PromisedReply<ServerMessage> onSuccess(ServerMessage result) {
                            return null;
                        }
                    }, new PromisedReply.FailureListener<ServerMessage>() {
                        @Override
                        public PromisedReply<ServerMessage> onFailure(Exception err) {
                            Log.w(TAG, "Error subscribing to 'fnd' topic", err);
                            return null;
                        }
                    });
        } catch (NotSynchronizedException ignored) {
        } catch (NotConnectedException ignored) {
            /* offline - ignored */
            Toast.makeText(fragment.getContext(), R.string.no_connection, Toast.LENGTH_SHORT).show();
        } catch (Exception err) {
            Log.i(TAG, "Subscription failed", err);
            Toast.makeText(fragment.getContext(), R.string.action_failed, Toast.LENGTH_LONG).show();
        }

        mAdapter.resetFound(getActivity(), mSearchTerm);
        // Refresh cursor.
        restartLoader(mSearchTerm);
    }

    @Override
    public void onPause() {
        super.onPause();

        // Let it finish.
        mImageLoader.setPauseWork(false);

        if (mFndTopic != null) {
            mFndTopic.setListener(null);
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
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        menu.clear();
        inflater.inflate(R.menu.menu_contacts, menu);

        final Activity activity = getActivity();
        if (activity == null) {
            return;
        }

        final SearchManager searchManager =
                (SearchManager) activity.getSystemService(Activity.SEARCH_SERVICE);

        if (searchManager == null) {
            return;
        }

        // Setting up SearchView

        // Locate the search item
        MenuItem searchItem = menu.findItem(R.id.action_search);

        // Retrieves the SearchView from the search menu item
        final SearchView searchView = (SearchView) searchItem.getActionView();
        searchView.setQueryHint(getResources().getString(R.string.hint_search_tags));
        // Assign searchable info to SearchView
        searchView.setSearchableInfo(
                searchManager.getSearchableInfo(activity.getComponentName()));
        searchView.setFocusable(true);
        searchView.setFocusableInTouchMode(true);

        // Set listeners for SearchView
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            private Handler mHandler;
            @Override
            public boolean onQueryTextSubmit(String queryText) {
                Log.i(TAG, "onQueryTextSubmit='"+queryText+"'");

                if (mHandler != null) {
                    mHandler.removeCallbacksAndMessages(null);
                }

                mSearchTerm = doSearch(queryText);

                return true;
            }

            @Override
            public boolean onQueryTextChange(final String queryText) {

                if (mHandler == null) {
                    mHandler = new Handler();
                } else {
                    mHandler.removeCallbacksAndMessages(null);
                }

                // Delay search in case of more input
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        mSearchTerm = doSearch(queryText);
                    }
                }, SEARCH_REQUEST_DELAY);
                return true;
            }
        });

        searchItem.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(MenuItem menuItem) {
                searchView.setIconified(false);
                searchView.requestFocus();
                searchView.requestFocusFromTouch();
                return true;
            }

            @Override
            public boolean onMenuItemActionCollapse(MenuItem menuItem) {
                searchView.clearFocus();
                mSearchTerm = null;
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
        final Activity activity = getActivity();
        if (activity == null) {
            return true;
        }
        Intent intent;
        switch (item.getItemId()) {
            case R.id.action_add_contact:
                intent = new Intent(Intent.ACTION_INSERT, ContactsContract.Contacts.CONTENT_URI);
                startActivity(intent);
                return true;

            case R.id.action_invite:
                ShareActionProvider provider = (ShareActionProvider) MenuItemCompat.getActionProvider(item);
                if (provider == null) {
                    return false;
                }
                intent = new Intent(Intent.ACTION_SEND);
                intent.setType("text/plain");
                intent.putExtra(Intent.EXTRA_SUBJECT, activity.getResources().getString(R.string.tinode_invite_subject));
                intent.putExtra(Intent.EXTRA_TEXT, activity.getResources().getString(R.string.tinode_invite_body));
                provider.setShareIntent(intent);
                return true;

            case R.id.action_offline:
                Cache.getTinode().reconnectNow(true, false);
                return true;
        }

        // R.id.action_search

        return false;
    }

    private void onFindQueryResult() {
        mAdapter.resetFound(getActivity(), mSearchTerm);
    }

    private String doSearch(String query) {
        query = query.trim();
        query = !TextUtils.isEmpty(query) ? query : null;

        // No change.
        if (mSearchTerm == null && query == null) {
            return null;
        }

        // Don't do anything if the new filter is the same as the current filter
        if (mSearchTerm != null && mSearchTerm.equals(query)) {
            return mSearchTerm;
        }

        restartLoader(query);

        // Query is too short to be sent to the server.
        if (query != null && query.length() < MIN_TAG_LENGTH) {
            return query;
        }

        final FndTopic<?> fnd = Cache.getTinode().getFndTopic();
        fnd.setMeta(new MsgSetMeta<>(
                new MetaSetDesc<String, String>(query == null ? Tinode.NULL_VALUE : query, null)));
        if (query != null) {
            toggleProgressIndicator(true);
            fnd.getMeta(MsgGetMeta.sub()).thenFinally(new PromisedReply.FinalListener() {
                @Override
                public void onFinally() {
                    toggleProgressIndicator(false);
                }
            });
        } else {
            // If query is empty, clear the results and refresh.
            onFindQueryResult();
        }

        return query;
    }

    @Override
    public void toggleProgressIndicator(final boolean visible) {
        if (mProgress == null) {
            return;
        }
        Activity activity = getActivity();
        if (activity == null) {
            return;
        }

        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (visible) {
                    mProgress.show();
                } else {
                    mProgress.hide();
                }
            }
        });
    }

    // TODO: Add onBackPressed handing to parent Activity.
    // public boolean onBackPressed() {
    //    return false;
    //}

    private class FndListener extends FndTopic.FndListener<VxCard> {
        @Override
        public void onMetaSub(final Subscription<VxCard,String[]> sub) {
            if (sub.pub != null) {
                sub.pub.constructBitmap();
            }
        }

        @Override
        public void onSubsUpdated() {
            onFindQueryResult();
        }
    }

    // Restarts the loader. This triggers onCreateLoader(), which builds the
    // necessary content Uri from mSearchTerm.
    private void restartLoader(String searchTerm) {
        final StartChatActivity activity = (StartChatActivity) getActivity();
        if (activity == null) {
            return;
        }

        if (UiUtils.isPermissionGranted(activity, Manifest.permission.READ_CONTACTS)) {
            mAdapter.setContactsPermission(true);
            Bundle args = new Bundle();
            args.putString(ContactsLoaderCallback.ARG_SEARCH_TERM, searchTerm);
            LoaderManager.getInstance(activity).restartLoader(LOADER_ID, args, mContactsLoaderCallback);
        } else if (!activity.isReadContactsPermissionRequested()) {
            mAdapter.setContactsPermission(false);
            activity.setReadContactsPermissionRequested();
            requestPermissions(new String[]{Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS},
                    UiUtils.CONTACTS_PERMISSION_ID);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == UiUtils.CONTACTS_PERMISSION_ID) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Sync p2p topics to Contacts.
                Activity activity = getActivity();
                if (activity == null) {
                    return;
                }
                // Sync contacts.
                UiUtils.onContactsPermissionsGranted(activity);
                // Permission is granted
                restartLoader(mSearchTerm);
            }
        }
    }
}
