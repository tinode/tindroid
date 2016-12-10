package co.tinode.tindroid;

import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
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
import android.view.Menu;
import android.view.View;
import android.widget.TextView;

import co.tinode.tindroid.account.Utils;
import co.tinode.tindroid.db.BaseDb;
import co.tinode.tindroid.db.MessageDb;
import co.tinode.tindroid.db.StoredTopic;
import co.tinode.tindroid.db.StoredUser;
import co.tinode.tindroid.db.TopicDb;
import co.tinode.tinodesdk.PromisedReply;
import co.tinode.tinodesdk.Tinode;
import co.tinode.tinodesdk.Topic;
import co.tinode.tinodesdk.model.Description;
import co.tinode.tinodesdk.model.MsgGetMeta;
import co.tinode.tinodesdk.model.MsgServerData;
import co.tinode.tinodesdk.model.MsgServerInfo;
import co.tinode.tinodesdk.model.MsgServerMeta;
import co.tinode.tinodesdk.model.MsgServerPres;
import co.tinode.tinodesdk.model.ServerMessage;
import co.tinode.tinodesdk.model.Subscription;

/**
 * View to display a single conversation
 */
public class MessageActivity extends AppCompatActivity {

    private static final String TAG = "MessageActivity";

    private static final int MESSAGES_QUERY_ID = 100;

    private MessagesListAdapter mMessagesAdapter;
    private RecyclerView mMessageList;
    private MessageLoaderCallbacks mLoaderCallbacks;
    private String mMessageText = null;

    private String mTopicName;
    protected Topic<VCard, String, String> mTopic;

    private SQLiteDatabase mDb;

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

        mDb = BaseDb.getInstance(this, Cache.getTinode().getMyId()).getWritableDatabase();

        LinearLayoutManager lm = new LinearLayoutManager(this);
        //lm.setReverseLayout(true);
        lm.setStackFromEnd(true);

        mMessageList = (RecyclerView) findViewById(R.id.messages_container);
        mMessageList.setLayoutManager(lm);

        mLoaderCallbacks = new MessageLoaderCallbacks();
        mMessagesAdapter = new MessagesListAdapter(this);
        mMessageList.setAdapter(mMessagesAdapter);
    }

    @Override
    public void onResume() {
        super.onResume();

        final Tinode tinode = Cache.getTinode();
        tinode.setListener(new UiUtils.EventListener(this));

        final Intent intent = getIntent();
        final LoaderManager loaderManager = getSupportLoaderManager();

        // Check if the activity was launched by internally-generated intent
        String oldTopicName = mTopicName;
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

        mMessageText = intent.getStringExtra(Intent.EXTRA_TEXT);

        // Get a known topic.
        mTopic = Cache.getTinode().getTopic(mTopicName);
        // sub could be null if this is a new topic.
        if (mTopic != null) {
            UiUtils.setupToolbar(this, mTopic.getPub(), mTopic.getTopicType(),
                    Cache.isUserOnline(mTopicName));
            runLoader(MESSAGES_QUERY_ID, null, mLoaderCallbacks, loaderManager);
        } else {
            topic = new Topic<>(tinode, mTopicName,
                    new Topic.Listener<VCard, String, String>() {

                @Override
                public void onSubscribe(int code, String text) {
                    // Topic name may change after subscription, i.e. new -> grpXXX
                    mTopicName = mTopic.getName();
                    runLoader(MESSAGES_QUERY_ID, null, mLoaderCallbacks, loaderManager);
                }

                @Override
                public void onData(MsgServerData data) {
                    runLoader(MESSAGES_QUERY_ID, null, mLoaderCallbacks, loaderManager);
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
                public void onMetaSub(Subscription sub) {

                }

                @Override
                public void onMetaDesc(final Description<VCard, String> desc) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            UiUtils.setupToolbar(MessageActivity.this, desc.pub, mTopic.getTopicType(),
                                    Cache.isUserOnline(mTopicName));
                        }
                    });
                }

                @Override
                public void onSubsUpdated() {

                }
            });
        }

        if (!topic.isAttached()) {
            try {
                MsgGetMeta.GetData getData = new MsgGetMeta.GetData();
                // GetData.since is inclusive, so adding 1 to skip the item we already have.
                getData.since = mStoredTopic.getSeq() + 1;
                topic.subscribe(null, new MsgGetMeta(
                        new MsgGetMeta.GetDesc(),
                        new MsgGetMeta.GetSub(),
                        getData));
            } catch (Exception ex) {
                Log.e(TAG, "something went wrong", ex);
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        Tinode tinode = Cache.getTinode();
        tinode.setListener(null);
        Topic topic = tinode.getTopic(mTopicName);
        if (topic != null) {
            topic.setListener(null);

            // Deactivate current topic
            if (topic.isAttached()) {
                try {
                    topic.leave();
                } catch (Exception ex) {
                    Log.e(TAG, "something went wrong", ex);
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
        Topic topic = Cache.getTinode().getTopic(mTopicName);
        if (topic != null) {
            int read = topic.noteRead();
            if (read > 0) {
                if (TopicDb.updateRead(mDb, mTopicName, read)) {
                    mStoredTopic.setRead(read);
                }
            }
        }
    }

    public void sendKeyPress() {
        Topic topic = Cache.getTinode().getTopic(mTopicName);
        if (topic != null) {
            topic.noteKeyPress();
        }
    }

    public void sendMessage() {
        Topic<?,?,String> topic = Cache.getTinode().getTopic(mTopicName);
        if (topic != null) {
            final TextView inputField = (TextView) findViewById(R.id.editMessage);
            String message = inputField.getText().toString().trim();
            if (!message.equals("")) {
                try {
                    topic.publish(message).thenApply(new PromisedReply.SuccessListener<ServerMessage>() {
                        @Override
                        public PromisedReply<ServerMessage> onSuccess(ServerMessage result) throws Exception {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    // Clear text from the input field
                                    inputField.setText("");
                                }
                            });
                            return null;
                        }
                    }, null);
                } catch (Exception unused) {
                    // TODO(gene): tell user that the message was not sent or save it for future delivery.
                }
            }
        }
    }

    class MessageLoaderCallbacks implements LoaderManager.LoaderCallbacks<Cursor> {

        @Override
        public Loader<Cursor> onCreateLoader(int id, Bundle args) {
            if (id == MESSAGES_QUERY_ID) {
                return new MessageDb.Loader(
                        MessageActivity.this,
                        Cache.getTinode().getMyId(),
                        mTopicName, -1, -1);
            }
            return null;
        }

        @Override
        public void onLoadFinished(Loader<Cursor> loader,
                                   Cursor cursor) {
            if (loader.getId() == MESSAGES_QUERY_ID) {
                Log.d(TAG, "Got cursor with itemcount=" + cursor.getCount());
                mMessagesAdapter.swapCursor(cursor);
            }
        }

        @Override
        public void onLoaderReset(Loader<Cursor> loader) {
            if (loader.getId() == MESSAGES_QUERY_ID) {
                mMessagesAdapter.swapCursor(null);
            }
        }
    }
}
