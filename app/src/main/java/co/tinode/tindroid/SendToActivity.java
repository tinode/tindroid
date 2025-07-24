package co.tinode.tindroid;

import android.app.Activity;
import android.app.SearchManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.SearchView;

import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import co.tinode.tindroid.widgets.HorizontalListDivider;
import co.tinode.tinodesdk.Topic;

public class SendToActivity extends AppCompatActivity {
    private static final String TAG = "SendToActivity";

    // Delay in milliseconds between the last keystroke and time when the query is sent to the server.
    private static final int SEARCH_REQUEST_DELAY = 1000;

    private ChatsAdapter mAdapter = null;
    private String mSearchTerm; // Stores the current search query term

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        UiUtils.setupSystemToolbar(this);

        // Get intent, action and MIME type
        final Intent intent = getIntent();
        final String action = intent.getAction();
        final String type = intent.getType();
        final CharSequence text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
        final Uri uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);

        if (!Intent.ACTION_SEND.equals(action) || type == null || (uri == null && text == null)) {
            Log.d(TAG, "Unable to share this type of content: '" + type +
                    "', uri=" + uri + "; text=" + text);
            finish();
        }

        setContentView(R.layout.activity_send_to);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        final ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle(R.string.send_to);
            toolbar.setNavigationOnClickListener(v -> {
                Intent launcher = new Intent(SendToActivity.this, ChatsActivity.class);
                launcher.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(launcher);
                finish();
            });
        }

        RecyclerView rv = findViewById(R.id.chat_list);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setHasFixedSize(true);
        rv.addItemDecoration(new HorizontalListDivider(this));
        mAdapter = new ChatsAdapter(this, topicName -> {
            if (isFinishing() || isDestroyed()) {
                return;
            }
            Intent launcher = new Intent(this, MessageActivity.class);
            if (uri != null) {
                launcher.setDataAndType(uri, type);
                launcher.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } else {
                launcher.setType(type);
                launcher.putExtra(Intent.EXTRA_TEXT, text);
            }
            launcher.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            launcher.putExtra(Const.INTENT_EXTRA_TOPIC, topicName);
            startActivity(launcher);
            finish();
        }, Topic::isWriter);
        rv.setAdapter(mAdapter);
    }

    @Override
    public void onResume() {
        super.onResume();
        mAdapter.resetContent(this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        final SearchManager searchManager = (SearchManager) getSystemService(Activity.SEARCH_SERVICE);
        if (searchManager == null) {
            return false;
        }

        MenuInflater inflater = getMenuInflater();
        menu.clear();
        inflater.inflate(R.menu.menu_search, menu);

        // Setting up SearchView

        // Locate the search item
        MenuItem searchItem = menu.findItem(R.id.action_search);

        // Retrieves the SearchView from the search menu item
        final SearchView searchView = (SearchView) searchItem.getActionView();
        if (searchView == null) {
            return false;
        }
        searchView.setQueryHint(getResources().getString(R.string.hint_search_tags));
        // Assign searchable info to SearchView
        searchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));
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
        return true;
    }

    private String doSearch(String query) {
        query = query.trim().toLowerCase(Locale.getDefault());
        query = !TextUtils.isEmpty(query) ? query : null;

        // No change.
        if (mSearchTerm == null && query == null) {
            return null;
        }

        // Don't do anything if the new filter is the same as the current filter
        if (mSearchTerm != null && mSearchTerm.equals(query)) {
            return mSearchTerm;
        }

        mAdapter.setTextFilter(query);
        mAdapter.resetContent(this);

        return query;
    }
}
