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
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SearchView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.widget.ContentLoadingProgressBar;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.selection.ItemDetailsLookup;
import androidx.recyclerview.selection.SelectionTracker;
import androidx.recyclerview.selection.StorageStrategy;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import co.tinode.tindroid.media.VxCard;
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

public class FindFragment extends Fragment {

    private static final String TAG = "FindFragment";

    // Delay in milliseconds between the last keystroke and time when the query is sent to the server.
    private static final int SEARCH_REQUEST_DELAY = 1000;

    private FndTopic<VxCard> mFndTopic;
    private FndListener mFndListener;

    private String mSearchTerm; // Stores the current search query term

    private FindAdapter mAdapter = null;
    private SelectionTracker<String> mSelectionTracker = null;

    private ContentLoadingProgressBar mProgress = null;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mFndTopic = Cache.getTinode().getOrCreateFndTopic();
        mFndListener = new FndListener();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        setHasOptionsMenu(true);

        return inflater.inflate(R.layout.fragment_chat_list, container, false);
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
        mAdapter = new FindAdapter(new FindAdapter.ClickListener() {
            @Override
            public void onCLick(final String topicName) {
                Intent intent = new Intent(activity, MessageActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                intent.putExtra("topic", topicName);
                activity.startActivity(intent);
            }
        });

        mAdapter.resetContent(null);
        rv.setAdapter(mAdapter);

        mSelectionTracker = new SelectionTracker.Builder<>(
                "find-selection",
                rv,
                new FindAdapter.ContactItemKeyProvider(mAdapter),
                new ContactDetailsLookup(rv),
                StorageStrategy.createStringStorage())
                .build();

        mSelectionTracker.onRestoreInstanceState(savedInstanceState);

        mAdapter.setSelectionTracker(mSelectionTracker);
        mSelectionTracker.addObserver(new SelectionTracker.SelectionObserver() {
            @Override
            public void onSelectionChanged() {
                super.onSelectionChanged();
                // Do something here.
            }
        });

        mProgress = fragment.findViewById(R.id.progressBar);
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

        mAdapter.resetContent(getActivity());
    }

    @Override
    public void onPause() {
        super.onPause();

        if (mFndTopic != null) {
            mFndTopic.setListener(null);
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        mSelectionTracker.onSaveInstanceState(outState);
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        menu.clear();
        inflater.inflate(R.menu.menu_find, menu);

        final Activity activity = getActivity();
        if (activity == null) {
            return;
        }

        // Setting up SearchView

        // Locate the search item
        MenuItem searchItem = menu.findItem(R.id.action_search);

        // Retrieves the system search manager service
        final SearchManager searchManager =
                (SearchManager) activity.getSystemService(Activity.SEARCH_SERVICE);

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

    /**
     * Do nothing.
     */
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        return false;
    }

    private void datasetChanged() {
        mAdapter.resetContent(getActivity());
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

        setProgressBarVisible(true);

        final FndTopic<?> fnd = Cache.getTinode().getFndTopic();
        fnd.setMeta(new MsgSetMeta<>(
                new MetaSetDesc<String, String>(query == null ? Tinode.NULL_VALUE : query, null)));
        fnd.getMeta(MsgGetMeta.sub()).thenFinally(new PromisedReply.FinalListener<ServerMessage>() {
            @Override
            public PromisedReply<ServerMessage> onFinally() {
                setProgressBarVisible(false);
                return null;
            }
        });

        return query;
    }

    private void setProgressBarVisible(final boolean visible) {
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
    public boolean onBackPressed() {
        if (mSelectionTracker.hasSelection()) {
            mSelectionTracker.clearSelection();
        } else {
            return true;
        }
        return false;
    }

    private static class ContactDetailsLookup extends ItemDetailsLookup<String> {
        RecyclerView mRecyclerView;

        ContactDetailsLookup(RecyclerView recyclerView) {
            this.mRecyclerView = recyclerView;
        }

        @Nullable
        @Override
        public ItemDetails<String> getItemDetails(@NonNull MotionEvent motionEvent) {
            View view = mRecyclerView.findChildViewUnder(motionEvent.getX(), motionEvent.getY());
            if (view != null) {
                RecyclerView.ViewHolder viewHolder = mRecyclerView.getChildViewHolder(view);
                if (viewHolder instanceof FindAdapter.ViewHolder) {
                    return ((FindAdapter.ViewHolder) viewHolder).getItemDetails(motionEvent);
                }
            }
            return null;
        }
    }

    private class FndListener extends FndTopic.FndListener<VxCard> {
        @Override
        public void onMetaSub(final Subscription<VxCard,String[]> sub) {
            if (sub.pub != null) {
                sub.pub.constructBitmap();
            }
        }

        @Override
        public void onSubsUpdated() {
            Log.i(TAG, "onSubsUpdated");
            datasetChanged();
        }
    }
}
