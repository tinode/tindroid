package co.tinode.tindroid;

import android.app.Activity;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Typeface;
import android.text.TextUtils;
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
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.core.content.res.ResourcesCompat;
import androidx.recyclerview.selection.ItemDetailsLookup;
import androidx.recyclerview.selection.ItemKeyProvider;
import androidx.recyclerview.selection.SelectionTracker;
import androidx.recyclerview.widget.RecyclerView;
import co.tinode.tindroid.db.StoredTopic;
import co.tinode.tindroid.format.PreviewFormatter;
import co.tinode.tindroid.media.VxCard;
import co.tinode.tinodesdk.ComTopic;
import co.tinode.tinodesdk.Storage;
import co.tinode.tinodesdk.model.Drafty;
import co.tinode.tinodesdk.model.TheCard;

/**
 * Handling active chats, i.e. 'me' topic.
 */
public class ChatsAdapter extends RecyclerView.Adapter<ChatsAdapter.ViewHolder> {
    private static final int MAX_MESSAGE_PREVIEW_LENGTH = 60;

    private static int sColorOffline;
    private static int sColorOnline;
    private final ClickListener mClickListener;
    private List<ComTopic<VxCard>> mTopics;
    private HashMap<String, Integer> mTopicIndex;
    private SelectionTracker<String> mSelectionTracker;
    private final Filter mTopicFilter;
    // Optional filter to find topics by name.
    private Filter mTextFilter = null;

    ChatsAdapter(Context context, ClickListener clickListener, @Nullable Filter filter) {
        super();

        mClickListener = clickListener;
        mTopicFilter = filter != null ? filter : topic -> true;

        setHasStableIds(true);
        setTextFilter(null);

        sColorOffline = ResourcesCompat.getColor(context.getResources(),
                R.color.offline, context.getTheme());
        sColorOnline = ResourcesCompat.getColor(context.getResources(),
                R.color.online, context.getTheme());
    }

    void resetContent(Activity activity) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            return;
        }

        final Collection<ComTopic<VxCard>> newTopics = Cache.getTinode().getFilteredTopics(t ->
                t.getTopicType().match(ComTopic.TopicType.USER) &&
                        mTopicFilter.filter((ComTopic) t) &&
                        mTextFilter.filter((ComTopic) t));

        final HashMap<String, Integer> newTopicIndex = new HashMap<>(newTopics.size());
        for (ComTopic t : newTopics) {
            newTopicIndex.put(t.getName(), newTopicIndex.size());
        }

        mTopics = new ArrayList<>(newTopics);
        mTopicIndex = newTopicIndex;

        activity.runOnUiThread(this::notifyDataSetChanged);
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
            if (mTopics.size() <= position) {
                // Looks like there is a race condition here.
                return;
            }
            ComTopic<VxCard> topic = mTopics.get(position);
            if (topic == null) {
                // This should not happen.
                return;
            }
            Storage.Message msg = Cache.getTinode().getLastMessage(topic.getName());
            holder.bind(position, topic, msg, mSelectionTracker != null &&
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
        if (mTopicIndex == null) {
            return -1;
        }
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

    void setTextFilter(@Nullable String text) {
        mTextFilter = new Filter() {
            private final String mQuery = text;
            @Override
            public boolean filter(ComTopic topic) {
                if (TextUtils.isEmpty(mQuery)) {
                    return true;
                }

                ArrayList<String> hayStack = new ArrayList<>();
                TheCard pub = (TheCard) topic.getPub();
                if (pub != null) {
                    hayStack.add(pub.fn);
                    hayStack.add(pub.note);
                }
                hayStack.add(topic.getComment());
                return hayStack.stream()
                        .filter(token -> token != null && token.toLowerCase(Locale.getDefault()).contains(mQuery))
                        .findAny()
                        .orElse(null) != null;
            }
        };
    }

    interface ClickListener {
        void onClick(String topicName);
    }

    interface Filter {
        // Returns true to keep topic, false to ignore.
        boolean filter(ComTopic topic);
    }

    static class ContactDetailsLookup extends ItemDetailsLookup<String> {
        final RecyclerView mRecyclerView;

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
        final ChatsAdapter mAdapter;

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

    public static class ViewHolder extends RecyclerView.ViewHolder {
        final int viewType;
        TextView name;
        TextView unreadCount;
        TextView priv;
        ImageView messageStatus;
        AppCompatImageView avatarView;
        ImageView online;
        ImageView deleted;
        ImageView channel;
        ImageView group;
        ImageView verified;
        ImageView staff;
        ImageView danger;
        ImageView muted;
        ImageView blocked;
        ImageView archived;

        final ContactDetails details;
        ClickListener clickListener;

        ViewHolder(@NonNull View item, ClickListener cl, int viewType) {
            super(item);
            this.viewType = viewType;

            if (viewType == R.layout.contact) {
                name = item.findViewById(R.id.contactName);
                unreadCount = item.findViewById(R.id.unreadCount);
                priv = item.findViewById(R.id.contactPriv);
                messageStatus = item.findViewById(R.id.messageStatus);
                avatarView = item.findViewById(R.id.avatar);
                online = item.findViewById(R.id.online);
                deleted = item.findViewById(R.id.deleted);
                channel = item.findViewById(R.id.icon_channel);
                group = item.findViewById(R.id.icon_group);
                verified = item.findViewById(R.id.icon_verified);
                staff = item.findViewById(R.id.icon_staff);
                danger = item.findViewById(R.id.icon_danger);
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

        void bind(int position, final ComTopic<VxCard> topic, Storage.Message msg, boolean selected) {
            final Context context = itemView.getContext();
            final String topicName = topic.getName();

            details.pos = position;
            details.id = topicName;

            VxCard pub = topic.getPub();
            if (pub != null && pub.fn != null) {
                name.setText(pub.fn);
                name.setTypeface(null, Typeface.NORMAL);
            } else if (topic.isSlfType()) {
                name.setText(R.string.self_topic_title);
                name.setTypeface(null, Typeface.NORMAL);
            } else {
                name.setText(R.string.placeholder_contact_title);
                name.setTypeface(null, Typeface.ITALIC);
            }
            Drafty content = (msg != null && !msg.isDeleted()) ? msg.getContent() : null;
            if (content != null) {
                if (msg.isMine()) {
                    messageStatus.setVisibility(View.VISIBLE);
                    UiUtils.setMessageStatusIcon(messageStatus, msg.getStatus(),
                            topic.msgReadCount(msg.getSeqId()), topic.msgRecvCount(msg.getSeqId()));
                } else {
                    messageStatus.setVisibility(View.GONE);
                }
                priv.setText(content.preview(MAX_MESSAGE_PREVIEW_LENGTH)
                        .format(new PreviewFormatter(priv.getContext(), priv.getTextSize())));
            } else {
                messageStatus.setVisibility(View.GONE);
                priv.setText(topic.getComment());
            }

            int unread = topic.getUnreadCount();
            if (unread > 0) {
                unreadCount.setText(unread > 9 ? "9+" : String.valueOf(unread));
                unreadCount.setVisibility(View.VISIBLE);
            } else {
                unreadCount.setVisibility(View.GONE);
            }

            UiUtils.setAvatar(avatarView, pub, topicName, topic.isDeleted());

            if (topic.isChannel()) {
                online.setVisibility(View.INVISIBLE);
                channel.setVisibility(View.VISIBLE);
            } else if (topic.isSlfType()) {
                online.setVisibility(View.INVISIBLE);
                channel.setVisibility(View.GONE);
            } else {
                channel.setVisibility(View.GONE);
                if (topic.isGrpType()) {
                   group.setVisibility(View.VISIBLE);
                } else {
                    group.setVisibility(View.GONE);
                }
                if (topic.isDeleted()) {
                    online.setVisibility(View.GONE);
                } else {
                    online.setVisibility(View.VISIBLE);
                    online.setColorFilter(topic.getOnline() ? sColorOnline : sColorOffline);
                }
            }

            if (topic.isDeleted()) {
                itemView.setAlpha(0.8f);
                deleted.setVisibility(View.VISIBLE);
            } else {
                deleted.setVisibility(View.GONE);
                itemView.setAlpha(1.0f);
            }

            verified.setVisibility(topic.isTrustedVerified() ? View.VISIBLE : View.GONE);
            staff.setVisibility(topic.isTrustedStaff() ? View.VISIBLE : View.GONE);
            danger.setVisibility(topic.isTrustedDanger() ? View.VISIBLE : View.GONE);

            if (topic.isSlfType()) {
                muted.setVisibility(View.GONE);
            } else {
                muted.setVisibility(topic.isMuted() ? View.VISIBLE : View.GONE);
            }
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

                itemView.setOnClickListener(view -> clickListener.onClick(topicName));

                itemView.setActivated(false);
            }

            // Field lengths may have changed.
            itemView.invalidate();
        }
    }
}
