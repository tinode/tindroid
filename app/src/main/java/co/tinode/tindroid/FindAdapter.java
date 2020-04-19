package co.tinode.tindroid;

import android.app.Activity;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.TextAppearanceSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.recyclerview.widget.RecyclerView;
import co.tinode.tindroid.media.VxCard;
import co.tinode.tinodesdk.model.Subscription;

/**
 * FindAdapter merges results from searching local Contacts with remote 'fnd' topic.
 */
public class FindAdapter extends RecyclerView.Adapter<FindAdapter.ViewHolder>
        implements ContactsLoaderCallback.CursorSwapper {

    @SuppressWarnings("unused")
    private static final String TAG = "FindAdapter";

    private TextAppearanceSpan mHighlightTextSpan;

    private List<Subscription<VxCard,String[]>> mFound;

    private Cursor mCursor;
    private String mSearchTerm;
    private ImageLoader mImageLoader;

    private ClickListener mClickListener;

    // TRUE is user granted access to contacts, FALSE otherwise.
    private boolean mPermissionGranted = false;

    FindAdapter(Context context, ImageLoader imageLoader, ClickListener clickListener) {
        super();

        mImageLoader = imageLoader;
        mCursor = null;

        mClickListener = clickListener;

        setHasStableIds(true);

        mHighlightTextSpan = new TextAppearanceSpan(context, R.style.searchTextHighlight);
    }

    void resetFound(Activity activity, String searchTerm) {
        Collection c = Cache.getTinode().getFndTopic().getSubscriptions();
        if (c == null) {
            mFound = new LinkedList<>();
        } else {
            // noinspection unchecked
            mFound = new LinkedList<>(c);
        }

        mSearchTerm = searchTerm;
        if (activity != null) {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    notifyDataSetChanged();
                }
            });
        }
    }

    void setContactsPermission(boolean granted) {
        mPermissionGranted = granted;
    }

    @Override
    public void swapCursor(Cursor newCursor, String searchTerm) {
        mSearchTerm = searchTerm;

        if (newCursor == mCursor) {
            return;
        }

        final Cursor oldCursor = mCursor;

        mCursor = newCursor;

        // Notify the observers about the new cursor
        notifyDataSetChanged();

        if (oldCursor != null) {
            oldCursor.close();
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        final LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        View view = inflater.inflate(viewType, parent, false);
        switch (viewType) {
            case R.layout.not_found:
            case R.layout.no_permission:
            case R.layout.no_search_query:
                return new ViewHolderEmpty(view);
            case R.layout.contact_section:
                return new ViewHolderSection(view);
            default:
                return new ViewHolderItem(view, mClickListener);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(position, getItemAt(position));
    }

    @Override
    public int getItemViewType(int position) {
        if (position == 0) {
            return R.layout.contact_section;
        }

        position --;

        int count = getCursorItemCount();
        if (count == 0) {
            if (position == 0) {
                // The 'empty' element in the 'PHONE CONTACTS' section.
                return mPermissionGranted ? R.layout.not_found : R.layout.no_permission;
            }
            // One 'empty' element
            count = 1;
        } else if (position < count) {
            return R.layout.contact;
        }

        position -= count;

        if (position == 0) {
            return R.layout.contact_section;
        }

        position --;

        count = getFoundItemCount();
        if (count == 0 && position == 0) {
            return TextUtils.isEmpty(mSearchTerm) ? R.layout.no_search_query : R.layout.not_found;
        }

        return R.layout.contact;
    }

    @Override
    public long getItemId(int position) {
        if (position == 0) {
            return "section_one".hashCode();
        }

        // Subtract section title.
        position --;

        int count = getCursorItemCount();
        if (count == 0) {
            if (position == 0) {
                // The 'empty' element in the 'PHONE CONTACTS' section.
                return ("empty_one" + mPermissionGranted).hashCode();
            }

            count = 1;
        } else if (position < count) {
            // Element from the cursor.
            mCursor.moveToPosition(position);
            String unique = mCursor.getString(ContactsLoaderCallback.ContactsQuery.IM_ADDRESS);
            return ("contact:" + unique).hashCode();
        }

        // Skip all cursor elements
        position -= count;

        if (position == 0) {
            // Section title DIRECTORY;
            return "section_two".hashCode();
        }

        // Subtract section title.
        position --;

        count = getFoundItemCount();
        if (count == 0 && position == 0) {
            // The 'empty' element in the DIRECTORY section.
            return ("empty_two" + TextUtils.isEmpty(mSearchTerm)).hashCode();
        }

        return ("found:" + mFound.get(position).getUnique()).hashCode();
    }

    private int getCursorItemCount() {
        return mCursor == null || mCursor.isClosed() ? 0 : mCursor.getCount();
    }

    private int getFoundItemCount() {
        return mFound != null ? mFound.size() : 0;
    }

    private Object getItemAt(int position) {
        if (position == 0) {
            // Section title 'PHONE CONTACTS';
            return null;
        }

        position --;

        // Count the section title element.
        int count = getCursorItemCount();
        if (count == 0) {
            if (position == 0) {
                // The 'empty' element in the 'PHONE CONTACTS' section.
                return null;
            }
            count = 1;
        } else if (position < count) {
            // One of the phone contacts. Move the cursor
            // to the correct position and return it.
            mCursor.moveToPosition(position);
            return mCursor;
        }

        position -= count;

        if (position == 0) {
            // Section title DIRECTORY;
            return null;
        }

        // Skip the 'DIRECTORY' element;
        position --;

        count = getFoundItemCount();
        if (count == 0 && position == 0) {
            // The 'empty' element in the DIRECTORY section.
            return null;
        }

        return mFound.get(position);
    }

    @Override
    public int getItemCount() {
        // At least 2 section titles.
        int itemCount = 2;

        int count = getFoundItemCount();
        itemCount += count == 0 ? 1 : count;
        count = getCursorItemCount();
        itemCount += count == 0 ? 1 : count;

        return itemCount;
    }

    static class ViewHolderSection extends ViewHolder {
        ViewHolderSection(@NonNull View item) {
            super(item);
        }

        public void bind(int position, Object data) {
            if (position == 0) {
                ((TextView) itemView).setText(R.string.contacts_section_contacts);
            } else {
                ((TextView) itemView).setText(R.string.contacts_section_directory);
            }
        }
    }

    static class ViewHolderEmpty extends ViewHolder {
        ViewHolderEmpty(@NonNull View item) {
            super(item);
        }

        public void bind(int position, Object data) {
        }
    }

    class ViewHolderItem extends ViewHolder {
        TextView name;
        TextView contactPriv;
        AppCompatImageView icon;

        ClickListener clickListener;

        ViewHolderItem(@NonNull View item, ClickListener cl) {
            super(item);

            name = item.findViewById(R.id.contactName);
            contactPriv = item.findViewById(R.id.contactPriv);
            icon = item.findViewById(R.id.avatar);

            item.findViewById(R.id.online).setVisibility(View.GONE);
            item.findViewById(R.id.unreadCount).setVisibility(View.GONE);

            clickListener = cl;
        }

        @Override
        public void bind(int position, final Object data) {
            if (data instanceof Subscription) {
                // noinspection unchecked
                bind(position, (Subscription<VxCard, String[]>)data);
            } else {
                bind(position, (Cursor) data);
            }
        }

        private void bind(int position, final Cursor cursor) {
            final String photoUri = cursor.getString(ContactsLoaderCallback.ContactsQuery.PHOTO_THUMBNAIL_DATA);
            final String displayName = cursor.getString(ContactsLoaderCallback.ContactsQuery.DISPLAY_NAME);
            final String unique = cursor.getString(ContactsLoaderCallback.ContactsQuery.IM_ADDRESS);

            final int startIndex = UiUtils.indexOfSearchQuery(displayName, mSearchTerm);

            if (startIndex == -1) {
                // If the user didn't do a search, or the search string didn't match a display
                // name, show the display name without highlighting
                name.setText(displayName);

                if (TextUtils.isEmpty(mSearchTerm)) {
                    if (TextUtils.isEmpty(unique)) {
                        // Search string is empty and we have no contacts to show
                        contactPriv.setVisibility(View.GONE);
                    } else {
                        contactPriv.setText(unique);
                        contactPriv.setVisibility(View.VISIBLE);
                    }
                } else {
                    // Shows a second line of text that indicates the search string matched
                    // something other than the display name
                    contactPriv.setVisibility(View.VISIBLE);
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
                name.setText(highlightedName);

                // Since the search string matched the name, this hides the secondary message
                contactPriv.setVisibility(View.GONE);
            }

            // Clear the icon then load the thumbnail from photoUri in a background worker thread
            Context context = itemView.getContext();
            icon.setImageDrawable(UiUtils.avatarDrawable(context, null, displayName, unique));
            mImageLoader.loadImage(context, photoUri, icon);

            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    clickListener.onCLick(unique);
                }
            });
        }

        private void bind(int position, final Subscription<VxCard, String[]> sub) {
            final Context context = itemView.getContext();
            final String unique = sub.getUnique();

            VxCard pub = sub.pub;
            if (pub != null) {
                name.setText(pub.fn);
                name.setTypeface(null, Typeface.NORMAL);
            } else {
                name.setText(R.string.placeholder_contact_title);
                name.setTypeface(null, Typeface.ITALIC);
            }
            if (sub.priv != null) {
                contactPriv.setText(TextUtils.join(", ", sub.priv));
            } else {
                contactPriv.setText("");
            }

            icon.setImageDrawable(
                    UiUtils.avatarDrawable(context,
                    pub != null ? pub.getBitmap() : null,
                    pub != null ? pub.fn : null,
                    unique));

            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    clickListener.onCLick(unique);
                }
            });
        }
    }

    static abstract class ViewHolder extends RecyclerView.ViewHolder {

        ViewHolder(@NonNull View itemView) {
            super(itemView);
        }

        abstract void bind(int position, Object data);
    }


    interface ClickListener {
        void onCLick(String topicName);
    }
}
