package co.tinode.tindroid;

import android.app.Activity;
import android.content.Context;
import android.database.Cursor;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v4.widget.CursorAdapter;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.DateFormat;
import java.util.Calendar;
import java.util.Date;

import co.tinode.tindroid.db.Message;
import co.tinode.tinodesdk.Topic;
import co.tinode.tinodesdk.model.MsgServerData;

/**
 * Handle display of a conversation
 */
public class MessagesListAdapter extends CursorAdapter {
    private static final String TAG = "MessagesListAdapter";

    private static final int QUERY_ALL_MESSAGES = 100;

    // Vertical padding between two messages from different senders
    private static final int SINGLE_PADDING = 10;
    // Vertical padding between two messages from the same sender
    private static final int TRAIN_PADDING = 2;

    // Grouping messages from the same sender (controls rounding of borders)
    private enum DisplayAs {SINGLE, FIRST, MIDDLE, LAST}

    // Material colors, shade #200.
    // TODO(gene): maybe move to resource file
    private static final colorizer[] sColorizer = {
            new colorizer(0xffef9a9a, 0xff212121), new colorizer(0xffc5e1a5, 0xff212121),
            new colorizer(0xff90caf9, 0xff212121), new colorizer(0xfffff59d, 0xff212121),
            new colorizer(0xffb0bec5, 0xff212121), new colorizer(0xfff48fb1, 0xff212121),
            new colorizer(0xffb39ddb, 0xff212121), new colorizer(0xff9fa8da, 0xff212121),
            new colorizer(0xffffab91, 0xff212121), new colorizer(0xffffe082, 0xff212121),
            new colorizer(0xffa5d6a7, 0xff212121), new colorizer(0xffbcaaa4, 0xff212121),
            new colorizer(0xffeeeeee, 0xff212121), new colorizer(0xff80deea, 0xff212121),
            new colorizer(0xffe6ee9c, 0xff212121), new colorizer(0xffce93d8, 0xff212121)
    };

    private Context mContext;
    private String mTopicName;
    private Topic<?,?,String> mTopic;

    public MessagesListAdapter(Context context) {
        super(context, null, 0);
        mContext = context;
    }

    public void changeTopic(String topicName) {
        if (mTopicName == null || !mTopicName.equals(topicName)) {
            mTopicName = topicName;
            mTopic = InmemoryCache.getTinode().getTopic(topicName);
            notifyDataSetChanged();
        }
    }

    @Override
    public int getCount() {
        return mTopic != null ? mTopic.getCachedMsgCount() : 0;
    }

    @Override
    public Object getItem(int position) {
        return mTopic.getMessageAt(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        MsgServerData<String> m = mTopic.getMessageAt(position);
        int senderIdx = mTopic.getSenderIndex(m.from);
        boolean isMine = mTopic.isMyMessage(m.from);
        if (senderIdx < 0) {
            senderIdx = 0;
        }

        // Logic for less vertical spacing between subsequent messages from the same sender vs different senders;
        int messageCount = mTopic.getCachedMsgCount();
        String prevFrom = (position > 0) ? mTopic.getMessageAt(position-1).from : null;
        String nextFrom = (position + 1 < messageCount) ? mTopic.getMessageAt(position+1).from : null;
        DisplayAs display = DisplayAs.SINGLE;
        if (m.from.equals(prevFrom)) {
            if (m.from.equals(nextFrom)) {
                display = DisplayAs.MIDDLE;
            } else {
                display = DisplayAs.LAST;
            }
        } else if (m.from.equals(nextFrom)) {
            display = DisplayAs.FIRST;
        }

        LayoutInflater inflater = (LayoutInflater) mContext
                .getSystemService(Activity.LAYOUT_INFLATER_SERVICE);

        if (convertView == null) {
            convertView = inflater.inflate(R.layout.message, null);
        }

        LinearLayout container = (LinearLayout) convertView.findViewById(R.id.container);
        container.setGravity(isMine ? Gravity.RIGHT : Gravity.LEFT);

        // To make sure padding is properly set, first set background, then set text.
        View bubble = convertView.findViewById(R.id.messageBubble);
        int bg_bubble = isMine ? R.drawable.bubble_r : R.drawable.bubble_l;
        switch (display) {
            case SINGLE:
                bg_bubble = isMine ? R.drawable.bubble_r : R.drawable.bubble_l;
                container.setPadding(container.getPaddingLeft(), SINGLE_PADDING,
                        container.getPaddingRight(), SINGLE_PADDING);
                break;
            case FIRST:
                bg_bubble = isMine ? R.drawable.bubble_r_z : R.drawable.bubble_l_z;
                container.setPadding(container.getPaddingLeft(), SINGLE_PADDING,
                        container.getPaddingRight(), TRAIN_PADDING);
                break;
            case MIDDLE:
                bg_bubble = isMine ? R.drawable.bubble_r_z : R.drawable.bubble_l_z;
                container.setPadding(container.getPaddingLeft(), TRAIN_PADDING,
                        container.getPaddingRight(), TRAIN_PADDING);
                break;
            case LAST:
                bg_bubble = isMine ? R.drawable.bubble_r : R.drawable.bubble_l;
                container.setPadding(container.getPaddingLeft(), TRAIN_PADDING,
                        container.getPaddingRight(), SINGLE_PADDING);
                break;
        }
        bubble.setBackgroundResource(bg_bubble);
        if (!isMine) {
            bubble.getBackground().mutate()
                    .setColorFilter(sColorizer[senderIdx].bg, PorterDuff.Mode.MULTIPLY);
        }
        ((TextView) convertView.findViewById(R.id.messageText)).setText(m.content);
        ((TextView) convertView.findViewById(R.id.messageMeta)).setText(shortDate(m.ts));

        ImageView delivered = (ImageView) convertView.findViewById(R.id.messageViewedIcon);
        delivered.setImageResource(android.R.color.transparent);
        if (isMine) {
            if (mTopic.msgReadCount(m.seq) > 0) {
                delivered.setImageResource(R.drawable.ic_done_all);
            } else if (mTopic.msgRecvCount(m.seq) > 0) {
                delivered.setImageResource(R.drawable.ic_done);
            }
        }
        return convertView;
    }

    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
        return null;
    }

    @Override
    public void bindView(View view, Context context, Cursor cursor) {

    }

    private static class colorizer {
        int bg;
        int fg;

        colorizer(int bg, int fg) {
            this.bg = bg;
            this.fg = fg;
        }
    }

    private static String shortDate(Date date) {
        Calendar now = Calendar.getInstance();
        Calendar then = Calendar.getInstance();
        then.setTime(date);

        if (then.get(Calendar.YEAR) == now.get(Calendar.YEAR)) {
            if (then.get(Calendar.MONTH) == now.get(Calendar.MONTH) &&
                    then.get(Calendar.DATE) == now.get(Calendar.DATE)) {
                return DateFormat.getTimeInstance(DateFormat.SHORT).format(then.getTime());
            } else {
                return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(then.getTime());
            }
        }
        return DateFormat.getInstance().format(then.getTime());
    }

    class MessagesLoader implements LoaderManager.LoaderCallbacks<Cursor> {

        @Override
        public Loader<Cursor> onCreateLoader(int id, Bundle args) {
            if (id == QUERY_ALL_MESSAGES) {
                new Message.Loader(mContext, mTopicName, -1, -1);
            }
            return null;
        }

        @Override
        public void onLoadFinished(Loader<Cursor> loader,
                                   Cursor cursor) {
            MessagesListAdapter.this.swapCursor(cursor);
        }

        @Override
        public void onLoaderReset(Loader<Cursor> loader) {
            MessagesListAdapter.this.swapCursor(null);
        }
    }
}
