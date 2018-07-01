package co.tinode.tindroid;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.PorterDuff;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.view.ActionMode;
import android.support.v7.widget.AppCompatImageView;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.util.Base64;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;

import co.tinode.tindroid.db.MessageDb;
import co.tinode.tindroid.db.StoredMessage;
import co.tinode.tindroid.media.SpanFormatter;
import co.tinode.tindroid.media.VxCard;
import co.tinode.tindroid.widgets.LetterTileDrawable;
import co.tinode.tindroid.widgets.RoundImageDrawable;
import co.tinode.tinodesdk.ComTopic;
import co.tinode.tinodesdk.NotConnectedException;
import co.tinode.tinodesdk.PromisedReply;
import co.tinode.tinodesdk.Topic;
import co.tinode.tinodesdk.model.ServerMessage;
import co.tinode.tinodesdk.model.Subscription;

/**
 * Handle display of a conversation
 */
public class MessagesListAdapter extends RecyclerView.Adapter<MessagesListAdapter.ViewHolder> {
    private static final String TAG = "MessagesListAdapter";

    private static final int MESSAGES_TO_LOAD = 20;
    private static final int MESSAGES_QUERY_ID = 100;

    private static final int VIEWTYPE_FULL_LEFT = 0;
    private static final int VIEWTYPE_SIMPLE_LEFT = 1;
    private static final int VIEWTYPE_FULL_AVATAR = 2;
    private static final int VIEWTYPE_SIMPLE_AVATAR = 3;
    private static final int VIEWTYPE_FULL_RIGHT = 4;
    private static final int VIEWTYPE_SIMPLE_RIGHT = 5;
    private static final int VIEWTYPE_FULL_CENTER = 6;

    // Storage Permissions
    private static final int REQUEST_EXTERNAL_STORAGE = 1;
    private static final String[] PERMISSIONS_STORAGE = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    private MessageActivity mActivity;
    private RecyclerView mRecyclerView;

    private Cursor mCursor;
    private String mTopicName;
    private ActionMode.Callback mSelectionModeCallback;
    private ActionMode mSelectionMode;

    private SparseBooleanArray mSelectedItems = null;

    private int mPagesToLoad;
    private MessageLoaderCallbacks mLoaderCallbacks;
    private SwipeRefreshLayout mRefresher;

    public MessagesListAdapter(MessageActivity context, SwipeRefreshLayout refresher) {
        super();
        mActivity = context;
        setHasStableIds(true);
        mLoaderCallbacks = new MessageLoaderCallbacks();
        mRefresher = refresher;
        mPagesToLoad = 1;

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

    @Override
    public void onAttachedToRecyclerView(RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);

        mRecyclerView = recyclerView;
    }

    private int[] getSelectedArray() {
        int[] items = new int[mSelectedItems.size()];
        for (int i = 0; i < items.length; i++) {
            items[i] = mSelectedItems.keyAt(i);
        }
        return items;
    }

    @SuppressWarnings("unchecked")
    private void copyMessageText(int[] positions) {
        StringBuilder sb = new StringBuilder();
        final Topic topic = Cache.getTinode().getTopic(mTopicName);
        if (topic == null) {
            return;
        }

        for (int position : positions) {
            StoredMessage msg = getMessage(position);
            if (msg != null) {
                Subscription<VxCard, ?> sub = (Subscription<VxCard, ?>) topic.getSubscription(msg.from);
                String name = (sub != null && sub.pub != null) ? sub.pub.fn : msg.from;
                sb.append("\n[").append(name).append("]: ").append(msg.content).append("; ")
                        .append(UiUtils.shortDate(msg.ts));
            }
        }

        if (sb.length() > 1) {
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
        final Topic topic = Cache.getTinode().getTopic(mTopicName);

        if (topic != null) {
            int[] list = new int[positions.length];
            int i = 0;
            while (i < positions.length) {
                int pos = positions[i];
                StoredMessage msg = getMessage(pos);
                if (msg != null) {
                    list[i] = msg.seq;
                    i++;
                }
            }

            try {
                topic.delMessages(list, true).thenApply(new PromisedReply.SuccessListener<ServerMessage>() {
                    @Override
                    public PromisedReply<ServerMessage> onSuccess(ServerMessage result) {
                        runLoader();
                        mActivity.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                // Update message list.
                                notifyDataSetChanged();
                                updateSelectionMode();
                            }
                        });
                        return null;
                    }
                }, null);
            } catch (NotConnectedException ignored) {
                Log.d(TAG, "sendDeleteMessages -- NotConnectedException");
            } catch (Exception ignored) {
                Log.d(TAG, "sendDeleteMessages -- Exception", ignored);
                Toast.makeText(mActivity, R.string.failed_to_delete_messages, Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public int getItemViewType(int position) {
        int itemType;
        StoredMessage m = getMessage(position);
        Topic.TopicType tp = Topic.getTopicTypeByName(mTopicName);

        // Logic for less vertical spacing between subsequent messages from the same sender vs different senders;
        // Cursor holds items in reverse order.
        long nextFrom = -2;
        if (position < getItemCount() - 1) {
            nextFrom = getMessage(position + 1).userId;
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

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        // create a new view
        View v;

        int bgColor = 0;
        switch (viewType) {
            case VIEWTYPE_FULL_CENTER:
                v = LayoutInflater.from(parent.getContext()).inflate(R.layout.meta_message, parent, false);
                bgColor = UiUtils.COLOR_META_BUBBLE;
                break;
            case VIEWTYPE_FULL_LEFT:
                v = LayoutInflater.from(parent.getContext()).inflate(R.layout.message_left_single, parent, false);
                bgColor = UiUtils.COLOR_MESSAGE_BUBBLE;
                break;
            case VIEWTYPE_FULL_AVATAR:
                v = LayoutInflater.from(parent.getContext()).inflate(R.layout.message_left_single_avatar, parent, false);
                bgColor = UiUtils.COLOR_MESSAGE_BUBBLE;
                break;
            case VIEWTYPE_FULL_RIGHT:
                v = LayoutInflater.from(parent.getContext()).inflate(R.layout.message_right_single, parent, false);
                break;
            case VIEWTYPE_SIMPLE_LEFT:
                v = LayoutInflater.from(parent.getContext()).inflate(R.layout.message_left, parent, false);
                bgColor = UiUtils.COLOR_MESSAGE_BUBBLE;
                break;
            case VIEWTYPE_SIMPLE_AVATAR:
                v = LayoutInflater.from(parent.getContext()).inflate(R.layout.message_left_avatar, parent, false);
                bgColor = UiUtils.COLOR_MESSAGE_BUBBLE;
                break;
            case VIEWTYPE_SIMPLE_RIGHT:
                v = LayoutInflater.from(parent.getContext()).inflate(R.layout.message_right, parent, false);
                break;
            default:
                return null;
        }

        if (bgColor != 0) {
            v.findViewById(R.id.messageBubble).getBackground().mutate()
                    .setColorFilter(bgColor, PorterDuff.Mode.MULTIPLY);
        }

        return new ViewHolder(v, viewType);
    }

    @SuppressLint("ClickableViewAccessibility")
    @SuppressWarnings("unchecked")
    @Override
    public void onBindViewHolder(final ViewHolder holder, int position) {
        ComTopic<VxCard> topic = (ComTopic<VxCard>) Cache.getTinode().getTopic(mTopicName);
        StoredMessage m = getMessage(position);

        holder.mText.setText(SpanFormatter.toSpanned(mActivity, m.content, holder.mText.getMaxWidth(),
                new SpanFormatter.ClickListener() {
            @Override
            public void onClick(String type, Map<String, Object> data) {
                if (mSelectedItems != null) {
                    int pos = holder.getAdapterPosition();
                    toggleSelectionAt(pos);
                    notifyItemChanged(pos);
                    updateSelectionMode();
                    return;
                }

                switch (type) {
                    case "LN":
                        String url = null;
                        try {
                            if (data != null) {
                                url = (String) data.get("url");
                            }
                        } catch (ClassCastException ignored) {}
                        if (url != null) {
                            try {
                                url = new URL(Cache.getTinode().getBaseUrl(), url).toString();
                                mActivity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                            } catch (MalformedURLException ignored) {}
                        }
                        break;

                    case "IM":
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
                            mActivity.showFragment("view_image", true, args);
                        } else {
                            Toast.makeText(mActivity, R.string.broken_image, Toast.LENGTH_SHORT).show();
                        }

                        break;

                    case "EX":
                        verifyStoragePermissions();

                        String fname = null;
                        String mimeType = null;
                        try {
                            fname = (String) data.get("name");
                            mimeType = (String) data.get("mime");
                        } catch (ClassCastException ignored) {}

                        if (TextUtils.isEmpty(fname)) {
                            fname = mActivity.getString(R.string.default_attachment_name);
                        }

                        // Create file in a downloads directory by default.
                        File file = new File(
                                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fname);
                        Uri fileUri = Uri.fromFile(file);
                        if (TextUtils.isEmpty(mimeType)) {
                            mimeType = UiUtils.getMimeType(fileUri);
                            if (mimeType == null) {
                                mimeType = "*/*";
                            }
                        }

                        try {
                            Object val = data.get("val");
                            if (val != null) {
                                FileOutputStream fos = new FileOutputStream(file);
                                fos.write(val instanceof String ?
                                        Base64.decode((String) val, Base64.DEFAULT) : (byte[]) val);
                                fos.close();
                            } else {
                                Object ref = data.get("ref");
                                if (ref != null && ref instanceof String) {
                                    url = new URL(Cache.getTinode().getBaseUrl(), (String) ref).toString();
                                    // FIXME: use LargeFileHelper.download to fetch the data from URL.
                                }
                            }

                            Intent intent = new Intent();
                            intent.setAction(android.content.Intent.ACTION_VIEW);
                            intent.setDataAndType(fileUri, mimeType);
                            mActivity.startActivity(intent);
                        } catch (NullPointerException | ClassCastException | IOException ex) {
                            Log.e(TAG, "Failed to save attachment to storage", ex);
                            Toast.makeText(mActivity, R.string.failed_to_download, Toast.LENGTH_SHORT).show();
                        } catch (ActivityNotFoundException ex) {
                            Log.i(TAG, "No application can handle downloaded file ", ex);
                            Toast.makeText(mActivity, R.string.failed_to_open_file, Toast.LENGTH_SHORT).show();
                        }
                        break;
                }
            }
        }));
        if (SpanFormatter.hasClickableSpans(m.content)) {
            holder.mText.setLinksClickable(true);
            holder.mText.setFocusable(true);
            holder.mText.setClickable(true);
            holder.mText.setMovementMethod(LinkMovementMethod.getInstance());
        }

        if (holder.mSelected != null) {
            if (mSelectedItems != null && mSelectedItems.get(position)) {
                // Log.d(TAG, "Visible item " + position);
                holder.mSelected.setVisibility(View.VISIBLE);
            } else {
                holder.mSelected.setVisibility(View.GONE);
            }
        }

        if (holder.mAvatar != null || holder.mUserName != null) {
            Subscription<VxCard,?> sub = topic != null ? topic.getSubscription(m.from) : null;
            if (sub != null && sub.pub != null) {
                Bitmap avatar = sub.pub.getBitmap();
                if (holder.mAvatar != null) {
                    if (avatar != null) {
                        holder.mAvatar.setImageDrawable(new RoundImageDrawable(avatar));
                    } else {
                        holder.mAvatar.setImageDrawable(
                                new LetterTileDrawable(mActivity.getResources())
                                        .setLetterAndColor(sub.pub.fn, sub.user)
                                        .setContactTypeAndColor(LetterTileDrawable.TYPE_PERSON));
                    }
                }

                if (holder.mUserName != null) {
                    holder.mUserName.setText(sub.pub.fn);
                }
            }
        }

        if (holder.mMeta != null) {
            holder.mMeta.setText(UiUtils.shortDate(m.ts));
        }

        if (holder.mDeliveredIcon != null) {
            holder.mDeliveredIcon.setImageResource(android.R.color.transparent);
            if (holder.mViewType == VIEWTYPE_FULL_RIGHT || holder.mViewType == VIEWTYPE_SIMPLE_RIGHT) {
                if (m.seq <= 0) {
                    holder.mDeliveredIcon.setImageResource(R.drawable.ic_schedule);
                } else if (topic != null) {
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

        // Log.d(TAG, "Msg[" + position + "] seq=" + m.seq + " at " + m.ts.getTime());
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

    void swapCursor(final String topicName, final Cursor cursor) {
        if (mCursor != null && mCursor == cursor) {
            return;
        }

        // Clear selection
        if (mSelectionMode != null) {
            mSelectionMode.finish();
            mSelectionMode = null;
        }

        mTopicName = topicName;
        Cursor oldCursor = mCursor;
        mCursor = cursor;
        if (oldCursor != null) {
            oldCursor.close();
        }

        // Log.d(TAG, "swapped cursor, topic=" + mTopicName);
    }

    private StoredMessage getMessage(int position) {
        mCursor.moveToPosition(mCursor.getCount() - position - 1);
        return StoredMessage.readMessage(mCursor);
    }

    /**
     * Checks if the app has permission to write to device storage
     *
     * If the app does not has permission then the user will be prompted to grant permission.
     */
    public void verifyStoragePermissions() {
        // Check if we have write permission
        if (!UiUtils.checkPermission(mActivity, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            // We don't have permission so prompt the user
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                mActivity.requestPermissions(PERMISSIONS_STORAGE, REQUEST_EXTERNAL_STORAGE);
            }
        }
    }

    void runLoader() {
        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                final LoaderManager lm = mActivity.getSupportLoaderManager();
                final Loader<Cursor> loader = lm.getLoader(MESSAGES_QUERY_ID);
                if (loader != null && !loader.isReset()) {
                    lm.restartLoader(MESSAGES_QUERY_ID, null, mLoaderCallbacks);
                } else {
                    lm.initLoader(MESSAGES_QUERY_ID, null, mLoaderCallbacks);
                }
            }
        });
    }

    boolean loadNextPage() {
        if (getItemCount() == mPagesToLoad * MESSAGES_TO_LOAD) {
            mPagesToLoad++;
            runLoader();
            return true;
        }

        return false;
    }

    private class MessageLoaderCallbacks implements LoaderManager.LoaderCallbacks<Cursor> {

        @Override
        public Loader<Cursor> onCreateLoader(int id, Bundle args) {
            if (id == MESSAGES_QUERY_ID) {
                return new MessageDb.Loader(mActivity, mTopicName, mPagesToLoad, MESSAGES_TO_LOAD);
            }
            return null;
        }

        @Override
        public void onLoadFinished(Loader<Cursor> loader,
                                   Cursor cursor) {
            if (loader.getId() == MESSAGES_QUERY_ID) {
                swapCursor(mTopicName, cursor);
            }
        }

        @Override
        public void onLoaderReset(Loader<Cursor> loader) {
            if (loader.getId() == MESSAGES_QUERY_ID) {
                swapCursor(null, null);
            }
        }

        private void swapCursor(final String topicName, final Cursor cursor) {
            MessagesListAdapter.this.swapCursor(topicName, cursor);
            mActivity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mRefresher.setRefreshing(false);
                        notifyDataSetChanged();
                        if (cursor != null)
                            mRecyclerView.scrollToPosition(cursor.getCount() - 1);
                    }
                });
        }
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        int mViewType;
        ImageView mAvatar;
        View mMessageBubble;
        AppCompatImageView mDeliveredIcon;
        TextView mText;
        TextView mMeta;
        TextView mUserName;
        View mSelected;
        View mOverlay;

        ViewHolder(View itemView, int viewType) {
            super(itemView);

            mViewType = viewType;
            mAvatar = itemView.findViewById(R.id.avatar);
            mMessageBubble = itemView.findViewById(R.id.messageBubble);
            mDeliveredIcon = itemView.findViewById(R.id.messageViewedIcon);
            mText = itemView.findViewById(R.id.messageText);
            mMeta = itemView.findViewById(R.id.messageMeta);
            mUserName = itemView.findViewById(R.id.userName);
            mSelected = itemView.findViewById(R.id.selected);
            mOverlay = itemView.findViewById(R.id.overlay);
        }
    }
}
