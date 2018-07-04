package co.tinode.tindroid;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.AsyncTaskLoader;
import android.support.v4.content.Loader;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;


import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import co.tinode.tindroid.db.BaseDb;
import co.tinode.tindroid.db.StoredTopic;
import co.tinode.tindroid.media.VxCard;

import co.tinode.tinodesdk.ComTopic;
import co.tinode.tinodesdk.LargeFileHelper;
import co.tinode.tinodesdk.NotConnectedException;
import co.tinode.tinodesdk.PromisedReply;
import co.tinode.tinodesdk.Storage;
import co.tinode.tinodesdk.Topic;
import co.tinode.tinodesdk.model.Drafty;
import co.tinode.tinodesdk.model.MsgServerCtrl;
import co.tinode.tinodesdk.model.ServerMessage;

import static android.app.Activity.RESULT_OK;

/**
 * Fragment handling message display and message sending.
 */
public class MessagesFragment extends Fragment
        implements LoaderManager.LoaderCallbacks<MessagesFragment.UploadResult> {
    private static final String TAG = "MessageFragment";

    private static final int MESSAGES_TO_LOAD = 20;

    private static final int ASYNC_TASK_UPLOADER = 101;

    private static final int ACTION_ATTACH_FILE = 100;
    private static final int ACTION_ATTACH_IMAGE = 101;

    // Maximum size of file to send in-band. 256KB.
    private static final long MAX_INBAND_ATTACHMENT_SIZE = 1 << 17;
    // Maximum size of file to upload. 8MB.
    private static final long MAX_ATTACHMENT_SIZE = 1 << 23;

    private static final int READ_DELAY = 1000;
    protected ComTopic<VxCard> mTopic;

    private LinearLayoutManager mMessageViewLayoutManager;
    private MessagesListAdapter mMessagesAdapter;
    private SwipeRefreshLayout mRefresher;

    private String mTopicName = null;
    private Timer mNoteTimer = null;
    private String mMessageToSend = null;

    private PromisedReply.FailureListener<ServerMessage> mFailureListener;

    public MessagesFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_messages, container, false);
    }

    @Override
    public void onActivityCreated(Bundle savedInstance) {
        super.onActivityCreated(savedInstance);

        final MessageActivity activity = (MessageActivity) getActivity();

        mMessageViewLayoutManager = new LinearLayoutManager(activity);
        mMessageViewLayoutManager.setStackFromEnd(true);

        RecyclerView ml = activity.findViewById(R.id.messages_container);
        ml.setLayoutManager(mMessageViewLayoutManager);

        // This needs to be rebound on activity creation.
        FileUploader.setProgressHandler(new UploadProgress());

        mRefresher = activity.findViewById(R.id.swipe_refresher);
        mMessagesAdapter = new MessagesListAdapter(activity, mRefresher);
        ml.setAdapter(mMessagesAdapter);
        mRefresher.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                if (!mMessagesAdapter.loadNextPage() && !StoredTopic.isAllDataLoaded(mTopic)) {
                    try {
                        mTopic.getMeta(mTopic.getMetaGetBuilder().withGetEarlierData(MESSAGES_TO_LOAD).build())
                                .thenApply(
                                        new PromisedReply.SuccessListener<ServerMessage>() {
                                            @Override
                                            public PromisedReply<ServerMessage> onSuccess(ServerMessage result) {
                                                mRefresher.setRefreshing(false);
                                                return null;
                                            }
                                        },
                                        new PromisedReply.FailureListener<ServerMessage>() {
                                            @Override
                                            public PromisedReply<ServerMessage> onFailure(Exception err) {
                                                mRefresher.setRefreshing(false);
                                                return null;
                                            }
                                        }
                                );
                    } catch (Exception e) {
                        mRefresher.setRefreshing(false);
                    }
                } else {
                    mRefresher.setRefreshing(false);
                }
            }
        });

        mFailureListener = new UiUtils.ToastFailureListener(getActivity());

        // Send message on button click
        getActivity().findViewById(R.id.chatSendButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendText();
            }
        });

        // Send image button
        getActivity().findViewById(R.id.attachImage).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openFileSelector("image/*", R.string.select_image, ACTION_ATTACH_IMAGE);
            }
        });

        // Send file button
        getActivity().findViewById(R.id.attachFile).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openFileSelector("*/*", R.string.select_file, ACTION_ATTACH_FILE);
            }
        });

        EditText editor = activity.findViewById(R.id.editMessage);
        // Send message on Enter
        editor.setOnEditorActionListener(
                new TextView.OnEditorActionListener() {
                    @Override
                    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                        sendText();
                        return true;
                    }
                });

        // Send notification on key presses
        editor.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @SuppressWarnings("unchecked")
            @Override
            public void onTextChanged(CharSequence charSequence, int start, int before, int count) {
                if (count > 0 || before > 0) {
                    activity.sendKeyPress();
                }
            }

            @Override
            public void afterTextChanged(Editable editable) {
            }
        });
    }

    @Override
    @SuppressWarnings("unchecked")
    public void onResume() {
        super.onResume();

        Bundle bundle = getArguments();
        String oldTopicName = mTopicName;
        mTopicName = bundle.getString("topic");
        mMessageToSend = bundle.getString("messageText");

        mTopic = (ComTopic<VxCard>) Cache.getTinode().getTopic(mTopicName);

        setHasOptionsMenu(true);

        // Check periodically if all messages were read;
        mNoteTimer = new Timer();
        mNoteTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                sendReadNotification();
            }
        }, READ_DELAY, READ_DELAY);

        mRefresher.setRefreshing(false);

        if (mTopicName != null) {
            mMessagesAdapter.swapCursor(mTopicName, null,  !mTopicName.equals(oldTopicName));
            mMessagesAdapter.runLoader();
        }
    }

    void runMessagesLoader() {
        mMessagesAdapter.runLoader();
    }

    @Override
    public void onPause() {
        super.onPause();

        // Stop reporting read messages
        mNoteTimer.cancel();
        mNoteTimer = null;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        // Inflate the menu; this adds items to the action bar if it is present.
        inflater.inflate(R.menu.menu_topic, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        switch (id) {
            case R.id.action_clear: {
                // TODO: implement Topic.deleteMessages
                return true;
            }
            case R.id.action_mute: {
                // TODO: implement setting notifications to off
                return true;
            }
            case R.id.action_delete: {
                showDeleteTopicConfirmationDialog();
                return true;
            }
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    // Confirmation dialog "Do you really want to do X?"
    private void showDeleteTopicConfirmationDialog() {
        final Activity activity = getActivity();
        if (activity == null) {
            return;
        }
        final AlertDialog.Builder confirmBuilder = new AlertDialog.Builder(activity);
        confirmBuilder.setNegativeButton(android.R.string.cancel, null);
        confirmBuilder.setMessage(R.string.confirm_delete_topic);

        confirmBuilder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                try {
                    mTopic.delete().thenApply(new PromisedReply.SuccessListener<ServerMessage>() {
                        @Override
                        public PromisedReply<ServerMessage> onSuccess(ServerMessage result) {
                            Intent intent = new Intent(getActivity(), ContactsActivity.class);
                            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                            startActivity(intent);
                            getActivity().finish();
                            return null;
                        }
                    }, mFailureListener);
                } catch (NotConnectedException ignored) {
                    Toast.makeText(activity, R.string.no_connection, Toast.LENGTH_SHORT).show();
                } catch (Exception ignored) {
                    Toast.makeText(activity, R.string.action_failed, Toast.LENGTH_SHORT).show();
                }
            }
        });
        confirmBuilder.show();
    }

    public void notifyDataSetChanged() {
        mMessagesAdapter.notifyDataSetChanged();
    }

    void openFileSelector(String mimeType, int title, int resultCode) {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType(mimeType);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(
                    Intent.createChooser(intent, getActivity().getString(title)), resultCode);
        } catch (ActivityNotFoundException ex) {
            Toast.makeText(getActivity(), R.string.file_manager_not_found, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.d(TAG, "onActivityResult, resultCode="+resultCode + ", requestCode=" + requestCode);
        if (resultCode == RESULT_OK) {
            switch (requestCode) {
                case ACTION_ATTACH_IMAGE:
                case ACTION_ATTACH_FILE: {
                    final Bundle args = new Bundle();
                    args.putParcelable("uri", data.getData());
                    args.putInt("requestCode", requestCode);
                    args.putString("topic", mTopicName);
                    final FragmentActivity activity = getActivity();
                    if (activity == null) {
                        return;
                    }

                    final LoaderManager lm = activity.getSupportLoaderManager();
                    final Loader<UploadResult> loader = lm.getLoader(ASYNC_TASK_UPLOADER);
                    if (loader != null && !loader.isReset()) {
                        lm.restartLoader(ASYNC_TASK_UPLOADER, args, this);
                    } else {
                        lm.initLoader(ASYNC_TASK_UPLOADER, args, this);
                    }
                    break;
                }
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    public void sendText() {
        final Activity activity = getActivity();
        final TextView inputField = activity.findViewById(R.id.editMessage);
        String message = inputField.getText().toString().trim();
        // notifyDataSetChanged();
        if (!message.equals("")) {
            if (sendMessage(Drafty.parse(message))) {
                // Message is successfully queued, clear text from the input field and redraw the list.
                inputField.setText("");
            }
        }
    }

    private boolean sendMessage(Drafty content) {
        if (mTopic != null) {
            try {
                PromisedReply<ServerMessage> reply = mTopic.publish(content);
                mMessagesAdapter.runLoader(); // Shows pending message
                reply.thenApply(new PromisedReply.SuccessListener<ServerMessage>() {
                    @Override
                    public PromisedReply<ServerMessage> onSuccess(ServerMessage result) {
                        // Updates message list with "delivered" icon.
                        mMessagesAdapter.runLoader();
                        return null;
                    }
                }, mFailureListener);
            } catch (NotConnectedException ignored) {
                Log.d(TAG, "sendMessage -- NotConnectedException", ignored);
            } catch (Exception ignored) {
                Log.d(TAG, "sendMessage -- Exception", ignored);
                Toast.makeText(getActivity(), R.string.failed_to_send_message, Toast.LENGTH_SHORT).show();
                return false;
            }
            return true;
        }
        return false;
    }

    // Send image in-band
    public static Drafty draftyImage(String mimeType, byte[] bits, int width, int height, String fname) {
        Drafty content = Drafty.parse(" ");
        content.insertImage(0, mimeType, bits, width, height, fname);
        return content;
    }

    // Send file in-band
    public static Drafty draftyFile(String mimeType, byte[] bits, String fname) {
        Drafty content = new Drafty();
        content.attachFile(mimeType, bits, fname);
        return content;
    }

    // Send file as a link.
    public static Drafty draftyAttachment(String mimeType, String fname, String refUrl, long size) {
        Log.d(TAG, "draftyAttachment " + refUrl);
        Drafty content = new Drafty();
        content.attachFile(mimeType, fname, refUrl, size);
        return content;
    }


    public void sendReadNotification() {
        if (mTopic != null) {
            mTopic.noteRead();
        }
    }

    public void topicSubscribed() {
        Activity activity = getActivity();
        if (activity == null) {
            return;
        }

        if (mTopic.getAccessMode().isWriter()) {
            Log.i(TAG, "Topic is Writer " + mTopic.getName());
            ((TextView) activity.findViewById(R.id.editMessage)).setText(TextUtils.isEmpty(mMessageToSend) ? "" : mMessageToSend);
            activity.findViewById(R.id.sendMessagePanel).setVisibility(View.VISIBLE);
            activity.findViewById(R.id.sendMessageDisabled).setVisibility(View.GONE);
            mMessageToSend = null;
        } else {
            Log.i(TAG, "Topic is NOT writer " + mTopic.getName());
            activity.findViewById(R.id.sendMessagePanel).setVisibility(View.GONE);
            activity.findViewById(R.id.sendMessageDisabled).setVisibility(View.VISIBLE);
        }
    }

    @NonNull
    @Override
    public Loader<UploadResult> onCreateLoader(int id, Bundle args) {
        return new FileUploader(getActivity(), args);
    }

    @Override
    public void onLoadFinished(@NonNull Loader<UploadResult> loader, UploadResult data) {
        final Activity activity = getActivity();

        Log.d(TAG, "onLoadFinished: data.msgId=" + data.msgId + ", data.error=" + data.error);

        if (data.msgId > 0) {
            try {
                mTopic.syncPending();
            } catch (Exception ex) {
                Log.d(TAG, "Failed to sync", ex);
                Toast.makeText(activity, R.string.failed_to_send_message, Toast.LENGTH_LONG).show();
            }
        } else if (data.error != null) {
            Toast.makeText(activity, data.error, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onLoaderReset(@NonNull Loader<UploadResult> loader) {
    }

    private static class FileUploader extends AsyncTaskLoader<UploadResult> {
        private static WeakReference<UploadProgress> sProgress;
        private final Bundle mArgs;

        FileUploader(Activity activity, Bundle args) {
            super(activity);
            mArgs = args;
        }

        static void setProgressHandler(UploadProgress progress) {
            sProgress = new WeakReference<>(progress);
        }

        @Override
        public void onStartLoading() {
        }

        @Override
        public UploadResult loadInBackground() {
            final UploadResult result = new UploadResult();
            Storage store = BaseDb.getInstance().getStore();
            final Uri uri = mArgs.getParcelable("uri");
            if (uri == null) {
                Log.d(TAG, "Received null URI");
                result.error = "Null URI";
                return result;
            }

            final int requestCode = mArgs.getInt("requestCode");
            final String topicName = mArgs.getString("topic");

            Log.d(TAG, "loadInBackground topic=" + topicName + ", uri=" + uri);
            final Context activity = getContext();
            final ContentResolver resolver = getContext().getContentResolver();
            final Topic topic = Cache.getTinode().getTopic(topicName);

            Drafty content = null;
            InputStream is = null;
            ByteArrayOutputStream baos = null;
            try {
                String fname = null;
                Long fsize = 0L;
                int imageWidth = 0, imageHeight = 0;

                String mimeType = resolver.getType(uri);
                if (mimeType == null) {
                    mimeType = UiUtils.getMimeType(uri);
                }

                Cursor cursor = resolver.query(uri, null, null, null, null);
                if (cursor != null) {
                    cursor.moveToFirst();
                    fname = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                    fsize = cursor.getLong(cursor.getColumnIndex(OpenableColumns.SIZE));
                    Log.d(TAG, "Got file data from cursor: fname=" + fname + ", size=" + fsize);
                    cursor.close();
                }

                // Still no size? Try opening directly.
                if (fsize == 0) {
                    String path = UiUtils.getPath(getContext(), uri);
                    if (path != null) {
                        File file = new File(path);
                        if (fname == null) {
                            fname = file.getName();
                        }
                        fsize = file.length();
                    }
                }

                if (fsize == 0) {
                    Log.d(TAG, "File size is zero "+uri);
                    result.error = activity.getString(R.string.invalid_file);
                    return result;
                }

                if (fname == null) {
                    fname = activity.getString(R.string.default_attachment_name);
                }

                if (requestCode == ACTION_ATTACH_IMAGE && fsize > MAX_INBAND_ATTACHMENT_SIZE) {
                    Log.d(TAG, "Attaching large image size="+fsize);
                    is = resolver.openInputStream(uri);
                    // Resize image to ensure it's under the maximum in-band size.
                    Bitmap bmp = BitmapFactory.decodeStream(is, null, null);
                    bmp = UiUtils.scaleBitmap(bmp);
                    imageWidth = bmp.getWidth();
                    imageHeight = bmp.getHeight();
                    is.close();

                    is = UiUtils.bitmapToStream(bmp, mimeType);
                    fsize = (long) is.available();
                }

                if (fsize > MAX_ATTACHMENT_SIZE) {
                    Log.d(TAG, "File is too big, size="+fsize);
                    result.error = activity.getString(R.string.attachment_too_large,
                            UiUtils.bytesToHumanSize(fsize), UiUtils.bytesToHumanSize(MAX_ATTACHMENT_SIZE));
                } else {
                    if (is == null) {
                        is = resolver.openInputStream(uri);
                    }

                    if (requestCode == ACTION_ATTACH_FILE && fsize > MAX_INBAND_ATTACHMENT_SIZE) {
                        Log.d(TAG, "File is medium size, sending as attachment, size="+fsize);

                        // Create message draft.
                        result.msgId = store.msgDraft(topic, draftyAttachment(mimeType, fname, uri.toString(), -1));

                        // Upload then send message with a link. This is a long-running blocking call.
                        MsgServerCtrl ctrl = Cache.getTinode().getFileUploader().upload(is, fname, mimeType, fsize,
                                new LargeFileHelper.FileHelperProgress() {
                                    @Override
                                    public void onProgress(long progress, long size) {
                                        UploadProgress p = sProgress.get();
                                        if (p != null) {
                                            p.onProgress(result.msgId, progress, size);
                                        }
                                        Log.d(TAG, "Progress: " + progress + ", size=" + size);
                                    }
                                });
                        content = draftyAttachment(mimeType, fname, ctrl.getStringParam("url"), fsize);
                    } else {
                        Log.d(TAG, "Attaching image or small file inline, size="+fsize);

                        baos = new ByteArrayOutputStream();
                        byte[] buffer = new byte[16384];
                        int len;
                        while ((len = is.read(buffer)) > 0) {
                            baos.write(buffer, 0, len);
                        }

                        byte[] bits = baos.toByteArray();
                        if (requestCode == ACTION_ATTACH_FILE) {
                            result.msgId = store.msgDraft(topic, draftyFile(mimeType, bits, fname));
                        } else {
                            if (imageWidth == 0) {
                                BitmapFactory.Options options = new BitmapFactory.Options();
                                options.inJustDecodeBounds = true;
                                InputStream bais = new ByteArrayInputStream(bits);
                                BitmapFactory.decodeStream(bais, null, options);
                                bais.close();

                                imageWidth = options.outWidth;
                                imageHeight = options.outHeight;
                            }
                            result.msgId = store.msgDraft(topic, draftyImage(mimeType, bits, imageWidth, imageHeight, fname));
                        }
                    }
                }
            } catch (IOException ex) {
                Log.e(TAG, "Failed to attach file", ex);
                result.error = ex.getMessage();
            } finally {
                if (is != null) {
                    try {
                        is.close();
                    } catch (IOException ignored) {}
                }
                if (baos != null) {
                    try {
                        baos.close();
                    } catch (IOException ignored) {}
                }
            }

            if (result.msgId > 0) {
                if (content != null) {
                    // Success: mark message as ready for delivery. It's OK for content to be null.
                    store.msgReady(topic, result.msgId, content);
                } else {
                    // Failure: discard draft.
                    store.msgDiscard(topic, result.msgId);
                    result.msgId = -1;
                }
            }

            return result;
        }
    }

    static class UploadResult {
        String error;
        long msgId = -1;

        UploadResult() {
        }
    }

    private class UploadProgress {

        UploadProgress() {
        }

        void onProgress(final long msgId, final long progress, final long total) {
            int first = mMessageViewLayoutManager.findFirstVisibleItemPosition();
            int last = mMessageViewLayoutManager.findLastVisibleItemPosition();
            for (int i=first; i<=last; i++) {
                if (mMessagesAdapter.getItemId(i) == msgId) {
                    Activity activity = getActivity();
                    if (activity == null) {
                        break;
                    }
                    final int position = i;
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Float ratio = total > 0 ? (float) progress/ total : (float) progress;
                            mMessagesAdapter.notifyItemChanged(position, ratio);
                        }
                    });
                    break;
                }
            }
        }
    }
}
