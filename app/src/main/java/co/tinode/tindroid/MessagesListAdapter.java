package co.tinode.tindroid;

import android.annotation.SuppressLint;
import android.database.Cursor;
import android.graphics.PorterDuff;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.support.v7.view.ActionMode;
import android.util.SparseBooleanArray;
import android.util.SparseIntArray;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.DateFormat;
import java.util.Calendar;
import java.util.Date;

import co.tinode.tindroid.db.MessageDb;
import co.tinode.tindroid.db.StoredMessage;
import co.tinode.tinodesdk.Topic;

/**
 * Handle display of a conversation
 */
public class MessagesListAdapter extends RecyclerView.Adapter<MessagesListAdapter.ViewHolder> {
    private static final String TAG = "MessagesListAdapter";

    // Vertical padding between two messages from different senders
    private static final int SINGLE_PADDING = 10;
    // Vertical padding between two messages from the same sender
    private static final int TRAIN_PADDING = 2;
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

    private MessageActivity mActivity;
    private Cursor mCursor;
    private String mTopicName;
    private ActionMode.Callback mSelectionModeCallback;
    private ActionMode mSelectionMode;

    private SparseBooleanArray mSelectedItems = null;

    public MessagesListAdapter(MessageActivity context) {
        super();
        mActivity = context;
        setHasStableIds(true);

        // Change
        mSelectionModeCallback = new ActionMode.Callback() {
            @Override
            public boolean onPrepareActionMode(ActionMode actionMode, Menu menu) {
                mSelectedItems = new SparseBooleanArray();
                return false;
            }

            @Override
            public void onDestroyActionMode(ActionMode actionMode) {
                SparseBooleanArray arr = mSelectedItems;
                mSelectedItems = null;
                if (arr.size() < 6) {
                    for (int i = 0; i < arr.size(); i++) {
                        notifyItemChanged(arr.keyAt(i));
                    }
                } else {
                    notifyDataSetChanged();
                }
            }

            @Override
            public boolean onCreateActionMode(ActionMode actionMode, Menu menu) {
                mActivity.getMenuInflater().inflate(R.menu.menu_message_selected, menu);
                return true;
            }

            @Override
            public boolean onActionItemClicked(ActionMode actionMode, MenuItem menuItem) {
                switch (menuItem.getItemId()) {
                    case R.id.action_delete:
                        Log.d(TAG, "Delete selected items");
                        mActivity.sendDeleteMessages(getSelectedArray());
                        return true;
                    case R.id.action_copy:
                        Log.d(TAG, "Copy selected item to clipboard");
                        mActivity.copyMessageText(getSelectedArray());
                        return true;
                    case R.id.action_send_now:
                        Log.d(TAG, "Try re-sending selected item");
                        return true;
                    case R.id.action_view_details:
                        Log.d(TAG, "Show message details");
                        return true;
                    default:
                        break;
                }
                return false;
            }
        };
    }

    private int[] getSelectedArray() {
        int[] items = new int[mSelectedItems.size()];
        for (int i=0; i< items.length; i++) {
            items[i] = mSelectedItems.keyAt(i);
        }
        return items;
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

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        // create a new view
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.message, parent, false);
        return new ViewHolder(v);
    }

    @SuppressLint("RtlHardcoded")
    @Override
    public void onBindViewHolder(final ViewHolder holder, int position) {

        mCursor.moveToPosition(position);
        StoredMessage<String> m = MessageDb.readMessage(mCursor);

        // Logic for less vertical spacing between subsequent messages from the same sender vs different senders;
        long prevFrom = -2;
        if (position > 0) {
            mCursor.moveToPrevious();
            prevFrom = MessageDb.readMessage(mCursor).userId;
        }
        long nextFrom = -2;
        if (position < getItemCount() - 1) {
            mCursor.moveToPosition(position + 1);
            nextFrom = MessageDb.readMessage(mCursor).userId;
        }
        DisplayAs display = DisplayAs.SINGLE;
        if (m.userId == prevFrom) {
            if (m.userId == nextFrom) {
                display = DisplayAs.MIDDLE;
            } else {
                display = DisplayAs.LAST;
            }
        } else if (m.userId == nextFrom) {
            display = DisplayAs.FIRST;
        }

        holder.mContent.setGravity(m.isMine() ? Gravity.RIGHT : Gravity.LEFT);

        // To make sure padding is properly set, first set background, then set text.
        int bg_bubble = m.isMine() ? R.drawable.bubble_r : R.drawable.bubble_l;
        switch (display) {
            case SINGLE:
                bg_bubble = m.isMine() ? R.drawable.bubble_r : R.drawable.bubble_l;
                holder.mContainer.setPadding(holder.mContainer.getPaddingLeft(), SINGLE_PADDING,
                        holder.mContainer.getPaddingRight(), SINGLE_PADDING);
                break;
            case FIRST:
                bg_bubble = m.isMine() ? R.drawable.bubble_r_z : R.drawable.bubble_l_z;
                holder.mContainer.setPadding(holder.mContainer.getPaddingLeft(), SINGLE_PADDING,
                        holder.mContainer.getPaddingRight(), TRAIN_PADDING);
                break;
            case MIDDLE:
                bg_bubble = m.isMine() ? R.drawable.bubble_r_z : R.drawable.bubble_l_z;
                holder.mContainer.setPadding(holder.mContainer.getPaddingLeft(), TRAIN_PADDING,
                        holder.mContainer.getPaddingRight(), TRAIN_PADDING);
                break;
            case LAST:
                bg_bubble = m.isMine() ? R.drawable.bubble_r : R.drawable.bubble_l;
                holder.mContainer.setPadding(holder.mContainer.getPaddingLeft(), TRAIN_PADDING,
                        holder.mContainer.getPaddingRight(), SINGLE_PADDING);
                break;
        }

        holder.mMessageBubble.setBackgroundResource(bg_bubble);
        if (!m.isMine()) {
            holder.mMessageBubble.getBackground().mutate()
                    .setColorFilter(sColorizer[m.senderIdx].bg, PorterDuff.Mode.MULTIPLY);
        }

        if (mSelectedItems != null && mSelectedItems.get(position)) {
            Log.d(TAG, "Visible item " + position);
            holder.mSelected.setVisibility(View.VISIBLE);
        } else {
            holder.mSelected.setVisibility(View.GONE);
        }

        holder.mText.setText(m.content);
        holder.mMeta.setText(shortDate(m.ts));

        holder.mDeliveredIcon.setImageResource(android.R.color.transparent);
        if (m.isMine()) {
            if (m.seq <= 0) {
                holder.mDeliveredIcon.setImageResource(R.drawable.ic_schedule);
            } else {
                Topic topic = Cache.getTinode().getTopic(mTopicName);

                if (topic.msgReadCount(m.seq) > 0) {
                    holder.mDeliveredIcon.setImageResource(R.drawable.ic_visibility);
                } else if (topic.msgRecvCount(m.seq) > 0) {
                    holder.mDeliveredIcon.setImageResource(R.drawable.ic_done_all);
                } else {
                    holder.mDeliveredIcon.setImageResource(R.drawable.ic_done);
                }
            }
        }

        holder.mOverlay.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                int pos = holder.getAdapterPosition();
                Log.d(TAG, "Long click in position " + pos);

                if (mSelectedItems == null) {
                    mSelectionMode = mActivity.startSupportActionMode(mSelectionModeCallback);
                }

                toggleSelectionAt(pos);
                notifyItemChanged(pos);
                updateSelectionMode();

                return true;
            }
        });
        holder.mOverlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mSelectedItems != null) {
                    int pos = holder.getAdapterPosition();
                    Log.d(TAG, "Short click in position " + pos);

                    toggleSelectionAt(pos);
                    notifyItemChanged(pos);
                    updateSelectionMode();
                }
            }
        });
    }

    @Override
    public long getItemId(int position) {
        mCursor.moveToPosition(position);
        return MessageDb.getLocalId(mCursor);
    }

    @Override
    public int getItemCount() {
        return mCursor != null ? mCursor.getCount() : 0;
    }

    private void toggleSelectionAt(int pos) {
        if (mSelectedItems.get(pos)) {
            mSelectedItems.delete(pos);
        } else {
            mSelectedItems.put(pos, true);
        }
    }

    private boolean updateSelectionMode() {
        if (mSelectionMode != null) {
            if (mSelectedItems.size() == 0) {
                mSelectionMode.finish();
                mSelectionMode = null;
            } else {
                mSelectionMode.setTitle(String.valueOf(mSelectedItems.size()));
            }
        }
        return mSelectionMode != null;
    }

    void swapCursor(final String topicName, final Cursor cursor) {
        // Clear selection
        if (mSelectionMode != null) {
            mSelectionMode.finish();
            mSelectionMode = null;
        }

        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "MessagesListAdapter.swapCursor");

                mTopicName = topicName;
                mCursor = cursor;
                notifyDataSetChanged();
                // -1 means scroll to the bottom
                (mActivity).scrollTo(-1);
            }
        });
    }

    StoredMessage<String> getMessage(int position) {
        mCursor.moveToPosition(position);
        return MessageDb.readMessage(mCursor);
    }

    // Grouping messages from the same sender (controls rounding of borders)
    private enum DisplayAs {
        SINGLE, FIRST, MIDDLE, LAST
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
        FrameLayout mContainer;
        LinearLayout mContent;
        View mMessageBubble;
        ImageView mDeliveredIcon;
        TextView mText;
        TextView mMeta;
        View mSelected;
        View mOverlay;

        ViewHolder(View itemView) {
            super(itemView);
            mItemView = itemView;
            mContainer = (FrameLayout) itemView.findViewById(R.id.container);
            mContent = (LinearLayout) itemView.findViewById(R.id.content);
            mMessageBubble = itemView.findViewById(R.id.messageBubble);
            mDeliveredIcon = (ImageView) itemView.findViewById(R.id.messageViewedIcon);
            mText = (TextView) itemView.findViewById(R.id.messageText);
            mMeta = (TextView) itemView.findViewById(R.id.messageMeta);
            mSelected = itemView.findViewById(R.id.selected);
            mOverlay = itemView.findViewById(R.id.overlay);
        }
    }
}
