package co.tinode.tindroid;


import android.content.Context;
import android.graphics.Typeface;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.AppCompatImageView;
import android.util.SparseBooleanArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.List;

import co.tinode.tindroid.db.StoredTopic;
import co.tinode.tindroid.media.VxCard;
import co.tinode.tinodesdk.ComTopic;

/**
 * Handling contact list.
 */
public class ChatListAdapter extends BaseAdapter {
    private static final String TAG = "ChatListAdapter";

    private Context mContext;
    private List<ComTopic<VxCard>> mTopics;
    private SparseBooleanArray mSelectedItems;


    public ChatListAdapter(AppCompatActivity context) {
        super();
        mContext = context;
        mSelectedItems = new SparseBooleanArray();
        resetContent();
    }

    public void resetContent() {
        mTopics = Cache.getTinode().getFilteredTopics(ComTopic.TopicType.USER, null);
    }

    @Override
    public int getCount() {
        return mTopics.size();
    }

    @Override
    public Object getItem(int position) {
        return mTopics.get(position);
    }

    @Override
    public long getItemId(int position) {
        return StoredTopic.getId(mTopics.get(position));
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View item = convertView;
        ViewHolder holder;
        if (item == null) {
            LayoutInflater inflater = (LayoutInflater) mContext
                    .getSystemService(AppCompatActivity.LAYOUT_INFLATER_SERVICE);
            if (inflater == null) {
                return null;
            }
            item = inflater.inflate(R.layout.contact, parent, false);
            holder = new ViewHolder();
            holder.name = item.findViewById(R.id.contactName);
            holder.unreadCount = item.findViewById(R.id.unreadCount);
            holder.contactPriv = item.findViewById(R.id.contactPriv);
            holder.icon = item.findViewById(R.id.avatar);
            holder.online = item.findViewById(R.id.online);

            item.setTag(holder);
        } else {
            holder = (ViewHolder) item.getTag();
        }

        bindView(position, holder);

        return item;
    }

    private void bindView(int position, ViewHolder holder) {
        final ComTopic<VxCard> topic = mTopics.get(position);

        holder.topic = topic.getName();
        VxCard pub = topic.getPub();
        if (pub != null) {
            holder.name.setText(pub.fn);
            holder.name.setTypeface(null, Typeface.NORMAL);
        } else {
            holder.name.setText(R.string.placeholder_contact_title);
            holder.name.setTypeface(null, Typeface.ITALIC);
        }
        holder.contactPriv.setText(topic.getComment());

        int unread = topic.getUnreadCount();
        if (unread > 0) {
            holder.unreadCount.setText(unread > 9 ? "9+" : String.valueOf(unread));
            holder.unreadCount.setVisibility(View.VISIBLE);
        } else {
            holder.unreadCount.setVisibility(View.INVISIBLE);
        }

        UiUtils.assignBitmap(mContext, holder.icon,
                pub != null ? pub.getBitmap() : null,
                pub != null ? pub.fn : null,
                holder.topic);

        holder.online.setColorFilter(topic.getOnline() ? UiUtils.COLOR_ONLINE : UiUtils.COLOR_OFFLINE);
        // Log.d(TAG, "User " + topic.getName() + " is " + (topic.getOnline() ? "online" : "offline"));
    }

    public void toggleSelected(int position) {
        selectView(position, !mSelectedItems.get(position));
    }

    public void removeSelection() {
        mSelectedItems = new SparseBooleanArray();
        notifyDataSetChanged();
    }

    public String getTopicNameFromView(View view) {
        final ViewHolder holder = (ViewHolder) view.getTag();
        return holder != null ? holder.topic : null;
    }

    private void selectView(int position, boolean value) {
        if (value)
            mSelectedItems.put(position, true);
        else
            mSelectedItems.delete(position);
        notifyDataSetChanged();
    }


    public int getSelectedCount() {
        return mSelectedItems.size();
    }

    public SparseBooleanArray getSelectedIds() {
        return mSelectedItems;
    }

    private class ViewHolder {
        String topic;
        TextView name;
        TextView unreadCount;
        TextView contactPriv;
        AppCompatImageView icon;
        AppCompatImageView online;
    }
}
