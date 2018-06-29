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
import android.support.v4.app.Fragment;
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
import java.util.Timer;
import java.util.TimerTask;

import co.tinode.tindroid.db.StoredTopic;
import co.tinode.tindroid.media.VxCard;
import co.tinode.tinodesdk.ComTopic;
import co.tinode.tinodesdk.LargeFileHelper;
import co.tinode.tinodesdk.NotConnectedException;
import co.tinode.tinodesdk.PromisedReply;
import co.tinode.tinodesdk.model.Drafty;
import co.tinode.tinodesdk.model.MsgServerCtrl;
import co.tinode.tinodesdk.model.ServerMessage;

import static android.app.Activity.RESULT_OK;

/**
 * Fragment handling message display and message sending.
 */
public class MessagesFragment extends Fragment implements LoaderManager.LoaderCallbacks<MessagesFragment.UploadResult> {
    private static final String TAG = "MessageFragment";

    private static final int MESSAGES_TO_LOAD = 20;

    private static final int ACTION_ATTACH_FILE = 100;
    private static final int ACTION_ATTACH_IMAGE = 101;

    // Maximum size of file to send in-band. 256KB.
    private static final long MAX_INBAND_ATTACHMENT_SIZE = 1 << 17;
    // Maximum size of file to upload. 8MB.
    private static final long MAX_ATTACHMENT_SIZE = 1 << 23;

    private static final int ASYNC_TASK_UPLOADER = 100;

    // Delay before sending out a RECEIVED notification to be sure we are not sending too many.
    // private static final int RECV_DELAY = 500;
    private static final int READ_DELAY = 1000;
    protected ComTopic<VxCard> mTopic;
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

        LinearLayoutManager lm = new LinearLayoutManager(activity);
        lm.setStackFromEnd(true);

        RecyclerView ml = activity.findViewById(R.id.messages_container);
        ml.setLayoutManager(lm);

        mRefresher = activity.findViewById(R.id.swipe_refresher);

        mMessagesAdapter = new MessagesListAdapter(activity, mRefresher);
        ml.setAdapter(mMessagesAdapter);

        mRefresher.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                if (!mMessagesAdapter.loadNextPage() && !StoredTopic.isAllDataLoaded(mTopic)) {
                    // Log.d(TAG, "Calling server for more data");
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

        if (mTopicName != null && !mTopicName.equals(oldTopicName)) {
            mMessagesAdapter.swapCursor(mTopicName, null);
        }
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

        runMessageLoader();
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

    void runMessageLoader() {
        mMessagesAdapter.runLoader();
    }

    private boolean sendMessage(Drafty content) {
        if (mTopic != null) {
            try {
                PromisedReply<ServerMessage> reply = mTopic.publish(content);
                runMessageLoader(); // Shows pending message
                reply.thenApply(new PromisedReply.SuccessListener<ServerMessage>() {
                    @Override
                    public PromisedReply<ServerMessage> onSuccess(ServerMessage result) {
                        // Updates message list with "delivered" icon.
                        runMessageLoader();
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
        if (resultCode == RESULT_OK) {
            switch (requestCode) {
                case ACTION_ATTACH_IMAGE:
                case ACTION_ATTACH_FILE: {
                    Bundle args = new Bundle();
                    args.putParcelable("uri", data.getData());
                    args.putInt("requestCode", requestCode);
                    getActivity().getSupportLoaderManager().initLoader(ASYNC_TASK_UPLOADER, args, this);
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

    @Override
    public Loader<UploadResult> onCreateLoader(int id, Bundle args) {
        return new FileUploader(getActivity(), args, null);
    }

    @Override
    public void onLoadFinished(Loader<UploadResult> loader, UploadResult data) {
        if (data.data != null) {
            sendMessage(data.data);
        } else if (data.error != null) {
            Activity activity = getActivity();
            Toast.makeText(activity, data.error, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onLoaderReset(Loader<UploadResult> loader) {
    }

    private static class FileUploader extends AsyncTaskLoader<UploadResult> {
        private final LargeFileHelper.FileHelperProgress mProgress;
        private final Bundle mArgs;

        FileUploader(Activity activity, Bundle args, LargeFileHelper.FileHelperProgress progress) {
            super(activity);
            mProgress = progress;
            mArgs = args;
        }

        @Override
        public UploadResult loadInBackground() {
            UploadResult result = new UploadResult(null, null);
            final Uri uri = Uri.parse(mArgs.getString("uri"));
            if (uri == null) {
                Log.d(TAG, "Received null URI");
                result.error = "Null URI";
                return result;
            }

            final int requestCode = mArgs.getInt("requestCode");

            String fname;
            Long fsize;
            int imageWidth = 0, imageHeight = 0;

            final Context activity = getContext();
            final ContentResolver resolver = getContext().getContentResolver();

            String mimeType = resolver.getType(uri);
            InputStream is = null;
            ByteArrayOutputStream baos = null;
            try {
                if (mimeType == null) {
                    mimeType = UiUtils.getMimeType(uri);
                    String path = UiUtils.getPath(getContext(), uri);
                    if (path != null) {
                        File file = new File(path);
                        fname = file.getName();
                        fsize = file.length();
                    } else {
                        fname = null;
                        fsize = 0L;
                    }
                } else {
                    Cursor cursor = resolver.query(uri, null, null, null, null);
                    if (cursor != null) {
                        cursor.moveToFirst();
                        fname = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                        fsize = cursor.getLong(cursor.getColumnIndex(OpenableColumns.SIZE));
                        cursor.close();
                    } else {
                        fname = null;
                        fsize = 0L;
                    }
                }

                if (requestCode == ACTION_ATTACH_IMAGE && fsize > MAX_INBAND_ATTACHMENT_SIZE) {
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
                    result.error = activity.getString(R.string.attachment_too_large,
                            UiUtils.bytesToHumanSize(fsize), UiUtils.bytesToHumanSize(MAX_ATTACHMENT_SIZE));
                } else {
                    if (is == null) {
                        is = resolver.openInputStream(uri);
                    }

                    if (requestCode == ACTION_ATTACH_FILE && fsize > MAX_INBAND_ATTACHMENT_SIZE) {
                        // Upload then send message with a link.
                        MsgServerCtrl ctrl = Cache.getTinode().getFileUploader().upload(is, fname, mimeType, fsize,
                                new LargeFileHelper.FileHelperProgress() {
                                    @Override
                                    public void onProgress(long progress, long size) {
                                        if (mProgress != null) {
                                            mProgress.onProgress(progress, size);
                                        }
                                        Log.d(TAG, "Progress: " + progress + ", size=" + size);
                                    }
                                });
                        result.data = draftyAttachment(mimeType, fname, ctrl.getStringParam("url"), fsize);
                    } else {
                        baos = new ByteArrayOutputStream();
                        byte[] buffer = new byte[16384];
                        int len;
                        while ((len = is.read(buffer)) > 0) {
                            baos.write(buffer, 0, len);
                        }

                        byte[] bits = baos.toByteArray();
                        if (requestCode == ACTION_ATTACH_FILE) {
                            result.data = draftyFile(mimeType, bits, fname);
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
                            result.data = draftyImage(mimeType, bits, imageWidth, imageHeight, fname);
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
            return result;
        }
    }

    static class UploadResult {
        Drafty data;
        String error;

        UploadResult(Drafty d, String e) {
            data = d;
            error = e;
        }
    }
}
