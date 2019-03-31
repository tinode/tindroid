package co.tinode.tindroid;

import android.content.Context;
import android.content.res.TypedArray;
import android.database.Cursor;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.TextAppearanceSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AlphabetIndexer;
import android.widget.SectionIndexer;
import android.widget.TextView;

import java.util.HashMap;
import java.util.Set;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.recyclerview.widget.RecyclerView;

/**
 * This is a subclass of CursorAdapter that supports binding Cursor columns to a view layout.
 * If those items are part of search results, the search string is marked by highlighting the
 * query text. An {@link AlphabetIndexer} is used to allow quicker navigation up and down the
 * ListView.
 */
class ContactsAdapter extends RecyclerView.Adapter<ContactsAdapter.ViewHolder>
        implements SectionIndexer, ContactsLoaderCallback.CursorSwapper {
    private static final String TAG = "ContactsAdapter";

    private AlphabetIndexer mAlphabetIndexer; // Stores the AlphabetIndexer instance
    private TextAppearanceSpan mHighlightTextSpan; // Stores the highlight text appearance style

    private String mSearchTerm;
    private ClickListener mClickListener;
    private Cursor mCursor;
    private ImageLoader mImageLoader;

    // Selected items
    private HashMap<String,Integer> mSelected;

    ContactsAdapter(Context context, ImageLoader imageLoader, ClickListener clickListener) {

        mClickListener = clickListener;
        mImageLoader = imageLoader;
        mSelected = new HashMap<>();

        setHasStableIds(true);
        mCursor = null;

        // Loads a string containing the English alphabet. To fully localize the app, provide a
        // strings.xml file in res/values-<x> directories, where <x> is a locale. In the file,
        // define a string with android:name="alphabet" and contents set to all of the
        // alphabetic characters in the language in their proper sort order, in upper case if
        // applicable.
        final String alphabet = context.getString(R.string.alphabet);

        // Instantiates a new AlphabetIndexer bound to the column used to sort contact names.
        // The cursor is left null, because it has not yet been retrieved.
        mAlphabetIndexer = new AlphabetIndexer(null, ContactsLoaderCallback.ContactsQuery.SORT_KEY, alphabet);

        // Defines a span for highlighting the part of a display name that matches the search
        // string
        mHighlightTextSpan = new TextAppearanceSpan(context, R.style.searchTextHighlight);
    }

    /**
     * Overrides newView() to inflate the list item views.
     */
    @Override
    @NonNull
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(parent.getContext())
                .inflate(viewType, parent, false), viewType);
    }

    /**
     * Binds data from the Cursor to the provided view.
     */
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        if (holder.viewType == R.layout.contact_basic) {
            holder.bind(mCursor, position);
        }
    }

    @Override
    public void swapCursor(Cursor newCursor, String newSearchTerm) {
        mSearchTerm = newSearchTerm;

        if (newCursor == mCursor) {
            return;
        }

        final Cursor oldCursor = mCursor;

        // Update the AlphabetIndexer with new cursor as well
        mAlphabetIndexer.setCursor(newCursor);

        mCursor = newCursor;

        // Notify the observers about the new cursor
        notifyDataSetChanged();

        if (oldCursor != null) {
            oldCursor.close();
        }
    }

    private int getActualItemCount() {
        if (mCursor == null) {
            return 0;
        }
        return mCursor.getCount();
    }

    @Override
    public int getItemCount() {
        int count = getActualItemCount();
        return count == 0 ? 1 : count;
    }

    public int getItemViewType(int position) {
        if (getActualItemCount() == 0) {
            return R.layout.contact_empty;
        }

        return R.layout.contact_basic;
    }

    @Override
    public long getItemId(int pos) {
        if (getActualItemCount() == 0) {
            return -2;
        }

        if (mCursor == null) {
            throw new IllegalStateException("Cursor is null.");
        }
        if (!mCursor.moveToPosition(pos)) {
            throw new IllegalStateException("Failed to move cursor to position " + pos);
        }

        return mCursor.getLong(ContactsLoaderCallback.ContactsQuery.ID);
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

    boolean isSelected(String unique) {
        return mSelected.containsKey(unique);
    }

    Set<String> getSelected() {
        return mSelected.keySet();
    }

    void toggleSelected(String unique) {
        if (isSelected(unique)) {
            mSelected.remove(unique);
        } else {
            mSelected.put(unique, 0);
        }
        notifyDataSetChanged();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        int viewType;
        String unique;
        TextView text1;
        TextView text2;
        AppCompatImageView icon;

        ViewHolder(@NonNull final View view, int viewType) {
            super(view);

            this.viewType = viewType;
            if (viewType == R.layout.contact_basic) {
                text1 = view.findViewById(android.R.id.text1);
                text2 = view.findViewById(android.R.id.text2);
                icon = view.findViewById(android.R.id.icon);
            }
        }

        void bind(Cursor cursor, int position) {
            if (!cursor.moveToPosition(position)) {
                throw new IllegalArgumentException("Invalid cursor position " + position);
            }

            // Get the thumbnail image Uri from the current Cursor row.
            final String photoUri = cursor.getString(ContactsLoaderCallback.ContactsQuery.PHOTO_THUMBNAIL_DATA);
            final String displayName = cursor.getString(ContactsLoaderCallback.ContactsQuery.DISPLAY_NAME);
            unique = cursor.getString(ContactsLoaderCallback.ContactsQuery.IM_ADDRESS);

            final int startIndex = UiUtils.indexOfSearchQuery(displayName, mSearchTerm);

            if (startIndex == -1) {
                // If the user didn't do a search, or the search string didn't match a display
                // name, show the display name without highlighting
                text1.setText(displayName);

                if (TextUtils.isEmpty(mSearchTerm)) {
                    if (TextUtils.isEmpty(unique)) {
                        // Search string is empty and we have no contacts to show
                        text2.setVisibility(View.GONE);
                    } else {
                        text2.setText(unique);
                        text2.setVisibility(View.VISIBLE);
                    }
                } else {
                    // Shows a second line of text that indicates the search string matched
                    // something other than the display name
                    text2.setVisibility(View.VISIBLE);
                }
            } else {
                // If the search string matched the display name, applies a SpannableString to
                // highlight the search string with the displayed display name

                // Wraps the display name in the SpannableString
                final SpannableString highlightedName = new SpannableString(displayName);

                // Sets the span to start at the starting point of the match and end at "length"
                // characters beyond the starting point
                highlightedName.setSpan(mHighlightTextSpan, startIndex,
                        startIndex + mSearchTerm.length(), 0);

                // Binds the SpannableString to the display name View object
                text1.setText(highlightedName);

                // Since the search string matched the name, this hides the secondary message
                text2.setVisibility(View.GONE);
            }

            if (isSelected(unique)) {
                icon.setImageResource(R.drawable.ic_selected);
                itemView.setBackgroundResource(R.drawable.contact_background);

                itemView.setActivated(true);
            } else {
                Context context = itemView.getContext();
                // Clear the icon then load the thumbnail from photoUri in a background worker thread.
                icon.setImageDrawable(UiUtils.avatarDrawable(context, null, displayName, unique));
                mImageLoader.loadImage(context, photoUri, icon);

                TypedArray typedArray = itemView.getContext().obtainStyledAttributes(
                        new int[]{android.R.attr.selectableItemBackground});
                itemView.setBackgroundResource(typedArray.getResourceId(0, 0));
                typedArray.recycle();

                itemView.setActivated(false);
            }

            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (mClickListener != null) {
                        mClickListener.onClick(unique, ViewHolder.this);
                    }
                }
            });
        }
    }

    interface ClickListener {
        void onClick(String topicName, ViewHolder holder);
    }
}
