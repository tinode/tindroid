package co.tinode.tindroid;

import android.annotation.SuppressLint;
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
import android.widget.ImageSwitcher;
import android.widget.ImageView;
import android.widget.SectionIndexer;
import android.widget.TextView;

import java.util.HashMap;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import coil.Coil;
import coil.request.ImageRequest;
import coil.size.Scale;

/**
 * This is a subclass of CursorAdapter that supports binding Cursor columns to a view layout.
 * If those items are part of search results, the search string is marked by highlighting the
 * query text. An {@link AlphabetIndexer} is used to allow quicker navigation up and down the
 * ListView.
 */
class ContactsAdapter extends RecyclerView.Adapter<ContactsAdapter.ViewHolder>
        implements SectionIndexer, ContactsLoaderCallback.CursorSwapper {

    private final AlphabetIndexer mAlphabetIndexer; // Stores the AlphabetIndexer instance
    private final TextAppearanceSpan mHighlightTextSpan; // Stores the highlight text appearance style
    private final ClickListener mClickListener;
    // Selected items
    private final HashMap<String, Integer> mSelected;
    private String mSearchTerm;
    private Cursor mCursor;
    private boolean mPermissionGranted = false;

    ContactsAdapter(Context context, ClickListener clickListener) {

        mClickListener = clickListener;
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

    void setContactsPermissionGranted() {
        mPermissionGranted = true;
    }

    @SuppressLint("NotifyDataSetChanged")
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
            return mPermissionGranted ? R.layout.contact_empty : R.layout.no_permission;
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

    void toggleSelected(String unique, int pos) {
        if (isSelected(unique)) {
            mSelected.remove(unique);
        } else {
            mSelected.put(unique, null);
        }
        if (pos >= 0) {
            notifyItemChanged(pos);
        }
    }

    interface ClickListener {
        void onClick(int position, String unique, String displayName, String photoUri);
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        final int viewType;
        String unique;
        String photoUri;
        String displayName;
        TextView text1;
        TextView text2;
        ImageSwitcher switcher;

        ViewHolder(@NonNull final View view, int viewType) {
            super(view);

            this.viewType = viewType;
            if (viewType == R.layout.contact_basic) {
                Context context = view.getContext();
                text1 = view.findViewById(android.R.id.text1);
                text2 = view.findViewById(android.R.id.text2);
                switcher = view.findViewById(R.id.icon_switcher);
                switcher.setInAnimation(context, R.anim.flip_in);
                switcher.setOutAnimation(context, R.anim.flip_out);
            }
        }

        void bind(Cursor cursor, final int position) {
            if (!cursor.moveToPosition(position)) {
                throw new IllegalArgumentException("Invalid cursor position " + position);
            }

            // Get the thumbnail image Uri from the current Cursor row.
            photoUri = cursor.getString(ContactsLoaderCallback.ContactsQuery.PHOTO_THUMBNAIL_DATA);
            displayName = cursor.getString(ContactsLoaderCallback.ContactsQuery.DISPLAY_NAME);
            unique = cursor.getString(ContactsLoaderCallback.ContactsQuery.IM_ADDRESS);

            final int startIndex = UtilsString.indexOfSearchQuery(displayName, mSearchTerm);

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
            } else if (!mSearchTerm.isEmpty()) {
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
                ((ImageView) switcher.getCurrentView()).setImageResource(R.drawable.ic_selected);
                itemView.setBackgroundResource(R.drawable.contact_background);

                itemView.setActivated(true);
            } else {
                ImageView icon = (ImageView) switcher.getCurrentView();
                Context context = icon.getContext();
                if (photoUri != null) {
                    // Clear the icon then load the thumbnail from photoUri background.
                    Coil.imageLoader(context).enqueue(
                            new ImageRequest.Builder(context)
                                .data(photoUri)
                                .placeholder(R.drawable.disk)
                                .error(R.drawable.ic_broken_image_round)
                                .scale(Scale.FIT)
                                .target(icon)
                                .build());
                } else {
                    icon.setImageDrawable(
                            UiUtils.avatarDrawable(icon.getContext(), null, displayName, unique, false));
                }

                TypedArray typedArray = itemView.getContext().obtainStyledAttributes(
                        new int[]{android.R.attr.selectableItemBackground});
                itemView.setBackgroundResource(typedArray.getResourceId(0, 0));
                typedArray.recycle();

                itemView.setActivated(false);
            }

            if (mClickListener != null) {
                itemView.setOnClickListener(view -> {
                    mClickListener.onClick(position, unique, displayName, photoUri);
                    if (isSelected(unique)) {
                        ViewHolder.this.switcher.setImageResource(R.drawable.ic_selected);
                    } else if (photoUri != null) {
                        Context context = switcher.getContext();
                        Coil.imageLoader(context).enqueue(
                                new ImageRequest.Builder(context)
                                    .data(photoUri)
                                    .placeholder(R.drawable.disk)
                                    .error(R.drawable.ic_broken_image_round)
                                    .scale(Scale.FIT)
                                    .target((ImageView) switcher.getNextView()).build());
                    } else {
                        switcher.setImageDrawable(
                                UiUtils.avatarDrawable(switcher.getContext(), null, displayName, unique,
                                        false));
                    }
                });
            }
        }
    }
}
