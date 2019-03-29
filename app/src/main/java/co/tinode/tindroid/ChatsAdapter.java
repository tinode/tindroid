package co.tinode.tindroid;

import android.app.Activity;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Typeface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatImageView;

import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    private boolean mIsArchive;

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

    void resetContent(Activity activity, boolean archive) {
        mIsArchive = archive;
        mTopics = Cache.getTinode().getFilteredTopics(new TopicFilter() {
            @Override
            public boolean isIncluded(Topic t) {
                return t.getTopicType().compare(Topic.TopicType.USER) &&
                        (t.isArchived() == mIsArchive);
            }
        });

        activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    notifyDataSetChanged();
                }
            });
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        final LayoutInflater inflater = (LayoutInflater) parent.getContext()
                .getSystemService(AppCompatActivity.LAYOUT_INFLATER_SERVICE);
        return new ViewHolder(
                inflater.inflate(R.layout.contact, parent, false), mClickListener);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ComTopic<VxCard> topic = mTopics.get(position);
        holder.bind(position, topic, mSelectionTracker != null &&
                mSelectionTracker.isSelected(topic.getName()));
    }

    @Override
    public long getItemId(int position) {
        return StoredTopic.getId(mTopics.get(position));
    }

    @Override
    public int getItemCount() {
        return mTopics == null ? 0 : mTopics.size();
    }

    private ComTopic<VxCard> getItemAt(int pos) {
        return mTopics.get(pos);
    }

    void setSelectionTracker(SelectionTracker<String> selectionTracker) {
        mSelectionTracker = selectionTracker;
    }

    static class ContactDetails extends ItemDetailsLookup.ItemDetails<String> {
        int pos;
        String name;

        ContactDetails() {
        }

        @Override
        public int getPosition() {
            return pos;
        }

        @Nullable
        @Override
        public String getSelectionKey() {
            return name;
        }
    }

    static class ContactItemKeyProvider extends ItemKeyProvider<String> {
        private ChatsAdapter mAdapter;
        private final Map<String,Integer> mKeyToPosition;

        ContactItemKeyProvider(ChatsAdapter adapter) {
            super(SCOPE_CACHED);

            mAdapter = adapter;

            mKeyToPosition = new HashMap<>(mAdapter.getItemCount());

            for (int i = 0; i < mAdapter.getItemCount(); i++) {
                mKeyToPosition.put(mAdapter.getItemAt(i).getName(), i);
            }
        }

        @Nullable
        @Override
        public String getKey(int i) {
            return mAdapter.getItemAt(i).getName();
        }

        @Override
        public int getPosition(@NonNull String s) {
            Integer pos = mKeyToPosition.get(s);
            return pos == null ? -1 : pos;
        }
    }

    static class ViewHolder
            extends RecyclerView.ViewHolder {
        TextView name;
        TextView unreadCount;
        TextView contactPriv;
        AppCompatImageView icon;
        AppCompatImageView online;

        ContactDetails details;
        ClickListener clickListener;

        ViewHolder(@NonNull View item, ClickListener cl) {
            super(item);

            name = item.findViewById(R.id.contactName);
            unreadCount = item.findViewById(R.id.unreadCount);
            contactPriv = item.findViewById(R.id.contactPriv);
            icon = item.findViewById(R.id.avatar);
            online = item.findViewById(R.id.online);

            details = new ContactDetails();
            clickListener = cl;
        }

        ContactDetails getItemDetails(@SuppressWarnings("unused") MotionEvent motion) {
            return details;
        }

        void bind(int position, final ComTopic<VxCard> topic, boolean selected) {
            final Context context = itemView.getContext();
            final String topicName = topic.getName();

            details.pos = position;
            details.name = topic.getName();

            VxCard pub = topic.getPub();
            if (pub != null) {
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
                unreadCount.setVisibility(View.INVISIBLE);
            }

            icon.setImageDrawable(UiUtils.avatarDrawable(context,
                    pub != null ? pub.getBitmap() : null,
                    pub != null ? pub.fn : null,
                    topicName));

            online.setColorFilter(topic.getOnline() ? sColorOnline : sColorOffline);

            if (selected) {
                itemView.setBackgroundResource(R.drawable.contact_background);
                itemView.setOnClickListener(null);
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
            }

            itemView.setActivated(selected);
        }
    }

    interface ClickListener {
        void onCLick(String topicName);
    }
}
