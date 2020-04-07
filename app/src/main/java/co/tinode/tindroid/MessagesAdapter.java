package co.tinode.tindroid;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.app.ActivityCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.loader.app.LoaderManager;
import androidx.core.content.FileProvider;
import androidx.loader.content.Loader;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.appcompat.view.ActionMode;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.recyclerview.widget.RecyclerView;

import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.ForegroundColorSpan;
import android.text.style.IconMarginSpan;
import android.text.style.StyleSpan;
import android.util.Base64;
import android.util.Log;
import android.util.LongSparseArray;
import android.util.SparseBooleanArray;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import co.tinode.tindroid.db.BaseDb;
import co.tinode.tindroid.db.MessageDb;
import co.tinode.tindroid.db.StoredMessage;
import co.tinode.tindroid.media.SpanFormatter;
import co.tinode.tindroid.media.VxCard;
import co.tinode.tindroid.widgets.LetterTileDrawable;
import co.tinode.tindroid.widgets.RoundImageDrawable;
import co.tinode.tinodesdk.ComTopic;
import co.tinode.tinodesdk.LargeFileHelper;
import co.tinode.tinodesdk.PromisedReply;
import co.tinode.tinodesdk.Storage;
import co.tinode.tinodesdk.Topic;
import co.tinode.tinodesdk.model.Drafty;
import co.tinode.tinodesdk.model.MsgRange;
import co.tinode.tinodesdk.model.ServerMessage;
import co.tinode.tinodesdk.model.Subscription;

/**
 * Handle display of a conversation
 */
public class MessagesAdapter extends RecyclerView.Adapter<MessagesAdapter.ViewHolder> {
    private static final String TAG = "MessagesAdapter";

    private static final int MESSAGES_TO_LOAD = 20;

    private static final int MESSAGES_QUERY_ID = 200;

    private static final String HARD_RESET = "hard_reset";
    private static final int REFRESH_NONE = 0;
    private static final int REFRESH_SOFT = 1;
    private static final int REFRESH_HARD = 2;

    private static final int VIEWTYPE_FULL_LEFT = 0;
    private static final int VIEWTYPE_SIMPLE_LEFT = 1;
    private static final int VIEWTYPE_FULL_AVATAR = 2;
    private static final int VIEWTYPE_SIMPLE_AVATAR = 3;
    private static final int VIEWTYPE_FULL_RIGHT = 4;
    private static final int VIEWTYPE_SIMPLE_RIGHT = 5;
    private static final int VIEWTYPE_CENTER = 6;
    private static final int VIEWTYPE_INVALID = 100;

    // Storage Permissions
    private static final int REQUEST_EXTERNAL_STORAGE = 1;
    private static final String[] PERMISSIONS_STORAGE = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    private static Spanned sInvalidContent = null;

    private MessageActivity mActivity;
    private RecyclerView mRecyclerView;

    private Cursor mCursor;
    private String mTopicName = null;
    private ActionMode.Callback mSelectionModeCallback;
    private ActionMode mSelectionMode;

    private SparseBooleanArray mSelectedItems = null;

    private int mPagesToLoad;
    private SwipeRefreshLayout mRefresher;

    private MessageLoaderCallbacks mMessageLoaderCallback;

    // This is a map of message IDs to their corresponding loader IDs.
    // This is needed for upload cancellations.
    private LongSparseArray<Integer> mLoaders;

    private SpanClicker mSpanFormatterClicker;

    MessagesAdapter(MessageActivity context, SwipeRefreshLayout refresher) {
        super();

        mActivity = context;
        setHasStableIds(true);

        mRefresher = refresher;
        mPagesToLoad = 1;

        mLoaders = new LongSparseArray<>();

        mMessageLoaderCallback = new MessageLoaderCallbacks();

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
                        sendDeleteMessages(getSelectedArray());
                        return true;
                    case R.id.action_copy:
                        copyMessageText(getSelectedArray());
                        return true;
                    case R.id.action_send_now:
                        // FIXME: implement resending now.
                        Log.d(TAG, "Try re-sending selected item");
                        return true;
                    case R.id.action_view_details:
                        // FIXME: implement viewing message details.
                        Log.d(TAG, "Show message details");
                        return true;
                    default:
                        break;
                }
                return false;
            }
        };

        mSpanFormatterClicker = new SpanClicker();

        verifyStoragePermissions();
    }

    @Override
    public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);

        mRecyclerView = recyclerView;
    }

    private int[] getSelectedArray() {
        if (mSelectedItems == null || mSelectedItems.size() == 0) {
            return null;
        }

        int[] items = new int[mSelectedItems.size()];
        for (int i = 0; i < items.length; i++) {
            items[i] = mSelectedItems.keyAt(i);
        }
        return items;
    }

    @SuppressWarnings("unchecked")
    private void copyMessageText(int[] positions) {
        if (positions == null || positions.length == 0) {
            return;
        }

        StringBuilder sb = new StringBuilder();
        final Topic topic = Cache.getTinode().getTopic(mTopicName);
        if (topic == null) {
            return;
        }

        // The list is inverted, so iterating messages in inverse order as well.
        for (int i = positions.length - 1; i >= 0; i--) {
            StoredMessage msg = getMessage(positions[i]);
            if (msg != null) {
                Subscription<VxCard, ?> sub = (Subscription<VxCard, ?>) topic.getSubscription(msg.from);
                String name = (sub != null && sub.pub != null) ? sub.pub.fn : msg.from;
                sb.append("\n[").append(name).append("]: ").append(msg.content).append("; ")
                        .append(UiUtils.shortDate(msg.ts));
            }
        }

        if (sb.length() > 1) {
            // Delete unnecessary CR in the beginning.
            sb.deleteCharAt(0);
            String text = sb.toString();

            ClipboardManager clipboard = (ClipboardManager) mActivity.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText("message text", text));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void sendDeleteMessages(final int[] positions) {
        if (positions == null || positions.length == 0) {
            return;
        }

        final Topic topic = Cache.getTinode().getTopic(mTopicName);
        final Storage store = BaseDb.getInstance().getStore();
        if (topic != null) {
            ArrayList<Integer> toDelete = new ArrayList<>();
            int i = 0;
            int discarded = 0;
            while (i < positions.length) {
                int pos = positions[i++];
                StoredMessage msg = getMessage(pos);
                if (msg != null) {
                    if (msg.status == BaseDb.Status.SYNCED) {
                        toDelete.add(msg.seq);
                    } else {
                        store.msgDiscard(topic, msg.getId());
                        discarded ++;
                    }
                }
            }

            if (!toDelete.isEmpty()) {
                topic.delMessages(MsgRange.listToRanges(toDelete), true)
                        .thenApply(new PromisedReply.SuccessListener<ServerMessage>() {
                            @Override
                            public PromisedReply<ServerMessage> onSuccess(ServerMessage result) {
                                runLoader(false);
                                mActivity.runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        updateSelectionMode();
                                    }
                                });
                                return null;
                            }
                }, new UiUtils.ToastFailureListener(mActivity));
            } else if (discarded > 0) {
                runLoader(false);
                updateSelectionMode();
            }
        }
    }

    @Override
    public int getItemViewType(int position) {
        int itemType;
        StoredMessage m = getMessage(position);
        Topic.TopicType tp = Topic.getTopicTypeByName(mTopicName);

        if (m == null) {
            return VIEWTYPE_INVALID;
        }

        if (m.delId > 0) {
            return VIEWTYPE_CENTER;
        }
        // Logic for less vertical spacing between subsequent messages from the same sender vs different senders.
        // Zero item position is on the bottom of the screen.
        long nextFrom = -2;
        if (position > 0) {
            StoredMessage m2 = getMessage(position - 1);
            if (m2 != null) {
                nextFrom = m2.userId;
            }
        }

        final boolean isMine = m.isMine();

        if (m.userId != nextFrom) {
            itemType = isMine ? VIEWTYPE_FULL_RIGHT :
                    tp == Topic.TopicType.GRP ? VIEWTYPE_FULL_AVATAR :
                            VIEWTYPE_FULL_LEFT;
        } else {
            itemType = isMine ? VIEWTYPE_SIMPLE_RIGHT :
                    tp == Topic.TopicType.GRP ? VIEWTYPE_SIMPLE_AVATAR :
                            VIEWTYPE_SIMPLE_LEFT;
        }

        return itemType;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Create a new message bubble view.
        View v;

        final Resources res = mActivity.getResources();
        final int leftBgColor = ResourcesCompat.getColor(res, R.color.colorMessageBubbleOther, null);
        final int rightBgColor = ResourcesCompat.getColor(res, R.color.colorMessageBubbleMine, null);
        final int metaBgColor = ResourcesCompat.getColor(res, R.color.colorMessageBubbleMeta, null);
        int bgColor = 0;
        switch (viewType) {
            case VIEWTYPE_CENTER:
                v = LayoutInflater.from(parent.getContext()).inflate(R.layout.meta_message,
                        parent, false);
                bgColor = metaBgColor;
                break;
            case VIEWTYPE_FULL_LEFT:
                v = LayoutInflater.from(parent.getContext()).inflate(R.layout.message_left_single,
                        parent, false);
                bgColor = leftBgColor;
                break;
            case VIEWTYPE_FULL_AVATAR:
                v = LayoutInflater.from(parent.getContext()).inflate(R.layout.message_left_single_avatar,
                        parent, false);
                bgColor = leftBgColor;
                break;
            case VIEWTYPE_FULL_RIGHT:
                v = LayoutInflater.from(parent.getContext()).inflate(R.layout.message_right_single,
                        parent, false);
                bgColor = rightBgColor;
                break;
            case VIEWTYPE_SIMPLE_LEFT:
                v = LayoutInflater.from(parent.getContext()).inflate(R.layout.message_left,
                        parent, false);
                bgColor = leftBgColor;
                break;
            case VIEWTYPE_SIMPLE_AVATAR:
                v = LayoutInflater.from(parent.getContext()).inflate(R.layout.message_left_avatar,
                        parent, false);
                bgColor = leftBgColor;
                break;
            case VIEWTYPE_SIMPLE_RIGHT:
                v = LayoutInflater.from(parent.getContext()).inflate(R.layout.message_right,
                        parent, false);
                bgColor = rightBgColor;
                break;
            default:
                v = null;
        }

        if (bgColor != 0) {
            v.findViewById(R.id.messageBubble).getBackground().mutate()
                    .setColorFilter(bgColor, PorterDuff.Mode.MULTIPLY);
        }

        return new ViewHolder(v, viewType);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onBindViewHolder(@NonNull final ViewHolder holder, int position, @NonNull List<Object> payload) {
        if (!payload.isEmpty()) {
            Float progress = (Float) payload.get(0);
            holder.mProgressBar.setProgress((int) (progress * 100));
            return;
        }

        onBindViewHolder(holder, position);
    }

    @SuppressLint("ClickableViewAccessibility")
    @SuppressWarnings("unchecked")
    @Override
    public void onBindViewHolder(@NonNull final ViewHolder holder, int position) {

        final ComTopic<VxCard> topic = (ComTopic<VxCard>) Cache.getTinode().getTopic(mTopicName);
        final StoredMessage m = getMessage(position);

        if (topic == null || m == null) {
            return;
        }

        if (holder.mIcon != null) {
            // Meta bubble in the center of the screen
            holder.mIcon.setVisibility(View.VISIBLE);
            holder.mText.setText(R.string.content_deleted);
            return;
        }

        // Disable attachment clicker.
        boolean disableEnt = (m.status == BaseDb.Status.QUEUED || m.status == BaseDb.Status.DRAFT) &&
                (m.content != null && m.content.getEntReferences() != null);

        mSpanFormatterClicker.setPosition(position);
        Spanned text = SpanFormatter.toSpanned(holder.mText, m.content, disableEnt ? null : mSpanFormatterClicker);
        if (text.length() == 0) {
            text = invalidContentSpanned(mActivity);
        }

        holder.mText.setText(text);
        if (SpanFormatter.hasClickableSpans(m.content)) {
            holder.mText.setMovementMethod(LinkMovementMethod.getInstance());
            holder.mText.setLinksClickable(true);
            holder.mText.setFocusable(true);
            holder.mText.setClickable(true);
        } else {
            holder.mText.setMovementMethod(null);
            holder.mText.setLinksClickable(false);
            holder.mText.setFocusable(false);
            holder.mText.setClickable(false);
            holder.mText.setAutoLinkMask(0);
        }
        if (holder.mProgressInclude != null) {
            if (disableEnt) {
                final long msgId = m.getId();
                holder.mProgressResult.setVisibility(View.GONE);
                holder.mProgress.setVisibility(View.VISIBLE);
                holder.mProgressInclude.setVisibility(View.VISIBLE);
                holder.mCancelProgress.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (cancelUpload(msgId)) {
                            holder.mProgress.setVisibility(View.GONE);
                            holder.mProgressResult.setVisibility(View.VISIBLE);
                        }
                    }
                });
            } else {
                holder.mProgressInclude.setVisibility(View.GONE);
                holder.mCancelProgress.setOnClickListener(null);
            }
        }

        if (holder.mSelected != null) {
            if (mSelectedItems != null && mSelectedItems.get(position)) {
                holder.mSelected.setVisibility(View.VISIBLE);
            } else {
                holder.mSelected.setVisibility(View.GONE);
            }
        }

        if (holder.mAvatar != null || holder.mUserName != null) {
            Subscription<VxCard,?> sub = topic.getSubscription(m.from);
            if (sub != null && sub.pub != null) {
                Bitmap avatar = sub.pub.getBitmap();
                if (holder.mAvatar != null) {
                    if (avatar != null) {
                        holder.mAvatar.setImageDrawable(
                                new RoundImageDrawable(mActivity.getResources(), avatar));
                    } else {
                        holder.mAvatar.setImageDrawable(
                                new LetterTileDrawable(mActivity)
                                        .setLetterAndColor(sub.pub.fn, sub.user)
                                        .setContactTypeAndColor(LetterTileDrawable.ContactType.PERSON));
                    }
                }

                if (holder.mUserName != null) {
                    holder.mUserName.setText(sub.pub.fn);
                }
            } else {
                if (holder.mAvatar != null) {
                    holder.mAvatar.setImageResource(R.drawable.ic_person_circle);
                }
                if (holder.mUserName != null) {
                    Spannable span = new SpannableString(mActivity.getString(R.string.user_not_found));
                    span.setSpan(new StyleSpan(Typeface.ITALIC), 0, span.length(),
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    holder.mUserName.setText(span);
                }
            }
        }

        if (holder.mMeta != null) {
            holder.mMeta.setText(UiUtils.shortDate(m.ts));
        }

        if (holder.mDeliveredIcon != null) {
            holder.mDeliveredIcon.setImageResource(android.R.color.transparent);
            if (holder.mViewType == VIEWTYPE_FULL_RIGHT || holder.mViewType == VIEWTYPE_SIMPLE_RIGHT) {
                if (m.status.value <= BaseDb.Status.SENDING.value) {
                    holder.mDeliveredIcon.setImageResource(R.drawable.ic_schedule);
                } else {
                    if (topic.msgReadCount(m.seq) > 0) {
                        holder.mDeliveredIcon.setImageResource(R.drawable.ic_visibility);
                    } else if (topic.msgRecvCount(m.seq) > 0) {
                        holder.mDeliveredIcon.setImageResource(R.drawable.ic_done_all);
                    } else {
                        holder.mDeliveredIcon.setImageResource(R.drawable.ic_done);
                    }
                }
            }
        }

        holder.itemView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                int pos = holder.getAdapterPosition();

                if (mSelectedItems == null) {
                    mSelectionMode = mActivity.startSupportActionMode(mSelectionModeCallback);
                }

                toggleSelectionAt(pos);
                notifyItemChanged(pos);
                updateSelectionMode();

                return true;
            }
        });
        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mSelectedItems != null) {
                    int pos = holder.getAdapterPosition();
                    toggleSelectionAt(pos);
                    notifyItemChanged(pos);
                    updateSelectionMode();
                }
            }
        });
    }

    // Generates "( ! ) invalid content" message when Drafty fails to represent content.
    private static Spanned invalidContentSpanned(Context ctx) {
        if (sInvalidContent != null) {
            return sInvalidContent;
        }
        SpannableString span = new SpannableString(ctx.getString(R.string.invalid_content));
        span.setSpan(new StyleSpan(Typeface.ITALIC), 0, span.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        span.setSpan(new ForegroundColorSpan(Color.rgb(0x75, 0x75, 0x75)),
                0, span.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        Drawable icon = AppCompatResources.getDrawable(ctx, R.drawable.ic_error_gray);
        span.setSpan(new IconMarginSpan(UiUtils.bitmapFromDrawable(icon), 24),
                0, span.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sInvalidContent = span;
        return span;
    }

    // Must match position-to-item of getItemId.
    private StoredMessage getMessage(int position) {
        if (mCursor != null && !mCursor.isClosed() && mCursor.moveToPosition(position)) {
            return StoredMessage.readMessage(mCursor);
        }
        return null;
    }

    @Override
    // Must match position-to-item of getMessage.
    public long getItemId(int position) {
        if (mCursor != null && !mCursor.isClosed() && mCursor.moveToPosition(position)) {
            return MessageDb.getLocalId(mCursor);
        }
        return View.NO_ID;
    }

    int findItemPositionById(long itemId, int first, int last) {
        if (mCursor == null || mCursor.isClosed()) {
            return -1;
        }

        for (int i = first; i <= last; i++) {
            if (mCursor.moveToPosition(i)) {
                if (MessageDb.getLocalId(mCursor) == itemId) {
                    return i;
                }
            }
        }
        return -1;
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

    @SuppressWarnings("UnusedReturnValue")
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

    void resetContent(@Nullable final String topicName) {
        if (topicName == null) {
            boolean hard = mTopicName != null;
            mTopicName = null;
            swapCursor(null, hard ? REFRESH_HARD : REFRESH_NONE);
        } else {
            boolean hard = !topicName.equals(mTopicName);
            mTopicName = topicName;
            runLoader(hard);
        }
    }

    private void swapCursor(final Cursor cursor, final int refresh) {
        if (mCursor != null && mCursor == cursor) {
            return;
        }

        // Clear selection
        if (mSelectionMode != null) {
            mSelectionMode.finish();
            mSelectionMode = null;
        }

        Cursor oldCursor = mCursor;
        mCursor = cursor;
        if (oldCursor != null) {
            oldCursor.close();
        }

        if (refresh != REFRESH_NONE) {
            mActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    int position = -1;
                    if (cursor != null) {
                        LinearLayoutManager lm = (LinearLayoutManager) mRecyclerView.getLayoutManager();
                        if (lm != null) {
                            position = lm.findFirstVisibleItemPosition();
                        }
                    }
                    mRefresher.setRefreshing(false);
                    if (refresh == REFRESH_HARD) {
                        mRecyclerView.setAdapter(MessagesAdapter.this);
                    } else {
                        notifyDataSetChanged();
                    }
                    if (cursor != null) {
                        if (position == 0) {
                            mRecyclerView.scrollToPosition(0);
                        }
                    }
                }
            });
        }
    }

    /**
     * Checks if the app has permission to write to device storage
     *
     * If the app does not has permission then the user will be prompted to grant permission.
     */
    private boolean verifyStoragePermissions() {
        // Check if we have write permission
        if (!UiUtils.isPermissionGranted(mActivity, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            // We don't have permission so prompt the user
            ActivityCompat.requestPermissions(mActivity, PERMISSIONS_STORAGE, REQUEST_EXTERNAL_STORAGE);
            return false;
        }
        return true;
    }

    // Run loader on UI thread
    private void runLoader(final boolean hard) {
        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                final LoaderManager lm = LoaderManager.getInstance(mActivity);
                final Loader<Cursor> loader = lm.getLoader(MESSAGES_QUERY_ID);
                Bundle args = new Bundle();
                args.putBoolean(HARD_RESET, hard);
                if (loader != null && !loader.isReset()) {
                    lm.restartLoader(MESSAGES_QUERY_ID, args, mMessageLoaderCallback);
                } else {
                    lm.initLoader(MESSAGES_QUERY_ID, args, mMessageLoaderCallback);
                }
            }
        });
    }

    boolean loadNextPage() {
        if (getItemCount() == mPagesToLoad * MESSAGES_TO_LOAD) {
            mPagesToLoad++;
            runLoader(false);
            return true;
        }

        return false;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        int mViewType;
        ImageView mIcon;
        ImageView mAvatar;
        View mMessageBubble;
        AppCompatImageView mDeliveredIcon;
        TextView mText;
        TextView mMeta;
        TextView mUserName;
        View mSelected;
        View mOverlay;
        View mProgressInclude;
        ProgressBar mProgressBar;
        AppCompatImageButton mCancelProgress;
        View mProgress;
        View mProgressResult;

        ViewHolder(View itemView, int viewType) {
            super(itemView);

            mViewType = viewType;
            mIcon = itemView.findViewById(R.id.icon);
            mAvatar = itemView.findViewById(R.id.avatar);
            mMessageBubble = itemView.findViewById(R.id.messageBubble);
            mDeliveredIcon = itemView.findViewById(R.id.messageViewedIcon);
            mText = itemView.findViewById(R.id.messageText);
            mMeta = itemView.findViewById(R.id.messageMeta);
            mUserName = itemView.findViewById(R.id.userName);
            mSelected = itemView.findViewById(R.id.selected);
            mOverlay = itemView.findViewById(R.id.overlay);
            mProgressInclude = itemView.findViewById(R.id.progressInclide);
            mProgress = itemView.findViewById(R.id.progressPanel);
            mProgressBar = itemView.findViewById(R.id.attachmentProgressBar);
            mCancelProgress = itemView.findViewById(R.id.attachmentProgressCancel);
            mProgressResult = itemView.findViewById(R.id.progressResult);
        }
    }

    void addLoaderMapping(Long msgId, int loaderId) {
        mLoaders.put(msgId, loaderId);
    }

    Integer getLoaderMapping(Long msgId) {
        return mLoaders.get(msgId);
    }

    private boolean cancelUpload(long msgId) {
        Integer loaderId = mLoaders.get(msgId);
        if (loaderId != null) {
            LoaderManager.getInstance(mActivity).destroyLoader(loaderId);
            // Change mapping to force background loading process to return early.
            addLoaderMapping(msgId, -1);
            return true;
        }
        return false;
    }

    private void downloadAttachment(Map<String,Object> data, String fname, String mimeType) {

        // Create file in a downloads directory by default.
        File path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File file = new File(path, fname);
        Uri fileUri = Uri.fromFile(file);

        if (TextUtils.isEmpty(mimeType)) {
            mimeType = UiUtils.getMimeType(fileUri);
            if (mimeType == null) {
                mimeType = "*/*";
            }
        }

        FileOutputStream fos = null;
        try {
            if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
                Log.w(TAG, "External storage not mounted: " + path);

            } else if (!(path.mkdirs() || path.isDirectory())) {
                Log.w(TAG, "Path is not a directory - " + path);
            }

            Object val = data.get("val");
            if (val != null) {
                fos = new FileOutputStream(file);
                fos.write(val instanceof String ?
                        Base64.decode((String) val, Base64.DEFAULT) :
                        (byte[]) val);

                Intent intent = new Intent();
                intent.setAction(android.content.Intent.ACTION_VIEW);
                intent.setDataAndType(FileProvider.getUriForFile(mActivity,
                        "co.tinode.tindroid.provider", file), mimeType);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                try {
                    mActivity.startActivity(intent);
                } catch (ActivityNotFoundException ignored) {
                    mActivity.startActivity(new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS));
                }

            } else {
                Object ref = data.get("ref");
                if (ref instanceof String) {
                    URL url = new URL(Cache.getTinode().getBaseUrl(), (String) ref);
                    String scheme = url.getProtocol();
                    // Make sure the file is downloaded over http or https protocols.
                    if (scheme.equals("http") || scheme.equals("https")) {
                        LargeFileHelper lfh = Cache.getTinode().getFileUploader();
                        mActivity.startDownload(Uri.parse(url.toString()), fname, mimeType, lfh.headers());
                    } else {
                        Log.w(TAG, "Unsupported transport protocol '" + scheme + "'");
                        Toast.makeText(mActivity, R.string.failed_to_download, Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Log.w(TAG, "Invalid or missing attachment");
                    Toast.makeText(mActivity, R.string.failed_to_download, Toast.LENGTH_SHORT).show();
                }
            }

        } catch (NullPointerException | ClassCastException | IOException ex) {
            Log.w(TAG, "Failed to save attachment to storage", ex);
            Toast.makeText(mActivity, R.string.failed_to_save_download, Toast.LENGTH_SHORT).show();
        } catch (ActivityNotFoundException ex) {
            Log.w(TAG, "No application can handle downloaded file");
            Toast.makeText(mActivity, R.string.failed_to_open_file, Toast.LENGTH_SHORT).show();
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (Exception ignored) {}
            }
        }
    }

    private class MessageLoaderCallbacks implements LoaderManager.LoaderCallbacks<Cursor> {
        private boolean mHardReset;

        @NonNull
        @Override
        public Loader<Cursor> onCreateLoader(int id, Bundle args) {
            if (id == MESSAGES_QUERY_ID) {
                if (args != null) {
                    mHardReset = args.getBoolean(HARD_RESET, false);
                }
                return new MessageDb.Loader(mActivity, mTopicName, mPagesToLoad, MESSAGES_TO_LOAD);
            }

            throw new IllegalArgumentException("Unknown loader id " + id);
        }

        @Override
        public void onLoadFinished(@NonNull Loader<Cursor> loader,
                                   Cursor cursor) {
            if (loader.getId() == MESSAGES_QUERY_ID) {
                swapCursor(cursor, mHardReset ? REFRESH_HARD : REFRESH_SOFT);
            }
        }

        @Override
        public void onLoaderReset(@NonNull Loader<Cursor> loader) {
            if (loader.getId() == MESSAGES_QUERY_ID) {
                swapCursor(null, mHardReset ? REFRESH_HARD : REFRESH_SOFT);
            }
        }
    }

    class SpanClicker implements SpanFormatter.ClickListener {
        private int mPosition = -1;

        void setPosition(int pos) {
            mPosition = pos;
        }

        @Override
        public void onClick(String type, Map<String, Object> data) {
            if (mSelectedItems != null) {
                toggleSelectionAt(mPosition);
                notifyItemChanged(mPosition);
                updateSelectionMode();
                return;
            }

            switch (type) {
                case "LN":
                    // Click on an URL
                    try {
                        if (data != null) {
                            URL url = new URL(Cache.getTinode().getBaseUrl(), (String) data.get("url"));
                            String scheme = url.getProtocol();
                            if (!scheme.equals("http") && !scheme.equals("https")) {
                                // As a security measure refuse to follow URLs with non-http(s) protocols.
                                break;
                            }
                            mActivity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url.toString())));
                        }
                    } catch (ClassCastException | MalformedURLException | NullPointerException ignored) {
                    }
                    break;

                case "IM":
                    // Image
                    Bundle args = new Bundle();
                    if (data != null) {
                        try {
                            Object val = data.get("val");
                            args.putByteArray("image", val instanceof String ?
                                    Base64.decode((String) val, Base64.DEFAULT) :
                                    (byte[]) val);
                            args.putString("mime", (String) data.get("mime"));
                            args.putString("name", (String) data.get("name"));
                        } catch (ClassCastException ignored) {
                        }
                    }

                    if (args.getByteArray("image") != null) {
                        mActivity.showFragment(MessageActivity.FRAGMENT_VIEW_IMAGE, args, true);
                    } else {
                        Toast.makeText(mActivity, R.string.broken_image, Toast.LENGTH_SHORT).show();
                    }

                    break;

                case "EX":
                    // Attachment
                    if (verifyStoragePermissions()) {
                        String fname = null;
                        String mimeType = null;
                        try {
                            fname = (String) data.get("name");
                            mimeType = (String) data.get("mime");
                        } catch (ClassCastException ignored) {
                        }

                        if (TextUtils.isEmpty(fname)) {
                            fname = mActivity.getString(R.string.default_attachment_name);
                        }

                        downloadAttachment(data, fname, mimeType);
                    } else {
                        Toast.makeText(mActivity, R.string.failed_to_save_download, Toast.LENGTH_SHORT).show();
                    }
                    break;

                case "BN":
                    // Button
                    if (data != null) {
                        try {
                            String actionType = (String) data.get("act");
                            String actionValue = (String) data.get("val");
                            String name = (String) data.get("name");
                            StoredMessage msg = getMessage(mPosition);
                            if ("pub".equals(actionType)) {
                                Drafty newMsg = new Drafty((String) data.get("title"));
                                Map<String,Object> json = new HashMap<>();
                                // {"seq":6,"resp":{"yes":1}}
                                if (!TextUtils.isEmpty(name)) {
                                    Map<String,Object> resp = new HashMap<>();
                                    // noinspection
                                    resp.put(name, TextUtils.isEmpty(actionValue) ? 1 : actionValue);
                                    json.put("resp", resp);
                                }
                                if (msg != null) {
                                    json.put("seq", "" + msg.seq);
                                }
                                if (!json.isEmpty()) {
                                    newMsg.attachJSON(json);
                                }
                                mActivity.sendMessage(newMsg);

                            } else if ("url".equals(actionType)) {
                                URL url = new URL(Cache.getTinode().getBaseUrl(), (String) data.get("ref"));
                                String scheme = url.getProtocol();
                                if (!scheme.equals("http") && !scheme.equals("https")) {
                                    // As a security measure refuse to follow URLs with non-http(s) protocols.
                                    break;
                                }
                                Uri uri =  Uri.parse(url.toString());
                                Uri.Builder builder = uri.buildUpon();
                                if (!TextUtils.isEmpty(name)) {
                                    builder = builder.appendQueryParameter(name,
                                            TextUtils.isEmpty(actionValue) ? "1" : actionValue);
                                }
                                if (msg != null) {
                                    builder = builder.appendQueryParameter("seq", "" + msg.seq);
                                }
                                builder = builder.appendQueryParameter("uid", Cache.getTinode().getMyId());
                                mActivity.startActivity(new Intent(Intent.ACTION_VIEW, builder.build()));
                            }
                        } catch(ClassCastException | MalformedURLException | NullPointerException ignored){ }
                    }
                    break;
            }
        }
    }
}
