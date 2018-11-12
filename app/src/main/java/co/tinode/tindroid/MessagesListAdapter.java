package co.tinode.tindroid;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.DownloadManager;
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
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.FileProvider;
import android.support.v4.content.Loader;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.view.ActionMode;
import android.support.v7.widget.AppCompatImageButton;
import android.support.v7.widget.AppCompatImageView;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
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
import co.tinode.tinodesdk.NotConnectedException;
import co.tinode.tinodesdk.PromisedReply;
import co.tinode.tinodesdk.Storage;
import co.tinode.tinodesdk.Topic;
import co.tinode.tinodesdk.model.Drafty;
import co.tinode.tinodesdk.model.ServerMessage;
import co.tinode.tinodesdk.model.Subscription;

/**
 * Handle display of a conversation
 */
public class MessagesListAdapter
        extends RecyclerView.Adapter<MessagesListAdapter.ViewHolder>
        implements LoaderManager.LoaderCallbacks<Cursor> {
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
    private String mTopicName = null;
    private ActionMode.Callback mSelectionModeCallback;
    private ActionMode mSelectionMode;

    private SparseBooleanArray mSelectedItems = null;

    private int mPagesToLoad;
    private SwipeRefreshLayout mRefresher;

    // This is a map of message IDs to their corresponding loader IDs.
    // This is needed for upload cancellations.
    private LongSparseArray<Integer> mLoaders = null;

    private SpanClicker mSpanFormatterClicker;

    public MessagesListAdapter(MessageActivity context, SwipeRefreshLayout refresher) {
        super();

        mActivity = context;
        setHasStableIds(true);

        mRefresher = refresher;
        mPagesToLoad = 1;

        mLoaders = new LongSparseArray<>();

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

        mSpanFormatterClicker = new SpanClicker();

        verifyStoragePermissions();
    }

    @Override
    public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
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
        final Storage store = BaseDb.getInstance().getStore();
        if (topic != null) {
            ArrayList<Integer> toDelete = new ArrayList<>();
            int i = 0;
            int discarded = 0;
            while (i < positions.length) {
                int pos = positions[i++];
                StoredMessage msg = getMessage(pos);
                if (msg != null) {
                    if (msg.status == BaseDb.STATUS_SYNCED) {
                        toDelete.add(msg.seq);
                    } else {
                        store.msgDiscard(topic, msg.getId());
                        discarded ++;
                    }
                }
            }

            if (!toDelete.isEmpty()) {
                try {
                    topic.delMessages(toDelete, true).thenApply(new PromisedReply.SuccessListener<ServerMessage>() {
                        @Override
                        public PromisedReply<ServerMessage> onSuccess(ServerMessage result) {
                            runLoader();
                            mActivity.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
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
            } else if (discarded > 0) {
                runLoader();
                updateSelectionMode();
            }
        }
    }

    @Override
    public int getItemViewType(int position) {
        int itemType;
        StoredMessage m = getMessage(position);
        Topic.TopicType tp = Topic.getTopicTypeByName(mTopicName);

        // Logic for less vertical spacing between subsequent messages from the same sender vs different senders.
        // Zero item position is on the bottom of the screen.
        long nextFrom = -2;
        if (position > 0) {
            nextFrom = getMessage(position - 1).userId;
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
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
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
    @Override
    public void onBindViewHolder(@NonNull final ViewHolder holder, int position, List<Object> payload) {
        if (payload != null && !payload.isEmpty()) {
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

        ComTopic<VxCard> topic = (ComTopic<VxCard>) Cache.getTinode().getTopic(mTopicName);

        final StoredMessage m = getMessage(position);

        // Disable attachment clicker.
        boolean disableEnt = (m.status == BaseDb.STATUS_QUEUED || m.status == BaseDb.STATUS_DRAFT) &&
                (m.content.getEntReferences() != null);

        mSpanFormatterClicker.setPosition(position);
        holder.mText.setText(SpanFormatter.toSpanned(holder.mText, m.content,
                disableEnt ? null : mSpanFormatterClicker));
        if (SpanFormatter.hasClickableSpans(m.content)) {
            holder.mText.setLinksClickable(true);
            holder.mText.setFocusable(true);
            holder.mText.setClickable(true);
            holder.mText.setMovementMethod(LinkMovementMethod.getInstance());
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
            Subscription<VxCard,?> sub = topic != null ? topic.getSubscription(m.from) : null;
            if (sub != null && sub.pub != null) {
                Bitmap avatar = sub.pub.getBitmap();
                if (holder.mAvatar != null) {
                    if (avatar != null) {
                        holder.mAvatar.setImageDrawable(new RoundImageDrawable(avatar));
                    } else {
                        holder.mAvatar.setImageDrawable(
                                new LetterTileDrawable(mActivity)
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
                if (m.status <= BaseDb.STATUS_QUEUED) {
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
    }

    // Must match position-to-item of getItemId.
    StoredMessage getMessage(int position) {
        if (mCursor != null) {
            if (mCursor.moveToPosition(position)) {
                return StoredMessage.readMessage(mCursor);
            }
        }
        return null;
    }

    @Override
    // Must match position-to-item of getMessage.
    public long getItemId(int position) {
        if (mCursor != null && !mCursor.isClosed()) {
            if (mCursor.moveToPosition(position)) {
                return MessageDb.getLocalId(mCursor);
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

    void swapCursor(final String topicName, final Cursor cursor, boolean refresh) {
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
        if (refresh) {
            mActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mRefresher.setRefreshing(false);
                    notifyDataSetChanged();
                    if (cursor != null)
                        mRecyclerView.scrollToPosition(0);
                }
            });
        }
    }

    /**
     * Checks if the app has permission to write to device storage
     *
     * If the app does not has permission then the user will be prompted to grant permission.
     */
    private void verifyStoragePermissions() {
        // Check if we have write permission
        if (!UiUtils.checkPermission(mActivity, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            // We don't have permission so prompt the user
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                ActivityCompat.requestPermissions(mActivity, PERMISSIONS_STORAGE, REQUEST_EXTERNAL_STORAGE);
            }
        }
    }

    // Run loader on UI thread
    void runLoader() {
        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                final LoaderManager lm = mActivity.getSupportLoaderManager();
                final Loader<Cursor> loader = lm.getLoader(MESSAGES_QUERY_ID);
                if (loader != null && !loader.isReset()) {
                    lm.restartLoader(MESSAGES_QUERY_ID, null, MessagesListAdapter.this);
                } else {
                    lm.initLoader(MESSAGES_QUERY_ID, null, MessagesListAdapter.this);
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

    @NonNull
    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        if (id == MESSAGES_QUERY_ID) {
            return new MessageDb.Loader(mActivity, mTopicName, mPagesToLoad, MESSAGES_TO_LOAD);
        }

        throw new  IllegalArgumentException("Unknown loader id " + id);
    }

    @Override
    public void onLoadFinished(@NonNull Loader<Cursor> loader,
                               Cursor cursor) {
        switch (loader.getId()) {
            case MESSAGES_QUERY_ID:
                swapCursor(mTopicName, cursor, true);
                break;
        }
    }

    @Override
    public void onLoaderReset(@NonNull Loader<Cursor> loader) {
        switch (loader.getId()) {
            case MESSAGES_QUERY_ID:
                swapCursor(null, null, true);
                break;
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
        View mProgressInclude;
        ProgressBar mProgressBar;
        AppCompatImageButton mCancelProgress;
        View mProgress;
        View mProgressResult;

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
            mActivity.getSupportLoaderManager().destroyLoader(loaderId);
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
                Log.d(TAG, "External storage not mounted: " + path);

            } else if (!(path.mkdirs() || path.isDirectory())) {
                Log.d(TAG, "Path is not a directory - " + path);
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
                    LargeFileHelper lfh = Cache.getTinode().getFileUploader();
                    mActivity.startDownload(Uri.parse(new URL(Cache.getTinode().getBaseUrl(), (String) ref).toString()),
                            fname, mimeType, lfh.headers());
                } else {
                    Log.e(TAG, "Invalid or missing attachment");
                    Toast.makeText(mActivity, R.string.failed_to_download, Toast.LENGTH_SHORT).show();
                }
            }

        } catch (NullPointerException | ClassCastException | IOException ex) {
            Log.d(TAG, "Failed to save attachment to storage", ex);
            Toast.makeText(mActivity, R.string.failed_to_download, Toast.LENGTH_SHORT).show();
        } catch (ActivityNotFoundException ex) {
            Log.i(TAG, "No application can handle downloaded file");
            Toast.makeText(mActivity, R.string.failed_to_open_file, Toast.LENGTH_SHORT).show();
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (Exception ignored) {}
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
                            String url = new URL(Cache.getTinode().getBaseUrl(), (String) data.get("url")).toString();
                            mActivity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
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
                        mActivity.showFragment("view_image", true, args);
                    } else {
                        Toast.makeText(mActivity, R.string.broken_image, Toast.LENGTH_SHORT).show();
                    }

                    break;

                case "EX":
                    // Attachment
                    verifyStoragePermissions();

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
                                String url = new URL(Cache.getTinode().getBaseUrl(), (String) data.get("ref")).toString();
                                Uri uri =  Uri.parse(url);
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
