package co.tinode.tindroid;

import android.annotation.SuppressLint;
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
import android.widget.ImageView;
import android.widget.TextView;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import co.tinode.tindroid.media.VxCard;
import co.tinode.tinodesdk.Topic;
import co.tinode.tinodesdk.model.Subscription;

import coil.Coil;
import coil.request.ImageRequest;
import coil.size.Scale;

/**
 * FindAdapter merges results from searching local Contacts with remote 'fnd' topic.
 */
public class FindAdapter extends RecyclerView.Adapter<FindAdapter.ViewHolder>
        implements ContactsLoaderCallback.CursorSwapper {

    private final TextAppearanceSpan mHighlightTextSpan;
    private final ClickListener mClickListener;
    private List<FoundMember> mFound;
    private Cursor mCursor;
    private String mSearchTerm;
    // TRUE is user granted access to contacts, FALSE otherwise.
    private boolean mPermissionGranted = false;

    FindAdapter(Context context, @NonNull ClickListener clickListener) {
        super();

        mCursor = null;

        mClickListener = clickListener;

        setHasStableIds(true);

        mHighlightTextSpan = new TextAppearanceSpan(context, R.style.searchTextHighlight);
    }

    void resetFound(Activity activity, String searchTerm) {
        mFound = new LinkedList<>();
        Collection<Subscription<Object,String[]>> subs = Cache.getTinode().getFndTopic().getSubscriptions();
        if (subs != null) {
            for (Subscription<Object,String[]> s: subs) {
                mFound.add(new FoundMember(s.user == null ? s.topic : s.user, (VxCard) s.pub, s.priv));
            }
        }

        mSearchTerm = searchTerm;
        if (activity != null) {
            activity.runOnUiThread(this::notifyDataSetChanged);
        }
    }

    void setContactsPermission(boolean granted) {
        mPermissionGranted = granted;
    }

    @SuppressLint("NotifyDataSetChanged")
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
        if (viewType == R.layout.not_found ||
                viewType == R.layout.no_permission ||
                viewType == R.layout.no_search_query) {
            return new ViewHolderEmpty(view);
        } else if (viewType == R.layout.contact_section) {
            return new ViewHolderSection(view);
        }

        return new ViewHolderItem(view, mClickListener);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(position, getItemAt(position));
    }

    // Clear the avatar: there is some bug(?) in RecyclerView(?) which causes avatars to be
    // displayed in the wrong places.
    @Override
    public void onViewRecycled(@NonNull ViewHolder holder) {
        if (holder instanceof ViewHolderItem) {
            ImageView avatar = ((ViewHolderItem) holder).avatar;
            if (avatar != null) {
                avatar.setImageDrawable(null);
            }
        }
    }

    @Override
    public int getItemViewType(int position) {
        if (TextUtils.isEmpty(mSearchTerm)) {
            // Self topic is present.
            if (position == 0) {
                return R.layout.contact;
            }

            position--;
        }

        if (position == 0) {
            // Phone contacts section title.
            return R.layout.contact_section;
        }

        // Subtract section title.
        position--;

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

        position--;

        count = getFoundItemCount();
        if (count == 0 && position == 0) {
            return TextUtils.isEmpty(mSearchTerm) ? R.layout.no_search_query : R.layout.not_found;
        }

        return R.layout.contact;
    }

    @Override
    public long getItemId(int position) {
        if (TextUtils.isEmpty(mSearchTerm)) {
            // Self topic is present.
            if (position == 0) {
                return "slf".hashCode();
            }

            position--;
        }

        if (position == 0) {
            return "section_one".hashCode();
        }

        // Subtract section title.
        position--;

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
        position--;

        count = getFoundItemCount();
        if (count == 0 && position == 0) {
            // The 'empty' element in the DIRECTORY section.
            return ("empty_two" + TextUtils.isEmpty(mSearchTerm)).hashCode();
        }

        return ("found:" + mFound.get(position).id).hashCode();
    }

    private int getCursorItemCount() {
        return mCursor == null || mCursor.isClosed() ? 0 : mCursor.getCount();
    }

    private int getFoundItemCount() {
        return mFound != null ? mFound.size() : 0;
    }

    private Object getItemAt(int position) {
        if (TextUtils.isEmpty(mSearchTerm)) {
            // Self topic is present.
            if (position == 0) {
                return new FoundMember("slf", null, null);
            }

            position--;
        }

        if (position == 0) {
            // Section title 'PHONE CONTACTS';
            return R.string.contacts_section_contacts;
        }

        // Subtract section title.
        position--;

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
            return R.string.contacts_section_directory;
        }

        // Skip the 'DIRECTORY' element;
        position--;

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

        if (TextUtils.isEmpty(mSearchTerm)) {
            // Self topic is present.
            itemCount++;
        }

        int count = getFoundItemCount();
        itemCount += count == 0 ? 1 : count;
        count = getCursorItemCount();
        itemCount += count == 0 ? 1 : count;

        return itemCount;
    }

    interface ClickListener {
        void onClick(String topicName);
    }

    static class ViewHolderSection extends ViewHolder {
        ViewHolderSection(@NonNull View item) {
            super(item);
        }

        public void bind(int position, Object data) {
            ((TextView) itemView).setText((int) data);
        }
    }

    static class ViewHolderEmpty extends ViewHolder {
        ViewHolderEmpty(@NonNull View item) {
            super(item);
        }

        public void bind(int position, Object data) {
        }
    }

    public static abstract class ViewHolder extends RecyclerView.ViewHolder {

        ViewHolder(@NonNull View itemView) {
            super(itemView);
        }

        abstract void bind(int position, Object data);
    }

    public class ViewHolderItem extends ViewHolder {
        final TextView name;
        final TextView contactPriv;
        final ImageView avatar;

        final ClickListener clickListener;

        ViewHolderItem(@NonNull View item, ClickListener cl) {
            super(item);

            name = item.findViewById(R.id.contactName);
            contactPriv = item.findViewById(R.id.contactPriv);
            avatar = item.findViewById(R.id.avatar);

            item.findViewById(R.id.online).setVisibility(View.GONE);
            item.findViewById(R.id.unreadCount).setVisibility(View.GONE);

            clickListener = cl;
        }

        @Override
        public void bind(int position, final Object data) {
            if (data instanceof FoundMember) {
                bind((FoundMember) data);
            } else if (data instanceof Cursor) {
                bind((Cursor) data);
            }
        }

        private void bind(final Cursor cursor) {
            final String photoUri = cursor.getString(ContactsLoaderCallback.ContactsQuery.PHOTO_THUMBNAIL_DATA);
            final String displayName = cursor.getString(ContactsLoaderCallback.ContactsQuery.DISPLAY_NAME);
            final String unique = cursor.getString(ContactsLoaderCallback.ContactsQuery.IM_ADDRESS);

            final int startIndex = UtilsString.indexOfSearchQuery(displayName, mSearchTerm);

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
                // characters beyond the starting point.
                highlightedName.setSpan(mHighlightTextSpan, startIndex,
                        startIndex + mSearchTerm.length(), 0);

                // Binds the SpannableString to the display name View object
                name.setText(highlightedName);

                // Since the search string matched the name, this hides the secondary message
                contactPriv.setVisibility(View.GONE);
            }

            Context context = itemView.getContext();
            if (photoUri != null) {
                Coil.imageLoader(context).enqueue(
                        new ImageRequest.Builder(context)
                            .data(photoUri)
                            .placeholder(R.drawable.disk)
                            .error(R.drawable.ic_broken_image_round)
                            .target(avatar)
                            .scale(Scale.FIT)
                            .build());

            } else {
                avatar.setImageDrawable(
                        UiUtils.avatarDrawable(context, null, displayName, unique, false));
            }

            itemView.setOnClickListener(view -> clickListener.onClick(unique));
        }

        private void bind(final FoundMember member) {
            final String userId = member.id;

            UiUtils.setAvatar(avatar, member.pub, userId, false);
            if (member.pub != null) {
                name.setText(member.pub.fn);
                name.setTypeface(null, Typeface.NORMAL);
            } else if (Topic.isSlfType(userId)) {
                name.setText(R.string.self_topic_title);
                name.setTypeface(null, Typeface.NORMAL);
            } else {
                name.setText(R.string.placeholder_contact_title);
                name.setTypeface(null, Typeface.ITALIC);
            }

            if (member.priv != null) {
                String matched = TextUtils.join(", ", member.priv);
                final SpannableString highlightedName = new SpannableString(matched);
                final int startIndex = UtilsString.indexOfSearchQuery(matched, mSearchTerm);
                if (startIndex >= 0) {
                    highlightedName.setSpan(mHighlightTextSpan, startIndex,
                            startIndex + mSearchTerm.length(), 0);
                }
                contactPriv.setText(highlightedName);
            } else if (Topic.isSlfType(userId)) {
                contactPriv.setText(R.string.self_topic_description);
            } else {
                contactPriv.setText("");
            }

            itemView.setOnClickListener(view -> clickListener.onClick(userId));
        }
    }

    private record FoundMember(String id, VxCard pub, String[] priv) {
    }
}
