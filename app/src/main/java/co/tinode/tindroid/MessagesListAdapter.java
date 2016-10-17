package co.tinode.tindroid;

import android.annotation.SuppressLint;
import android.content.Context;
import android.database.Cursor;
import android.graphics.PorterDuff;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
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

import co.tinode.tindroid.db.MessageDb;
import co.tinode.tinodesdk.Topic;
import co.tinode.tinodesdk.model.MsgServerData;

/**
 * Handle display of a conversation
 */
public class MessagesListAdapter extends RecyclerView.Adapter<MessagesListAdapter.ViewHolder> {
    private static final String TAG = "MessagesListAdapter";

    // Vertical padding between two messages from different senders
    private static final int SINGLE_PADDING = 10;
    // Vertical padding between two messages from the same sender
    private static final int TRAIN_PADDING = 2;

    // Grouping messages from the same sender (controls rounding of borders)
    private enum DisplayAs {SINGLE, FIRST, MIDDLE, LAST}

    private Cursor mCursor;

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

    private String mTopicName;
    private Topic<?,?,String> mTopic;

    public MessagesListAdapter(Context context) {
        super();
        setHasStableIds(true);
    }

    public void setTopic(String topicName) {
        if (mTopicName == null || !mTopicName.equals(topicName)) {
            mTopicName = topicName;
            mTopic = InmemoryCache.getTinode().getTopic(topicName);
        }
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        // create a new view
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.message, parent, false);
        return new ViewHolder(v);
    }

    @SuppressLint("RtlHardcoded")
    @Override
    public void onBindViewHolder(ViewHolder holder, int reversePosition) {
        // Reverse item order
        int position = getItemCount() - reversePosition - 1;

        mCursor.moveToPosition(position);
        MsgServerData<String> m = MessageDb.readMessage(mCursor);

        int senderIdx = mTopic.getSenderIndex(m.from);
        boolean isMine = mTopic.isMyMessage(m.from);
        if (senderIdx < 0) {
            senderIdx = 0;
        }

        // Logic for less vertical spacing between subsequent messages from the same sender vs different senders;
        String prevFrom = null;
        if (position > 0) {
            mCursor.moveToPrevious();
            prevFrom = MessageDb.readMessage(mCursor).from;
        }
        String nextFrom = null;
        if (reversePosition > 0) {
            mCursor.moveToPosition(position + 1);
            nextFrom = MessageDb.readMessage(mCursor).from;
        }
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

        holder.mContainer.setGravity(isMine ? Gravity.RIGHT : Gravity.LEFT);

        // To make sure padding is properly set, first set background, then set text.
        int bg_bubble = isMine ? R.drawable.bubble_r : R.drawable.bubble_l;
        switch (display) {
            case SINGLE:
                bg_bubble = isMine ? R.drawable.bubble_r : R.drawable.bubble_l;
                holder.mContainer.setPadding(holder.mContainer.getPaddingLeft(), SINGLE_PADDING,
                        holder.mContainer.getPaddingRight(), SINGLE_PADDING);
                break;
            case FIRST:
                bg_bubble = isMine ? R.drawable.bubble_r_z : R.drawable.bubble_l_z;
                holder.mContainer.setPadding(holder.mContainer.getPaddingLeft(), SINGLE_PADDING,
                        holder.mContainer.getPaddingRight(), TRAIN_PADDING);
                break;
            case MIDDLE:
                bg_bubble = isMine ? R.drawable.bubble_r_z : R.drawable.bubble_l_z;
                holder.mContainer.setPadding(holder.mContainer.getPaddingLeft(), TRAIN_PADDING,
                        holder.mContainer.getPaddingRight(), TRAIN_PADDING);
                break;
            case LAST:
                bg_bubble = isMine ? R.drawable.bubble_r : R.drawable.bubble_l;
                holder.mContainer.setPadding(holder.mContainer.getPaddingLeft(), TRAIN_PADDING,
                        holder.mContainer.getPaddingRight(), SINGLE_PADDING);
                break;
        }
        holder.mMessageBubble.setBackgroundResource(bg_bubble);
        if (!isMine) {
            holder.mMessageBubble.getBackground().mutate()
                    .setColorFilter(sColorizer[senderIdx].bg, PorterDuff.Mode.MULTIPLY);
        }
        holder.mContent.setText(m.content);
        holder.mMeta.setText(shortDate(m.ts));

        holder.mDeliveredIcon.setImageResource(android.R.color.transparent);
        if (isMine) {
            if (mTopic.msgReadCount(m.seq) > 0) {
                holder.mDeliveredIcon.setImageResource(R.drawable.ic_done_all);
            } else if (mTopic.msgRecvCount(m.seq) > 0) {
                holder.mDeliveredIcon.setImageResource(R.drawable.ic_done);
            }
        }
    }

    @Override
    public long getItemId(int reversePosition) {
        int position = getItemCount() - reversePosition - 1;
        mCursor.moveToPosition(position);
        return MessageDb.getLocalId(mCursor);
    }

    @Override
    public int getItemCount() {
        return mCursor != null ? mCursor.getCount() : 0;
    }

    private static String shortDate(Date date) {
        if (date != null) {
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
        return "null date";
    }

    void swapCursor(Cursor cursor) {
        Log.d(TAG, "MessagesListAdapter.swapCursor");
        mCursor = cursor;

        notifyDataSetChanged();
    }

    private static class colorizer {
        int bg;
        int fg;

        colorizer(int bg, int fg) {
            this.bg = bg;
            this.fg = fg;
        }
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        View mItemView;
        LinearLayout mContainer;
        View mMessageBubble;
        ImageView mDeliveredIcon;
        TextView mContent;
        TextView mMeta;

        ViewHolder(View itemView) {
            super(itemView);
            mItemView = itemView;
            mContainer = (LinearLayout) itemView.findViewById(R.id.container);
            mMessageBubble = itemView.findViewById(R.id.messageBubble);
            mDeliveredIcon = (ImageView) itemView.findViewById(R.id.messageViewedIcon);
            mContent = (TextView) itemView.findViewById(R.id.messageText);
            mMeta = (TextView) itemView.findViewById(R.id.messageMeta);
        }
    }
}
