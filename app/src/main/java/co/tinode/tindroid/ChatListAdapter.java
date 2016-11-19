package co.tinode.tindroid;


import android.accounts.Account;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.widget.CursorAdapter;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.AppCompatImageView;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import co.tinode.tindroid.db.BaseDb;
import co.tinode.tindroid.db.TopicDb;
import co.tinode.tinodesdk.Topic;
import co.tinode.tinodesdk.model.Subscription;

/**
 * Handling contact list.
 */
public class ChatListAdapter extends CursorAdapter {
    private static final String TAG = "ChatListAdapter";

    private static final int QUERY_ID = 100;

    private Context mContext;
    private SparseBooleanArray mSelectedItems;

    public ChatListAdapter(AppCompatActivity context, String uid) {
        super(context, null, 0);
        mContext = context;
        mSelectedItems = new SparseBooleanArray();
        Log.d(TAG, "Initialized");
        context.getSupportLoaderManager().initLoader(QUERY_ID, null, new ChatsLoaderCallback(uid));

    }

    @Override
    public int getCount() {
        if (getCursor() == null) {
            return 0;
        }
        return super.getCount();
    }

    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
        LayoutInflater inflater = (LayoutInflater) mContext
                .getSystemService(AppCompatActivity.LAYOUT_INFLATER_SERVICE);

        final View item = inflater.inflate(R.layout.contact, parent, false);

        final ViewHolder holder = new ViewHolder();
        holder.name = (TextView) item.findViewById(R.id.contactName);
        holder.unreadCount = (TextView) item.findViewById(R.id.unreadCount);
        holder.contactPriv = (TextView) item.findViewById(R.id.contactPriv);
        holder.icon = (AppCompatImageView) item.findViewById(R.id.avatar);
        holder.online = (AppCompatImageView) item.findViewById(R.id.online);

        item.setTag(holder);

        return item;
    }

    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        final Subscription<VCard,String> s = TopicDb.readOne(cursor);
        final ViewHolder holder = (ViewHolder) view.getTag();

        holder.topic = s.topic;

        if (s.pub != null) {
            holder.name.setText(s.pub.fn);
        }
        holder.contactPriv.setText(s.priv);

        int unread = s.seq - s.read;
        if (unread > 0) {
            holder.unreadCount.setText(unread > 9 ? "9+" : String.valueOf(unread));
            holder.unreadCount.setVisibility(View.VISIBLE);
        } else {
            holder.unreadCount.setVisibility(View.INVISIBLE);
        }

        Bitmap bmp = s.pub != null ? s.pub.getBitmap() : null;
        if (bmp != null) {
            holder.icon.setImageDrawable(new RoundedImage(bmp));
        } else {
            Topic.TopicType topicType = Topic.getTopicTypeByName(s.topic);
            int res = -1;
            if (topicType == Topic.TopicType.GRP) {
                res = R.drawable.ic_group_circle;
            } else if (topicType == Topic.TopicType.P2P || topicType == Topic.TopicType.ME) {
                res = R.drawable.ic_person_circle;
            }

            Drawable drw;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                drw = mContext.getResources().getDrawable(res, mContext.getTheme());
            } else {
                drw = mContext.getResources().getDrawable(res);
            }
            if (drw != null) {
                holder.icon.setImageDrawable(drw);
            }
        }

        boolean online = Cache.isUserOnline(s.topic);
        holder.online.setColorFilter(online ? UiUtils.COLOR_ONLINE : UiUtils.COLOR_OFFLINE);
        Log.d(TAG, "User " + s.topic + " is " + (online ? "online" : "offline"));
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

    public void selectView(int position, boolean value) {
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

    private class ChatsLoaderCallback implements LoaderManager.LoaderCallbacks<Cursor> {
        private String mUid;

        ChatsLoaderCallback(String uid) {
            super();
            mUid = uid;
        }

        @Override
        public Loader<Cursor> onCreateLoader(int id, Bundle args) {
            Log.d(TAG, "ChatsLoaderCallback.onCreateLoader");
            // If this is the loader for finding contacts in the Contacts Provider
            if (id == ChatListAdapter.QUERY_ID) {
                return new ChatListLoader(mContext, mUid);
            }
            return null;
        }

        @Override
        public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
            Log.d(TAG, "ChatsLoaderCallback.onLoadFinished");
            // This swaps the new cursor into the adapter.
            if (loader.getId() == ChatListAdapter.QUERY_ID) {
                ChatListAdapter.this.swapCursor(data);
            }
        }

        @Override
        public void onLoaderReset(Loader<Cursor> loader) {
            if (loader.getId() == ChatListAdapter.QUERY_ID) {
                // When the loader is being reset, clear the cursor from the adapter. This allows the
                // cursor resources to be freed.
                ChatListAdapter.this.swapCursor(null);
            }
        }
    }

    private static class ChatListLoader extends CursorLoader {
        private String mUid;

        ChatListLoader(Context context, String uid) {
            super(context);
            mUid = uid;
        }

        @Override
        public Cursor loadInBackground() {
            SQLiteDatabase db = BaseDb.getInstance(getContext(), mUid).getReadableDatabase();
            return TopicDb.query(db);
        }
    }
}
