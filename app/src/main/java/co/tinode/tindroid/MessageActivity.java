package co.tinode.tindroid;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.ListView;
import android.widget.TextView;

import java.util.Timer;
import java.util.TimerTask;

import co.tinode.tindroid.account.Utils;
import co.tinode.tinodesdk.Topic;
import co.tinode.tinodesdk.model.Description;
import co.tinode.tinodesdk.model.MsgServerData;
import co.tinode.tinodesdk.model.MsgServerInfo;
import co.tinode.tinodesdk.model.MsgServerMeta;
import co.tinode.tinodesdk.model.MsgServerPres;
import co.tinode.tinodesdk.model.Subscription;

import static co.tinode.tindroid.InmemoryCache.*;

/**
 * View to display a single conversation
 */
public class MessageActivity extends AppCompatActivity {

    private static final String TAG = "MessageActivity";

    // Delay before sending out a RECEIVED notification to be sure we are not sending too many.
    // private static final int RECV_DELAY = 500;
    private static final int READ_DELAY = 1000;

    private String mTopicName;
    private Topic<VCard,String,String> mTopic;
    private MessagesListAdapter mMessagesAdapter;

    private Timer mNoteTimer = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_messages);

        mMessagesAdapter = new MessagesListAdapter(this);

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

        ListView listViewMessages = (ListView) findViewById(R.id.messages_container);
        listViewMessages.setAdapter(mMessagesAdapter);
    }

    @Override
    public void onResume() {
        super.onResume();

        final Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        final Intent intent = getIntent();

        // Check if the activity was launched by internally-generated intent
        String oldTopicName = mTopicName;
        mTopicName = intent.getStringExtra("topic");

        if (TextUtils.isEmpty(mTopicName)) {
            // mTopicName is empty, so this is an external intent
            Uri contactUri = intent.getData();
            //String contactMimeType = intent.getType();

            Cursor cursor = getContentResolver().query(contactUri,
                    new String[] {Utils.DATA_PID}, null, null, null);
            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    mTopicName = cursor.getString(cursor.getColumnIndex(Utils.DATA_PID));
                }
                cursor.close();
            }
        }

        String messageToSend = intent.getStringExtra(Intent.EXTRA_TEXT);
        ((TextView) findViewById(R.id.editMessage)).setText(messageToSend == null ? "" : messageToSend);

        mTopic = getTinode().getTopic(mTopicName);

        // Check periodically if all messages were read;
        mNoteTimer = new Timer();
        mNoteTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (mTopic != null) {
                    // Topic will not send an update if there is no change
                    mTopic.noteRead();
                }
            }
        }, READ_DELAY, READ_DELAY);

        if (mTopic != null) {
            setupToolbar(MessageActivity.this, toolbar, mTopic.getPublic(), mTopic.getTopicType());
            if (oldTopicName == null || !mTopicName.equals(oldTopicName)) {
                mMessagesAdapter.changeTopic(mTopicName);
            }
        } else {
            mTopic = new Topic<>(getTinode(), mTopicName,
                    new Topic.Listener<VCard,String,String>() {

                        @Override
                        public void onSubscribe(int code, String text) {
                            // Topic name may change after subscription, i.e. new -> grpXXX
                            mTopicName = mTopic.getName();
                            mMessagesAdapter.changeTopic(mTopic.getName());
                        }

                        @Override
                        public void onData(MsgServerData data) {

                            runOnUiThread(new Runnable() {
                                              @Override
                                              public void run() {
                                                  mMessagesAdapter.notifyDataSetChanged();
                                              }
                                          });
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
                                    break;
                                default:
                                    break;
                            }
                        }

                        @Override
                        public void onMeta(MsgServerMeta meta) {

                        }

                        @Override
                        public void onMetaSub(Subscription sub) {

                        }

                        @Override
                        public void onMetaDesc(final Description<VCard,String> desc) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    setupToolbar(MessageActivity.this, toolbar, desc.pub, mTopic.getTopicType());
                                }
                            });
                        }

                        @Override
                        public void onSubsUpdated() {

                        }
                    });
        }

        if (!mTopic.isAttached()) {
            try {
                mTopic.subscribe();
            } catch (Exception ex) {
                Log.e(TAG, "something went wrong", ex);
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        // Stop reporting read messages
        mNoteTimer.cancel();
        mNoteTimer = null;

        // Deactivate current topic
        if (mTopic.isAttached()) {
            try {
                mTopic.leave();
            } catch (Exception ex) {
                Log.e(TAG, "something went wrong", ex);
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

    public String getTopicName() {
        return mTopicName;
    }

    public MessagesListAdapter getMessagesAdapter() {
        return mMessagesAdapter;
    }
}
