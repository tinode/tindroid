package co.tinode.tindroid;

import android.app.Activity;
import android.app.SearchManager;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SearchView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import co.tinode.tindroid.media.VxCard;
import co.tinode.tindroid.widgets.HorizontalListDivider;
import co.tinode.tinodesdk.ComTopic;
import co.tinode.tinodesdk.model.Drafty;

public class ForwardToFragment extends Fragment {
    private static final String TAG = "ForwardToFragment";

    public static final String CONTENT_TO_FORWARD = "content_to_forward";
    private static final int SEARCH_REQUEST_DELAY = 300; // 300 ms;
    private static final int MIN_TERM_LENGTH = 3;

    private ChatsAdapter mAdapter = null;
    private Drafty mContent;
    private String mSearchTerm = null;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_forward_to, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        final AppCompatActivity activity = (AppCompatActivity) getActivity();
        if (activity == null) {
            return;
        }

        final ActionBar bar = activity.getSupportActionBar();
        if (bar != null) {
            bar.setDisplayHomeAsUpEnabled(false);
            bar.setTitle(R.string.forward_to);
        }

        RecyclerView rv = view.findViewById(R.id.chat_list);
        rv.setLayoutManager(new LinearLayoutManager(activity));
        rv.setHasFixedSize(true);
        rv.addItemDecoration(new HorizontalListDivider(activity));
        mAdapter = new ChatsAdapter(activity, topicName -> {
            Intent intent = new Intent(activity, MessageActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            intent.putExtra("topic", topicName);
            intent.putExtra("forward", mContent);
            activity.startActivity(intent);
        }, t -> doSearch((ComTopic) t));
        rv.setAdapter(mAdapter);
    }

    @Override
    public void onResume() {
        super.onResume();

        final Activity activity = getActivity();
        if (activity == null) {
            return;
        }

        setHasOptionsMenu(true);

        Bundle args = getArguments();
        if (args != null) {
            mContent = (Drafty) args.getSerializable(CONTENT_TO_FORWARD);
        }

        mAdapter.resetContent(activity);
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        menu.clear();
        inflater.inflate(R.menu.menu_forward_to, menu);

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
        searchView.setQueryHint(getResources().getString(R.string.hint_search_contacts));
        // Assign searchable info to SearchView
        searchView.setSearchableInfo(searchManager.getSearchableInfo(activity.getComponentName()));
        searchView.setFocusable(true);
        searchView.setFocusableInTouchMode(true);

        // Set listeners for SearchView
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            private Handler mHandler;

            @Override
            public boolean onQueryTextSubmit(String queryText) {
                Log.i(TAG, "onQueryTextSubmit='" + queryText + "'");

                if (mHandler != null) {
                    mHandler.removeCallbacksAndMessages(null);
                }

                mSearchTerm = queryText;

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
                mHandler.removeCallbacksAndMessages(null);
                mHandler.postDelayed(() -> mAdapter.resetContent(activity), SEARCH_REQUEST_DELAY);
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

    private boolean doSearch(ComTopic t) {
        if (t.isBlocked()) {
            return false;
        }

        String query = mSearchTerm != null ? mSearchTerm.trim() : null;
        if (TextUtils.isEmpty(query) || query.length() < MIN_TERM_LENGTH) {
            return true;
        }

        VxCard pub = (VxCard) t.getPub();
        if (pub.fn != null && pub.fn.contains(query)) {
            return true;
        }

        String comment = t.getComment();
        if (comment != null && comment.contains(query)) {
            return true;
        }

        return t.getName().startsWith(query);
    }
}
