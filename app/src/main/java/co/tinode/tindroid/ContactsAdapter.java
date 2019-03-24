package co.tinode.tindroid;

import android.content.Context;
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

import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.recyclerview.widget.RecyclerView;
import co.tinode.tindroid.account.Utils;
import co.tinode.tindroid.widgets.LetterTileDrawable;

/**
 * This is a subclass of CursorAdapter that supports binding Cursor columns to a view layout.
 * If those items are part of search results, the search string is marked by highlighting the
 * query text. An {@link AlphabetIndexer} is used to allow quicker navigation up and down the
 * ListView.
 */
class ContactsAdapter extends RecyclerView.Adapter<ContactsAdapter.ViewHolder> implements SectionIndexer {
    private AlphabetIndexer mAlphabetIndexer; // Stores the AlphabetIndexer instance
    private TextAppearanceSpan mHighlightTextSpan; // Stores the highlight text appearance style

    private Context mContext;
    private String mSearchTerm;
    private ClickListener mClickListener;
    private Cursor mCursor;

    /**
     * Instantiates a new Contacts Adapter.
     *
     * @param context A context that has access to the app's layout.
     */
    ContactsAdapter(Context context, Cursor cursor, ClickListener clickListener) {
        mContext = context;
        mClickListener = clickListener;

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
        mAlphabetIndexer = new AlphabetIndexer(null, ContactsFragment.ContactsQuery.SORT_KEY, alphabet);

        // Defines a span for highlighting the part of a display name that matches the search
        // string
        mHighlightTextSpan = new TextAppearanceSpan(context, R.style.searchTextHighlight);
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
        holder.bind(mCursor);
    }

    /**
     * Overrides swapCursor to move the new Cursor into the AlphabetIndex as well as the
     * CursorAdapter.
     */
    private void swapCursor(Cursor newCursor) {
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
    }

    void resetContent(Cursor newCursor, String newSearchTerm) {
        mSearchTerm = newSearchTerm;
        swapCursor(newCursor);
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

        return mCursor.getLong(ContactsFragment.ContactsQuery.ID);
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

    class ViewHolder extends RecyclerView.ViewHolder {
        TextView text1;
        TextView text2;
        AppCompatImageView icon;

        ViewHolder(@NonNull final View view) {
            super(view);
            text1 = view.findViewById(android.R.id.text1);
            text2 = view.findViewById(android.R.id.text2);
            icon = view.findViewById(android.R.id.icon);
        }

        void bind(Cursor cursor) {

            // Get the thumbnail image Uri from the current Cursor row.
            final String photoUri = cursor.getString(ContactsFragment.ContactsQuery.PHOTO_THUMBNAIL_DATA);

            final String displayName = cursor.getString(ContactsFragment.ContactsQuery.DISPLAY_NAME);

            final int startIndex = indexOfSearchQuery(displayName);

            Utils.ContactHolder extra = (contactsFragment.mPhEmImData != null ? contactsFragment.mPhEmImData.get(contact_id) : null);

            String line2 = (extra != null) ? extra.bestContact() : null;

            if (startIndex == -1) {
                // If the user didn't do a search, or the search string didn't match a display
                // name, show the display name without highlighting
                text1.setText(displayName);

                if (TextUtils.isEmpty(mSearchTerm)) {
                    if (TextUtils.isEmpty(line2)) {
                        // Search string is empty and we have no contacts to show
                        text2.setVisibility(View.GONE);
                    } else {
                        text2.setText(line2);
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

            // Clear the icon then load the thumbnail from photoUri in a background worker thread
            LetterTileDrawable tile = new LetterTileDrawable(mContext)
                    .setIsCircular(true)
                    .setLetterAndColor(displayName, line2)
                    .setContactTypeAndColor(LetterTileDrawable.TYPE_PERSON);
            icon.setImageDrawable(tile);
            contactsFragment.mImageLoader.loadImage(mContext, photoUri, holder.icon);

            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    mClickListener.onCLick(getIm());
                }
            });
        }
    }

    interface ClickListener {
        void onCLick(String topicName);
    }
}
