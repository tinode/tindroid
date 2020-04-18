package co.tinode.tindroid;

import android.app.Activity;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.core.content.res.ResourcesCompat;
import androidx.recyclerview.selection.ItemDetailsLookup;
import androidx.recyclerview.selection.ItemKeyProvider;
import androidx.recyclerview.selection.SelectionTracker;
import androidx.recyclerview.widget.RecyclerView;
import co.tinode.tindroid.db.StoredTopic;
import co.tinode.tindroid.media.VxCard;
import co.tinode.tinodesdk.ComTopic;
import co.tinode.tinodesdk.Tinode.TopicFilter;
import co.tinode.tinodesdk.Topic;

/**
 * Handling active chats, i.e. 'me' topic.
 */
public class ChatsAdapter extends RecyclerView.Adapter<ChatsAdapter.ViewHolder> {

    @SuppressWarnings("unused")
    private static final String TAG = "ChatsAdapter";

    private List<ComTopic<VxCard>> mTopics;
    private HashMap<String,Integer> mTopicIndex;

    private SelectionTracker<String> mSelectionTracker;
    private ClickListener mClickListener;

    private static int sColorOffline;
    private static int sColorOnline;

    ChatsAdapter(Context context, ClickListener clickListener) {
        super();

        mClickListener = clickListener;

        setHasStableIds(true);

        sColorOffline = ResourcesCompat.getColor(context.getResources(),
                R.color.offline, context.getTheme());
        sColorOnline = ResourcesCompat.getColor(context.getResources(),
                R.color.online, context.getTheme());
    }

    void resetContent(Activity activity, final boolean archive) {
        if (activity == null) {
            return;
        }

        final Collection<ComTopic<VxCard>> newTopics = Cache.getTinode().getFilteredTopics(new TopicFilter() {
            @Override
            public boolean isIncluded(Topic t) {
                return t.getTopicType().match(Topic.TopicType.USER) &&
                        (t.isArchived() == archive) && t.isJoiner();
            }
        });

        final HashMap<String,Integer> newTopicIndex = new HashMap<>(newTopics.size());
        for (Topic t : newTopics) {
            newTopicIndex.put(t.getName(), newTopicIndex.size());
        }

        activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mTopics = new ArrayList<>(newTopics);
                    mTopicIndex = newTopicIndex;

                    notifyDataSetChanged();
                }
            });
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        final LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        return new ViewHolder(
                inflater.inflate(viewType, parent, false), mClickListener, viewType);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        if (holder.viewType == R.layout.contact) {
            ComTopic<VxCard> topic = mTopics.get(position);
            holder.bind(position, topic, mSelectionTracker != null &&
                    mSelectionTracker.isSelected(topic.getName()));
        }
    }

    @Override
    public long getItemId(int position) {
        if (getActualItemCount() == 0) {
            return -2;
        }
        return StoredTopic.getId(mTopics.get(position));
    }

    private String getItemKey(int position) {
        return mTopics.get(position).getName();
    }

    private int getItemPosition(String key) {
        Integer pos = mTopicIndex.get(key);
        return pos == null ? -1 : pos;
    }

    private int getActualItemCount() {
       return mTopics == null ? 0 : mTopics.size();
    }

    @Override
    public int getItemCount() {
        // If there are no contacts, the RV will show a single 'empty' item.
        int count = getActualItemCount();
        return count == 0 ? 1 : count;
    }

    @Override
    public int getItemViewType(int position) {
        if (getActualItemCount() == 0) {
            return R.layout.contact_empty;
        }
        return R.layout.contact;
    }

    void setSelectionTracker(SelectionTracker<String> selectionTracker) {
        mSelectionTracker = selectionTracker;
    }

    static class ContactDetailsLookup extends ItemDetailsLookup<String> {
        RecyclerView mRecyclerView;

        ContactDetailsLookup(RecyclerView rv) {
            mRecyclerView = rv;
        }

        @Nullable
        @Override
        public ItemDetails<String> getItemDetails(@NonNull MotionEvent e) {
            View view = mRecyclerView.findChildViewUnder(e.getX(), e.getY());
            if (view != null) {
                ViewHolder holder = (ViewHolder) mRecyclerView.getChildViewHolder(view);
                return holder.getItemDetails();
            }
            return null;
        }
    }

    static class ContactDetails extends ItemDetailsLookup.ItemDetails<String> {
        int pos;
        String id;

        @Override
        public int getPosition() {
            return pos;
        }

        @Nullable
        @Override
        public String getSelectionKey() {
            return id;
        }
    }

    static class ContactKeyProvider extends ItemKeyProvider<String> {
        ChatsAdapter mAdapter;

        ContactKeyProvider(ChatsAdapter adapter) {
            super(SCOPE_MAPPED);
            mAdapter = adapter;
        }

        @Nullable
        @Override
        public String getKey(int position) {
            return mAdapter.getItemKey(position);
        }

        @Override
        public int getPosition(@NonNull String key) {
            return mAdapter.getItemPosition(key);
        }
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        int viewType;
        TextView name;
        TextView unreadCount;
        TextView contactPriv;
        AppCompatImageView avatar;
        ImageView online;
        ImageView muted;
        ImageView blocked;
        ImageView archived;

        ContactDetails details;
        ClickListener clickListener;

        ViewHolder(@NonNull View item, ClickListener cl, int viewType) {
            super(item);
            this.viewType = viewType;

            if (viewType == R.layout.contact) {
                name = item.findViewById(R.id.contactName);
                unreadCount = item.findViewById(R.id.unreadCount);
                contactPriv = item.findViewById(R.id.contactPriv);
                avatar = item.findViewById(R.id.avatar);
                online = item.findViewById(R.id.online);
                muted = item.findViewById(R.id.icon_muted);
                blocked = item.findViewById(R.id.icon_blocked);
                archived = item.findViewById(R.id.icon_archived);

                details = new ContactDetails();
                clickListener = cl;
            } else {
                details = null;
            }
        }

        ItemDetailsLookup.ItemDetails<String> getItemDetails() {
            return details;
        }

        void bind(int position, final ComTopic<VxCard> topic, boolean selected) {
            final Context context = itemView.getContext();
            final String topicName = topic.getName();

            details.pos = position;
            details.id = topic.getName();

            VxCard pub = topic.getPub();
            if (pub != null && pub.fn != null) {
                name.setText(pub.fn);
                name.setTypeface(null, Typeface.NORMAL);
            } else {
                name.setText(R.string.placeholder_contact_title);
                name.setTypeface(null, Typeface.ITALIC);
            }
            contactPriv.setText(topic.getComment());

            int unread = topic.getUnreadCount();
            if (unread > 0) {
                unreadCount.setText(unread > 9 ? "9+" : String.valueOf(unread));
                unreadCount.setVisibility(View.VISIBLE);
            } else {
                unreadCount.setVisibility(View.GONE);
            }

            avatar.setImageDrawable(UiUtils.avatarDrawable(context,
                    pub != null ? pub.getBitmap() : null,
                    pub != null ? pub.fn : null,
                    topicName));

            online.setColorFilter(topic.getOnline() ? sColorOnline : sColorOffline);

            muted.setVisibility(topic.isMuted() ? View.VISIBLE : View.GONE);
            archived.setVisibility(topic.isArchived() ? View.VISIBLE : View.GONE);
            blocked.setVisibility(!topic.isJoiner() ? View.VISIBLE : View.GONE);

            if (selected) {
                itemView.setBackgroundResource(R.drawable.contact_background);
                itemView.setOnClickListener(null);

                itemView.setActivated(true);
            } else {

                TypedArray typedArray = context.obtainStyledAttributes(
                        new int[]{android.R.attr.selectableItemBackground});
                itemView.setBackgroundResource(typedArray.getResourceId(0, 0));
                typedArray.recycle();

                itemView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        clickListener.onCLick(topicName);
                    }
                });

                itemView.setActivated(false);
            }

            // Field lengths may have changed.
            itemView.invalidate();
        }
    }

    interface ClickListener {
        void onCLick(String topicName);
    }
}
