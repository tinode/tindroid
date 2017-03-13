package co.tinode.tindroid;

import android.app.Activity;
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.support.v4.app.ListFragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.widget.CursorAdapter;
import android.support.v7.widget.AppCompatImageView;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.TextAppearanceSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AlphabetIndexer;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Locale;
import java.util.TreeSet;

import co.tinode.tindroid.account.Utils;
import co.tinode.tinodesdk.NotConnectedException;
import co.tinode.tinodesdk.PromisedReply;
import co.tinode.tinodesdk.Topic;
import co.tinode.tinodesdk.model.ServerMessage;

import static android.app.Activity.RESULT_OK;

/**
 * Fragment for adding/editing a group topic
 */
public class AddGroupFragment extends ListFragment {

    private static final String TAG = "AddGroupFragment";

    // Bundle key for saving previously selected search result item
    private static final String STATE_PREVIOUSLY_SELECTED_KEY =
            "co.tinode.tindroid.SELECTED_ITEM";

    private ContactsAdapter mAdapter; // The list of contacts with Tinode handles
    private RecyclerView mMembers;
    private TextView mNoMembers;
    private ImageLoader mImageLoader; // Handles loading the contact image in a background thread
    private String mSearchTerm; // Stores the current search query term

    private ContactsLoaderCallback mContactsLoaderCallback;

    private int mPreviouslySelectedSearchItem = 0;

    // Sorted set of selected contacts (cursor positions of selected contacts).
    private TreeSet<Integer> mSelectedContacts;

    public AddGroupFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null) {
            // If we're restoring state after this fragment was recreated then
            // retrieve previous search term and previously selected search
            // result.
            mSearchTerm = savedInstanceState.getString(SearchManager.QUERY);
            mPreviouslySelectedSearchItem =
                    savedInstanceState.getInt(STATE_PREVIOUSLY_SELECTED_KEY, 0);
        }

        mContactsLoaderCallback = new ContactsLoaderCallback();

        mImageLoader = new ImageLoader(getActivity(), UiUtils.getListPreferredItemHeight(this),
                getActivity().getSupportFragmentManager()) {
            @Override
            protected Bitmap processBitmap(Object data) {
                // This gets called in a background thread and passed the data from
                // ImageLoader.loadImage().
                return UiUtils.loadContactPhotoThumbnail(AddGroupFragment.this, (String) data, getImageSize());
            }
        };
        // Set a placeholder loading image for the image loader
        mImageLoader.setLoadingImage(R.drawable.ic_person_circle);

        mSelectedContacts = new TreeSet<>();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        Log.d(TAG, "onCreateView");

        setHasOptionsMenu(true);

        return inflater.inflate(R.layout.fragment_edit_topic, container, false);
    }

    @Override
    public void onActivityCreated(Bundle savedInstance) {
        super.onActivityCreated(savedInstance);

        Log.d(TAG, "onActivityCreated");
        final Activity activity = getActivity();

        activity.findViewById(R.id.upload_avatar).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                UiUtils.requestAvatar(AddGroupFragment.this);
            }
        });

        activity.findViewById(R.id.goNext).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final EditText titleEdit = ((EditText) activity.findViewById(R.id.title));
                final String topicTitle = titleEdit.getText().toString();
                if (TextUtils.isEmpty(topicTitle)) {
                    titleEdit.setError(getString(R.string.name_required));
                    return;
                }

                if (mMembers.getAdapter().getItemCount() == 0) {
                    Toast.makeText(activity, R.string.add_one_member, Toast.LENGTH_SHORT).show();
                    return;
                }

                final Topic topic = Cache.getTinode().newGroupTopic(null);
                try {
                    topic.subscribe().thenApply(new PromisedReply.SuccessListener<ServerMessage>() {
                        @Override
                        public PromisedReply<ServerMessage> onSuccess(ServerMessage result) throws Exception {
                            Intent intent = new Intent(activity, MessageActivity.class);
                            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                            intent.putExtra("topic", topic.getName());
                            startActivity(intent);
                            return null;
                        }
                    }, null);
                } catch (NotConnectedException ignored) {
                    Log.d(TAG, "create new topic -- NotConnectedException");
                } catch (Exception e) {
                    Toast.makeText(activity, R.string.failed_to_create_topic, Toast.LENGTH_SHORT).show();
                    return;
                }
            }
        });

        mNoMembers = (TextView) activity.findViewById(R.id.noMembers);

        mMembers = (RecyclerView) activity.findViewById(R.id.groupMembers);
        mMembers.setLayoutManager(new LinearLayoutManager(activity, LinearLayoutManager.HORIZONTAL, false));
        mMembers.setAdapter(new MembersAdapter());

        mAdapter = new ContactsAdapter(getActivity());
        setListAdapter(mAdapter);

        getListView().setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int position, long id) {
                Log.d(TAG, "Click, pos=" + position + ", id=" + id);
                MembersAdapter ma = (MembersAdapter) mMembers.getAdapter();
                MemberData data = mAdapter.getItem(position);
                ma.addItem(data);
                mSelectedContacts.add(data.position);
                mAdapter.notifyDataSetChanged();
            }
        });

        getLoaderManager().initLoader(0, null, mContactsLoaderCallback);
    }

    // Deselect previously selected item in the contact list.
    private void deselect(int cursorPosition) {
        mSelectedContacts.remove(cursorPosition);
        mAdapter.notifyDataSetChanged();
    }

    private void createTopic(String title) {

    }

    @Override
    public void onPause() {
        super.onPause();

        // In the case onPause() is called during a fling the image loader is
        // un-paused to let any remaining background work complete.
        mImageLoader.setPauseWork(false);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == UiUtils.SELECT_PICTURE && resultCode == RESULT_OK) {
            UiUtils.acceptAvatar(getActivity(), (ImageView) getActivity().findViewById(R.id.imageAvatar), data);
        }
    }

    /**
     * This is a subclass of CursorAdapter that supports binding Cursor columns to a view layout.
     * If those items are part of search results, the search string is marked by highlighting the
     * query text. An {@link AlphabetIndexer} is used to allow quicker navigation up and down the
     * ListView.
     */
    private class ContactsAdapter extends CursorAdapter {
        private LayoutInflater mInflater; // Stores the layout inflater
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

            // Defines a span for highlighting the part of a display name that matches the search
            // string
            highlightTextSpan = new TextAppearanceSpan(getActivity(), R.style.searchTextHiglight);
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

            // Get the thumbnail image Uri from the current Cursor row.
            final String photoUri = cursor.getString(ContactsQuery.PHOTO_THUMBNAIL_DATA);

            final String displayName = cursor.getString(ContactsQuery.DISPLAY_NAME);

            final int startIndex = indexOfSearchQuery(displayName);

            holder.inviteButton.setVisibility(View.GONE);

            if (startIndex == -1) {
                // If the user didn't do a search, or the search string didn't match a display
                // name, show the display name without highlighting
                holder.text1.setText(displayName);

                if (TextUtils.isEmpty(mSearchTerm)) {
                    holder.text2.setText(cursor.getString(ContactsQuery.IM_HANDLE));
                }

                holder.text2.setVisibility(View.VISIBLE);
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
            holder.icon.setImageResource(R.drawable.ic_person_circle);
            mImageLoader.loadImage(photoUri, holder.icon);
        }


        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            return super.getView(translateListToCursorPosition(position), convertView, parent);
        }

        /** Convert position in the ListView into cursor position. List omits items contained in the mSelectedContacts.
         * The cursor position is >= list position.
         *
         * @param position list position to translate to cursor position
         *
         * @return cursor position
         */
        private int translateListToCursorPosition(int position) {
            for (Integer idx : mSelectedContacts) {
                if (idx <= position) {
                    position++;
                }
            }
            return position;
        }

        @Override
        public MemberData getItem(int position) {
            Cursor cursor = getCursor();
            if (cursor != null) {
                int cursorPosition = translateListToCursorPosition(position);
                cursor.moveToPosition(cursorPosition);
                final String uid = cursor.getString(ContactsQuery.IM_HANDLE);
                final String photoUri = cursor.getString(ContactsQuery.PHOTO_THUMBNAIL_DATA);
                final String displayName = cursor.getString(ContactsQuery.DISPLAY_NAME);

                return new MemberData(cursorPosition, uid, displayName, photoUri);
            }

            return null;
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
            return super.getCount() - mSelectedContacts.size();
        }

        /**
         * A class that defines fields for each resource ID in the list item layout. This allows
         * ContactsAdapter.newView() to store the IDs once, when it inflates the layout, instead of
         * calling findViewById in each iteration of bindView.
         */
        private class ViewHolder {
            TextView text1;
            TextView text2;
            AppCompatImageView icon;
            View inviteButton;
        }
    }

    interface ContactsQuery {
        String[] PROJECTION = {
                ContactsContract.Data._ID,
                ContactsContract.Data.CONTACT_ID,
                ContactsContract.CommonDataKinds.Im.DISPLAY_NAME_PRIMARY,
                ContactsContract.CommonDataKinds.Im.PHOTO_THUMBNAIL_URI,
                ContactsContract.CommonDataKinds.Im.DATA,
                ContactsContract.Data.MIMETYPE,
                ContactsContract.CommonDataKinds.Im.PROTOCOL,
                ContactsContract.CommonDataKinds.Im.CUSTOM_PROTOCOL,
        };

        int ID = 0;
        int CONTACT_ID = 1;
        int DISPLAY_NAME = 2;
        int PHOTO_THUMBNAIL_DATA = 3;
        int IM_HANDLE = 4;

        String SELECTION = ContactsContract.Data.MIMETYPE + "=? AND " +
                ContactsContract.CommonDataKinds.Im.PROTOCOL + "=? AND " +
                ContactsContract.CommonDataKinds.Im.CUSTOM_PROTOCOL + "=?";
        String[] SELECTION_ARGS = {
                ContactsContract.CommonDataKinds.Im.CONTENT_ITEM_TYPE,
                Integer.toString(ContactsContract.CommonDataKinds.Im.PROTOCOL_CUSTOM),
                Utils.IM_PROTOCOL,
        };
        String SORT_ORDER = ContactsContract.CommonDataKinds.Im.DISPLAY_NAME_PRIMARY;
    }

    private class ContactsLoaderCallback implements LoaderManager.LoaderCallbacks<Cursor> {


        @Override
        public Loader<Cursor> onCreateLoader(int id, Bundle args) {

            Uri contentUri;

            // There are two types of searches, one which displays all contacts and
            // one which filters contacts by a search query. If mSearchTerm is set
            // then a search query has been entered and the latter should be used.
            if (mSearchTerm == null) {
                // Since there's no search string, use the content URI that searches the entire
                // Contacts table
                contentUri = ContactsContract.Data.CONTENT_URI;
            } else {
                // Since there's a search string, use the special content Uri that searches the
                // Contacts table. The URI consists of a base Uri and the search string.
                contentUri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_FILTER_URI, Uri.encode(mSearchTerm));
            }

            Log.d(TAG, "init cursor for Uri " + contentUri);

            // Returns a new CursorLoader for querying the Contacts table. No arguments are used
            // for the selection clause. The search string is either encoded onto the content URI,
            // or no contacts search string is used. The other search criteria are constants. See
            // the ContactsQuery interface.
            return new CursorLoader(getActivity(),
                    contentUri,
                    ContactsQuery.PROJECTION,
                    ContactsQuery.SELECTION,
                    ContactsQuery.SELECTION_ARGS,
                    ContactsQuery.SORT_ORDER);
        }

        @Override
        public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
            // This swaps the new cursor into the adapter.
            Log.d(TAG, "delivered cursor with items: " + data.getCount());
            mAdapter.swapCursor(data);
        }

        @Override
        public void onLoaderReset(Loader<Cursor> loader) {
            // When the loader is being reset, clear the cursor from the adapter. This allows the
            // cursor resources to be freed.
            mAdapter.swapCursor(null);
        }
    }

    private class MembersAdapter extends RecyclerView.Adapter<MemberViewHolder> {

        private ArrayList<MemberData> mItems;

        public MembersAdapter() {
            mItems = new ArrayList<>();
        }

        @Override
        public MemberViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            // create a new view
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.group_member, parent, false);
            return new MemberViewHolder(v);
        }

        @Override
        public void onBindViewHolder(final MemberViewHolder holder, int position) {
            // MemberData data = mItems.get(position);

            holder.mAvatar.setImageResource(R.drawable.ic_person_circle);
            mImageLoader.loadImage(mItems.get(position).photoUri, holder.mAvatar);

            holder.mContainer.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    removeItem(holder.getAdapterPosition());
                }
            });
        }

        @Override
        public int getItemCount() {
            // Log.d(TAG, "MembersAdapter item count " + mItems.size());
            return mItems.size();
        }

        public void addItem(MemberData data) {
            Log.d(TAG, "Adding member, name=" + data.name + ", cursorPos=" + data.position);
            mItems.add(data);
            if (mItems.size() == 1) {
                mNoMembers.setVisibility(View.INVISIBLE);
            }
            notifyItemInserted(mItems.size() - 1);
        }

        public void removeItem(int position) {
            MemberData data = mItems.remove(position);
            deselect(data.position);
            notifyItemRemoved(position);
            if (mItems.size() == 0) {
                mNoMembers.setVisibility(View.VISIBLE);
            }
        }
    }

    class MemberViewHolder extends RecyclerView.ViewHolder {
        View mContainer;
        ImageView mAvatar;

        MemberViewHolder(View itemView) {
            super(itemView);
            mContainer = itemView;
            mAvatar = (ImageView) itemView.findViewById(R.id.avatar);
        }
    }

    private class MemberData {
        int position;
        String name;
        String uid;
        String photoUri;

        MemberData(int cursorPosition, String uid, String name, String photoUri) {
            this.position = cursorPosition;
            this.uid = uid;
            this.name = name;
            this.photoUri = photoUri;
        }
    }
}
