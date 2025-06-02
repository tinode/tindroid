package co.tinode.tindroid;

import android.Manifest;
import android.app.Activity;
import android.app.SearchManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
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

import java.util.Map;
import java.util.regex.Pattern;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.ShareActionProvider;
import androidx.core.view.MenuItemCompat;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.Lifecycle;
import androidx.loader.app.LoaderManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import co.tinode.tindroid.media.VxCard;
import co.tinode.tindroid.widgets.CircleProgressView;
import co.tinode.tindroid.widgets.HorizontalListDivider;
import co.tinode.tinodesdk.FndTopic;
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
public class FindFragment extends Fragment implements UiUtils.ProgressIndicator, MenuProvider {

    private static final String TAG = "FindFragment";

    // Delay in milliseconds between the last keystroke and time when the query is sent to the server.
    private static final int SEARCH_REQUEST_DELAY = 1000;

    private static final int LOADER_ID = 104;

    // Minimum allowed length of a search tag (server-enforced).
    private static final int MIN_TAG_LENGTH = 4;

    private FndTopic<VxCard> mFndTopic;
    private FndListener mFndListener;

    private LoginEventListener mLoginListener;

    private String mSearchTerm; // Stores the current search query term
    private FindAdapter mAdapter = null;

    // Callback which receives notifications of contacts loading status;
    private ContactsLoaderCallback mContactsLoaderCallback;

    private CircleProgressView mProgress = null;

    private final ActivityResultLauncher<String[]> mRequestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                for (Map.Entry<String,Boolean> e : result.entrySet()) {
                    // Check if all required permissions are granted.
                    if (!e.getValue()) {
                        return;
                    }
                }
                // Permissions are granted.
                FragmentActivity activity = getActivity();
                UiUtils.onContactsPermissionsGranted(activity);
                // Try to open the image selector again.
                restartLoader(getActivity(), mSearchTerm);
            });

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mFndTopic = Cache.getTinode().getOrCreateFndTopic();
        mFndListener = new FndListener();

        if (savedInstanceState != null) {
            mSearchTerm = savedInstanceState.getString(SearchManager.QUERY);
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_contacts, container, false);
    }

    @Override
    public void onViewCreated(@NonNull final View fragment, Bundle savedInstanceState) {
        final FragmentActivity activity = requireActivity();
        if (activity.isFinishing() || activity.isDestroyed()) {
            return;
        }

        activity.addMenuProvider(this, getViewLifecycleOwner(), Lifecycle.State.RESUMED);

        RecyclerView rv = fragment.findViewById(R.id.chat_list);
        rv.setLayoutManager(new LinearLayoutManager(activity));
        rv.setHasFixedSize(true);
        rv.addItemDecoration(new HorizontalListDivider(activity));
        mAdapter = new FindAdapter(activity, new ContactClickListener());

        mContactsLoaderCallback = new ContactsLoaderCallback(LOADER_ID, activity, mAdapter);

        mAdapter.swapCursor(null, mSearchTerm);
        mAdapter.setContactsPermission(UiUtils.isPermissionGranted(activity, Manifest.permission.READ_CONTACTS));
        rv.setAdapter(mAdapter);

        mProgress = fragment.findViewById(R.id.progressCircle);
    }

    @Override
    public void onResume() {
        super.onResume();

        final View fragment = getView();
        if (fragment == null) {
            return;
        }

        final Tinode tinode = Cache.getTinode();
        mLoginListener = new LoginEventListener(tinode.isConnected());
        tinode.addListener(mLoginListener);

        if (!tinode.isAuthenticated()) {
            // If connection is not ready, wait for completion. This method will be called again
            // from the onLogin callback;
            return;
        }

        topicAttach();
    }

    private void topicAttach() {
        Cache.attachFndTopic(mFndListener)
                .thenApply(new PromisedReply.SuccessListener<>() {
                    @Override
                    public PromisedReply<ServerMessage> onSuccess(ServerMessage result) {
                        final FragmentActivity activity = getActivity();
                        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
                            return null;
                        }

                        mAdapter.resetFound(activity, mSearchTerm);
                        // Refresh cursor.
                        activity.runOnUiThread(() -> restartLoader(activity, mSearchTerm));
                        return null;
                    }
                })
                .thenCatch(new UiUtils.ToastFailureListener(getActivity()))
                .thenApply(new PromisedReply.SuccessListener<>() {
                    @Override
                    public PromisedReply<ServerMessage> onSuccess(ServerMessage result) {
                        final FragmentActivity activity = getActivity();
                        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
                            return null;
                        }
                        // Load local contacts even if there is no connection.
                        activity.runOnUiThread(() -> restartLoader(activity, mSearchTerm));
                        return null;
                    }
                });
    }

    @Override
    public void onPause() {
        super.onPause();

        if (mFndTopic != null) {
            mFndTopic.setListener(null);
        }

        Cache.getTinode().removeListener(mLoginListener);
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
    public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        menu.clear();
        inflater.inflate(R.menu.menu_contacts, menu);

        final FragmentActivity activity = getActivity();
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
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
        if (searchView == null) {
            return;
        }
        searchView.setQueryHint(getResources().getString(R.string.hint_search_tags));
        // Assign searchable info to SearchView
        searchView.setSearchableInfo(searchManager.getSearchableInfo(activity.getComponentName()));
        searchView.setFocusable(true);
        searchView.setFocusableInTouchMode(true);

        // Set listeners for SearchView
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            private Handler mHandler;

            @Override
            public boolean onQueryTextSubmit(String queryText) {
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
                mHandler.postDelayed(() -> mSearchTerm = doSearch(queryText), SEARCH_REQUEST_DELAY);
                return true;
            }
        });

        searchItem.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(@NonNull MenuItem menuItem) {
                searchView.setIconified(false);
                searchView.requestFocus();
                searchView.requestFocusFromTouch();
                return true;
            }

            @Override
            public boolean onMenuItemActionCollapse(@NonNull MenuItem menuItem) {
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
    public boolean onMenuItemSelected(@NonNull MenuItem item) {
        final FragmentActivity activity = getActivity();
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            return true;
        }

        Intent intent;
        int id = item.getItemId();
        if (id == R.id.action_add_contact) {
            intent = new Intent(Intent.ACTION_INSERT, ContactsContract.Contacts.CONTENT_URI);
            try {
                startActivity(intent);
            } catch (ActivityNotFoundException ignored) {
                Log.w(TAG, "No application can add contact");
                Toast.makeText(activity, R.string.action_failed, Toast.LENGTH_LONG).show();
            }
            return true;
        } else if (id == R.id.action_invite) {
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
        } else if (id == R.id.action_offline) {
            Cache.getTinode().reconnectNow(true, false, false);
            return true;
        }

        return false;
    }

    private void onFindQueryResult() {
        mAdapter.resetFound(getActivity(), mSearchTerm);
    }

    private static final Pattern sSingleTagTest = Pattern.compile("[\\s,:]");
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

        restartLoader(getActivity(), query);

        // Query is too short to be sent to the server.
        if (query != null && query.length() < MIN_TAG_LENGTH) {
            return query;
        }

        if (TextUtils.isEmpty(query)) {
            query = Tinode.NULL_VALUE;
        } else if (!sSingleTagTest.matcher(query).matches()) {
            // No colons, spaces or commas. Try as email, phone, or alias.
            String email = UtilsString.asEmail(query);
            if (email != null) {
                query = String.format("%s%s", Tinode.TAG_EMAIL, email);
            } else {
                String tel = UtilsString.asPhone(query);
                if (tel != null) {
                    query = String.format("%s%s", Tinode.TAG_PHONE, tel);
                } else {
                    if (query.charAt(0) == '@') {
                        query = query.substring(1);
                    }
                    // Convert to "alice" -> "alias:alice,alice"
                    query = String.format("%s%s,%s", Tinode.TAG_ALIAS, query, query);
                }
            }
        }

        mFndTopic.setMeta(new MsgSetMeta.Builder<String,String>().with(new MetaSetDesc<>(query, null)).build());
        if (!Tinode.NULL_VALUE.equals(query)) {
            toggleProgressIndicator(true);
            mFndTopic.getMeta(MsgGetMeta.sub()).thenFinally(new PromisedReply.FinalListener() {
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

        FragmentActivity activity = getActivity();
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            return;
        }

        activity.runOnUiThread(() -> {
            if (visible) {
                mProgress.show();
            } else {
                mProgress.hide();
            }
        });
    }

    // Restarts the loader. This triggers onCreateLoader(), which builds the
    // necessary content Uri from mSearchTerm.
    private void restartLoader(final FragmentActivity activity, final String searchTerm) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            return;
        }

        if (UiUtils.isPermissionGranted(activity, Manifest.permission.READ_CONTACTS)) {
            mAdapter.setContactsPermission(true);
            Bundle args = new Bundle();
            args.putString(ContactsLoaderCallback.ARG_SEARCH_TERM, searchTerm);
            LoaderManager.getInstance(activity).restartLoader(LOADER_ID, args, mContactsLoaderCallback);
        } else if (((ReadContactsPermissionChecker) activity).shouldRequestReadContactsPermission()) {
            mAdapter.setContactsPermission(false);
            ((ReadContactsPermissionChecker) activity).setReadContactsPermissionRequested();
            mRequestPermissionLauncher.launch(new String[]{Manifest.permission.READ_CONTACTS,
                    Manifest.permission.WRITE_CONTACTS});
        }
    }

    interface ReadContactsPermissionChecker {
        boolean shouldRequestReadContactsPermission();

        void setReadContactsPermissionRequested();
    }

    private class FndListener extends FndTopic.FndListener<VxCard> {
        @Override
        public void onMetaSub(final Subscription<VxCard, String[]> sub) {
            if (sub.pub != null) {
                sub.pub.constructBitmap();
            }
        }

        @Override
        public void onSubsUpdated() {
            onFindQueryResult();
        }
    }

    private class ContactClickListener implements FindAdapter.ClickListener {
        @Override
        public void onClick(String topicName) {
            FragmentActivity activity = getActivity();

            if (activity == null || activity.isDestroyed() || activity.isFinishing()) {
                return;
            }
            Intent initial = activity.getIntent();
            Intent launcher = new Intent(activity, MessageActivity.class);
            Uri uri = initial != null ? initial.getParcelableExtra(Intent.EXTRA_STREAM) : null;
            if (uri != null) {
                launcher.setDataAndType(uri, initial.getType());
                launcher.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            }
            // See discussion here: https://github.com/tinode/tindroid/issues/39
            launcher.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            launcher.putExtra(Const.INTENT_EXTRA_TOPIC, topicName);
            startActivity(launcher);
            activity.finish();
        }
    }

    private class LoginEventListener extends UiUtils.EventListener {
        LoginEventListener(boolean online) {
            super(getActivity(), online);
        }

        @Override
        public void onLogin(int code, String txt) {
            super.onLogin(code, txt);
            topicAttach();
        }
    }
}
