package co.tinode.tindroid;

import android.app.NotificationManager;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.content.ClipboardManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import co.tinode.tindroid.account.Utils;
import co.tinode.tindroid.db.MessageDb;
import co.tinode.tindroid.db.StoredMessage;
import co.tinode.tinodesdk.NotConnectedException;
import co.tinode.tinodesdk.PromisedReply;
import co.tinode.tinodesdk.Tinode;
import co.tinode.tinodesdk.Topic;
import co.tinode.tinodesdk.model.Description;
import co.tinode.tinodesdk.model.MsgServerData;
import co.tinode.tinodesdk.model.MsgServerInfo;
import co.tinode.tinodesdk.model.MsgServerPres;
import co.tinode.tinodesdk.model.ServerMessage;
import co.tinode.tinodesdk.model.Subscription;

/**
 * View to display a single conversation
 */
public class MessageActivity extends AppCompatActivity {

    private static final String TAG = "MessageActivity";

    private static final int MESSAGES_QUERY_ID = 100;

    private static final String FRAGMENT_MESSAGES = "msg";
    private static final String FRAGMENT_INFO = "info";

    private LoaderManager mLoaderManager;
    private MessageLoaderCallbacks mLoaderCallbacks;
    private String mMessageText = null;

    private String mTopicName;
    protected Topic<VCard, String, String> mTopic;

    private MessagesFragment mMsgFragment = null;
    private TopicInfoFragment mInfoFragment = null;

    public static void runLoader(final Bundle args, final LoaderManager.LoaderCallbacks<Cursor> callbacks,
                                  final LoaderManager loaderManager) {
        final Loader<Cursor> loader = loaderManager.getLoader(MESSAGES_QUERY_ID);
        if (loader != null && !loader.isReset()) {
            loaderManager.restartLoader(MESSAGES_QUERY_ID, args, callbacks);
        } else {
            loaderManager.initLoader(MESSAGES_QUERY_ID, args, callbacks);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_messages);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mInfoFragment != null && mInfoFragment.isVisible()) {
                    showMsgFragment();
                } else {
                    Intent intent = new Intent(MessageActivity.this, ContactsActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                    startActivity(intent);
                }
            }
        });

        showMsgFragment();

        mLoaderManager = getSupportLoaderManager();
        mLoaderCallbacks = new MessageLoaderCallbacks();
    }

    @Override
    public void onResume() {
        super.onResume();

        final Tinode tinode = Cache.getTinode();
        tinode.setListener(new UiUtils.EventListener(this, tinode.isConnected()));

        final Intent intent = getIntent();

        // Check if the activity was launched by internally-generated intent.
        mTopicName = intent.getStringExtra("topic");

        if (TextUtils.isEmpty(mTopicName)) {
            // mTopicName is empty, so this is an external intent
            Uri contactUri = intent.getData();
            if (contactUri != null) {
                Cursor cursor = getContentResolver().query(contactUri,
                        new String[]{Utils.DATA_PID}, null, null, null);
                if (cursor != null) {
                    if (cursor.moveToFirst()) {
                        mTopicName = cursor.getString(cursor.getColumnIndex(Utils.DATA_PID));
                    }
                    cursor.close();
                }
            }
        }

        if (TextUtils.isEmpty(mTopicName)) {
            Log.e(TAG, "Activity started with an empty topic name");
            finish();
            return;
        }

        // Cancel all pending notifications addressed to the current topic
        ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).cancel(mTopicName, 0);

        mMessageText = intent.getStringExtra(Intent.EXTRA_TEXT);

        // Get a known topic.
        mTopic = tinode.getTopic(mTopicName);
        if (mTopic != null) {
            mTopic.setListener(new TListener());

            UiUtils.setupToolbar(this, mTopic.getPub(), mTopic.getTopicType(), mTopic.getOnline());
            runLoader(null, mLoaderCallbacks, mLoaderManager);

            if (!mTopic.isAttached()) {
                try {
                    mTopic.subscribe(null,
                            mTopic.subscribeParamGetBuilder()
                                    .withGetDesc()
                                    .withGetSub()
                                    .withGetData()
                                    .build());
                } catch (NotConnectedException ignored) {
                    Log.d(TAG, "Offline mode");
                } catch (Exception ex) {
                    Log.e(TAG, "something went wrong", ex);
                }
            }
        } else {
            Log.d(TAG, "Attempt to instantiate an unknown topic: " + mTopicName);
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        Cache.getTinode().setListener(null);
        if (mTopic != null) {
            mTopic.setListener(null);

            // Deactivate current topic
            if (mTopic.isAttached()) {
                try {
                    mTopic.leave();
                } catch (Exception ex) {
                    Log.e(TAG, "something went wrong in Topic.leave", ex);
                }
            }
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        setIntent(intent);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        UiUtils.setVisibleTopic(hasFocus ? mTopicName : null);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        switch (id) {
            case R.id.action_attach: {
                // TODO: implement
                return true;
            }
            case R.id.action_view_contact: {
                showInfoFragment();
                return true;
            }
            case R.id.action_delete: {
                // TODO: implement
                return true;
            }
            case R.id.action_mute: {
                // TODO: implement
                return true;
            }
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /** Display chat */
    private void showMsgFragment() {
        if (mMsgFragment == null) {
            mMsgFragment = new MessagesFragment();
        }
        FragmentTransaction trx = getSupportFragmentManager().beginTransaction();
        trx.replace(R.id.contentFragment, mMsgFragment, FRAGMENT_MESSAGES);
        trx.commit();
    }

    /** Display topic info form */
    private void showInfoFragment() {
        if (mInfoFragment == null) {
            mInfoFragment = new TopicInfoFragment();
        }
        Bundle args = new Bundle();
        args.putString("topic", mTopicName);
        mInfoFragment.setArguments(args);
        FragmentTransaction trx = getSupportFragmentManager().beginTransaction();
        trx.replace(R.id.contentFragment, mInfoFragment, FRAGMENT_INFO);
        trx.commit();
    }

    public String getMessageText() {
        return mMessageText;
    }

    public void sendReadNotification() {
        if (mTopic != null) {
            mTopic.noteRead();
        }
    }

    public void sendKeyPress() {
        if (mTopic != null) {
            mTopic.noteKeyPress();
        }
    }

    public void sendMessage() {
        Log.d(TAG, "sendMessage");
        if (mTopic != null) {
            final TextView inputField = (TextView) findViewById(R.id.editMessage);
            String message = inputField.getText().toString().trim();
            mMsgFragment.notifyDataSetChanged();
            if (!message.equals("")) {
                try {
                    Log.d(TAG, "sendMessage -- sending...");
                    mTopic.publish(message).thenApply(new PromisedReply.SuccessListener<ServerMessage>() {
                        @Override
                        public PromisedReply<ServerMessage> onSuccess(ServerMessage result) throws Exception {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    // Update message list.
                                    mMsgFragment.notifyDataSetChanged();
                                    Log.d(TAG, "sendMessage -- {ctrl} received");
                                }
                            });
                            return null;
                        }
                    }, null);
                } catch (NotConnectedException ignored) {
                    Log.d(TAG, "sendMessage -- NotConnectedException");
                } catch (Exception ignored) {
                    Log.d(TAG, "sendMessage -- Exception");
                    Toast.makeText(this, R.string.failed_to_send_message, Toast.LENGTH_SHORT).show();
                    return;
                }

                // Message is successfully queued, clear text from the input field and redraw the list.
                Log.d(TAG, "sendMessage -- clearing text and notifying");
                inputField.setText("");
                runLoader(null, mLoaderCallbacks, mLoaderManager);
            }
        }
    }

    public void sendDeleteMessages(final int[] positions) {
        if (mTopic != null) {
            int[] list = new int[positions.length];
            int i = 0;
            while (i < positions.length) {
                int pos = positions[i];
                StoredMessage<String> msg = mMsgFragment.getMessage(pos);
                if (msg != null) {
                    list[i] = msg.seq;
                    i++;
                }
            }

            try {
                mTopic.delMessages(list, true).thenApply(new PromisedReply.SuccessListener<ServerMessage>() {
                    @Override
                    public PromisedReply<ServerMessage> onSuccess(ServerMessage result) throws Exception {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                // Update message list.
                                runLoader(null, mLoaderCallbacks, mLoaderManager);
                                Log.d(TAG, "sendDeleteMessages -- {ctrl} received");
                            }
                        });
                        return null;
                    }
                }, null);
            } catch (NotConnectedException ignored) {
                Log.d(TAG, "sendDeleteMessages -- NotConnectedException");
            } catch (Exception ignored) {
                Log.d(TAG, "sendDeleteMessages -- Exception", ignored);
                Toast.makeText(this, R.string.failed_to_delete_messages, Toast.LENGTH_SHORT).show();
            }
        }
    }

    void copyMessageText(int[] positions) {
        StringBuilder sb = new StringBuilder();
        for (int position : positions) {
            StoredMessage<String> msg = mMsgFragment.getMessage(position);
            if (msg != null) {
                sb.append("\n").append(formatMessageText(msg));
            }
        }

        if (sb.length() > 1) {
            sb.deleteCharAt(0);
            String text = sb.toString();

            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText("message text", text));
        }
    }

    private String formatMessageText(StoredMessage<String> msg) {
        Subscription<VCard, ?> sub = mTopic.getSubscription(msg.from);
        String name = (sub != null && sub.pub != null) ? sub.pub.fn : msg.from;
        return "[" + name + "]: " + msg.content + "; " + UiUtils.shortDate(msg.ts);
    }

    private class TListener extends Topic.Listener<VCard, String, String> {

        TListener() {}

        @Override
        public void onSubscribe(int code, String text) {
            // Topic name may change after subscription, i.e. new -> grpXXX
            mTopicName = mTopic.getName();
            runLoader(null, mLoaderCallbacks, mLoaderManager);
        }

        @Override
        public void onData(MsgServerData data) {
            runLoader(null, mLoaderCallbacks, mLoaderManager);
        }

        @Override
        public void onPres(MsgServerPres pres) {
            Log.d(TAG, "Topic '" + mTopicName + "' onPres what='" + pres.what + "'");
        }

        @Override
        public void onInfo(MsgServerInfo info) {
            switch (info.what) {
                case "read":
                case "recv":
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mMsgFragment.notifyDataSetChanged();
                        }
                    });
                    break;
                case "kp":
                    // TODO(gene): show typing notification
                    Log.d(TAG, info.from + ": typing...");
                    break;
                default:
                    break;
            }
        }

        @Override
        public void onMetaDesc(final Description<VCard, String> desc) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    UiUtils.setupToolbar(MessageActivity.this, mTopic.getPub(), mTopic.getTopicType(),
                            mTopic.getOnline());
                }
            });
        }

        @Override
        public void onOnline(final boolean online) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    UiUtils.setupToolbar(MessageActivity.this, mTopic.getPub(), mTopic.getTopicType(),
                            mTopic.getOnline());
                }
            });

        }
    }

    private class MessageLoaderCallbacks implements LoaderManager.LoaderCallbacks<Cursor> {

        @Override
        public Loader<Cursor> onCreateLoader(int id, Bundle args) {
            if (id == MESSAGES_QUERY_ID) {
                return new MessageDb.Loader(MessageActivity.this, mTopicName, -1, -1);
            }
            return null;
        }

        @Override
        public void onLoadFinished(Loader<Cursor> loader,
                                   Cursor cursor) {
            if (loader.getId() == MESSAGES_QUERY_ID) {
                Log.d(TAG, "Got cursor with itemcount=" + cursor.getCount());
                mMsgFragment.swapCursor(mTopicName, cursor);
            }
        }

        @Override
        public void onLoaderReset(Loader<Cursor> loader) {
            if (loader.getId() == MESSAGES_QUERY_ID) {
                mMsgFragment.swapCursor(null, null);
            }
        }
    }
}
