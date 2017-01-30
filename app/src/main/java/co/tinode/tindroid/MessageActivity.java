package co.tinode.tindroid;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.Log;
import android.view.GestureDetector;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import co.tinode.tindroid.account.Utils;
import co.tinode.tindroid.db.MessageDb;
import co.tinode.tinodesdk.NotConnectedException;
import co.tinode.tinodesdk.PromisedReply;
import co.tinode.tinodesdk.Tinode;
import co.tinode.tinodesdk.Topic;
import co.tinode.tinodesdk.model.Description;
import co.tinode.tinodesdk.model.MsgServerData;
import co.tinode.tinodesdk.model.MsgServerInfo;
import co.tinode.tinodesdk.model.MsgServerPres;
import co.tinode.tinodesdk.model.ServerMessage;

/**
 * View to display a single conversation
 */
public class MessageActivity extends AppCompatActivity {

    private static final String TAG = "MessageActivity";

    private static final int MESSAGES_QUERY_ID = 100;

    private MessagesListAdapter mMessagesAdapter;
    private RecyclerView mMessageList;
    private LoaderManager mLoaderManager;
    private MessageLoaderCallbacks mLoaderCallbacks;
    private String mMessageText = null;

    private String mTopicName;
    protected Topic<VCard, String, String> mTopic;

    public static void runLoader(final int loaderId, final Bundle args,
                                  final LoaderManager.LoaderCallbacks<Cursor> callbacks,
                                  final LoaderManager loaderManager) {
        final Loader<Cursor> loader = loaderManager.getLoader(loaderId);
        if (loader != null && !loader.isReset()) {
            loaderManager.restartLoader(loaderId, args, callbacks);
        } else {
            loaderManager.initLoader(loaderId, args, callbacks);
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
                Intent intent = new Intent(MessageActivity.this, ContactsActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(intent);
            }
        });

        LinearLayoutManager lm = new LinearLayoutManager(this);
        //lm.setReverseLayout(true);
        lm.setStackFromEnd(true);

        mMessageList = (RecyclerView) findViewById(R.id.messages_container);

        mMessageList.setLayoutManager(lm);

        mLoaderManager = getSupportLoaderManager();
        mLoaderCallbacks = new MessageLoaderCallbacks();
        mMessagesAdapter = new MessagesListAdapter(this);
        mMessageList.setAdapter(mMessagesAdapter);
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
            runLoader(MESSAGES_QUERY_ID, null, mLoaderCallbacks, mLoaderManager);

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
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_topic_settings, menu);
        return true;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        setIntent(intent);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        UiUtils.setVisibleTopic(hasFocus ? mTopicName : null);
    }

    public void scrollTo(int position) {
        position = position == -1 ? mMessagesAdapter.getItemCount() - 1 : position;
        mMessageList.scrollToPosition(position);
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
            mMessagesAdapter.notifyDataSetChanged();
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
                                    mMessagesAdapter.notifyDataSetChanged();
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
                runLoader(MESSAGES_QUERY_ID, null, mLoaderCallbacks, mLoaderManager);
            }
        }
    }

    private class TListener extends Topic.Listener<VCard, String, String> {

        TListener() {}

        @Override
        public void onSubscribe(int code, String text) {
            // Topic name may change after subscription, i.e. new -> grpXXX
            mTopicName = mTopic.getName();
            runLoader(MESSAGES_QUERY_ID, null, mLoaderCallbacks, mLoaderManager);
        }

        @Override
        public void onData(MsgServerData data) {
            runLoader(MESSAGES_QUERY_ID, null, mLoaderCallbacks, mLoaderManager);
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
                            mMessagesAdapter.notifyDataSetChanged();
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
                mMessagesAdapter.swapCursor(mTopicName, cursor);
            }
        }

        @Override
        public void onLoaderReset(Loader<Cursor> loader) {
            if (loader.getId() == MESSAGES_QUERY_ID) {
                mMessagesAdapter.swapCursor(null, null);
            }
        }
    }
}
