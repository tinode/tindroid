package co.tinode.tindroid;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import java.util.Timer;

import co.tinode.tindroid.account.Utils;
import co.tinode.tindroid.media.VCard;
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

    static final String FRAGMENT_MESSAGES = "msg";
    static final String FRAGMENT_INFO = "info";
    static final String FRAGMENT_ADD_TOPIC = "add_topic";
    static final String FRAGMENT_EDIT_MEMBERS = "edit_members";

    // How long a typing indicator should play its animation, milliseconds.
    private static final int TYPING_INDICATOR_DURATION = 4000;
    private Timer mTypingAnimationTimer;

    private String mMessageText = null;

    private String mTopicName = null;
    private Topic<VCard, String> mTopic = null;

    private PausableSingleThreadExecutor mMessageSender = null;

    private PromisedReply.FailureListener<ServerMessage> mFailureListener;

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
                if (isFragmentVisible(FRAGMENT_EDIT_MEMBERS)) {
                    showFragment(FRAGMENT_INFO, false);
                } else if (!isFragmentVisible(FRAGMENT_MESSAGES)) {
                    showFragment(FRAGMENT_MESSAGES, false);
                } else {
                    Intent intent = new Intent(MessageActivity.this, ContactsActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                    startActivity(intent);
                }
            }
        });

        mMessageSender = new PausableSingleThreadExecutor();
        mMessageSender.pause();

        mFailureListener = new UiUtils.ToastFailureListener(this);
    }

    @Override
    public void onResume() {
        super.onResume();

        final Tinode tinode = Cache.getTinode();
        tinode.setListener(new UiUtils.EventListener(this, tinode.isConnected()));

        final Intent intent = getIntent();

        // Check if the activity was launched by internally-generated intent.
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
            Log.e(TAG, "Activity resumed with an empty topic name");
            finish();
            return;
        } else {
            Log.d(TAG, "Activity resumed with topic=" + mTopicName);
        }

        // Cancel all pending notifications addressed to the current topic
        ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).cancel(mTopicName, 0);

        mMessageText = intent.getStringExtra(Intent.EXTRA_TEXT);

        // Get a known topic.
        mTopic = tinode.getTopic(mTopicName);
        if (mTopic != null) {
            UiUtils.setupToolbar(this, mTopic.getPub(), mTopicName, mTopic.getOnline());
        } else {
            // New topic by name, either an actual grp* or p2p* topic name or a usr*
            Log.e(TAG, "Attempt to instantiate an unknown topic: " + mTopicName);
            mTopic = new Topic<>(tinode, mTopicName, null);
        }
        mTopic.setListener(new TListener());

        if (!mTopic.isAttached()) {
            try {
                mTopic.subscribe(null,
                        mTopic.getMetaGetBuilder()
                                .withGetDesc()
                                .withGetSub()
                                .withGetData()
                                .build()).thenApply(new PromisedReply.SuccessListener<ServerMessage>() {
                    @Override
                    public PromisedReply<ServerMessage> onSuccess(ServerMessage result) throws Exception {
                        UiUtils.setupToolbar(MessageActivity.this, mTopic.getPub(),
                                mTopicName, mTopic.getOnline());
                        mMessageSender.resume();
                        // Submit unsent messages for processing.
                        mMessageSender.submit(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    mTopic.publishPending();
                                } catch (Exception ignored) {
                                }
                            }
                        });
                        return null;
                    }
                }, mFailureListener);
            } catch (NotConnectedException ignored) {
                Log.d(TAG, "Offline mode, ignore");
            } catch (Exception ex) {
                Toast.makeText(this, R.string.action_failed, Toast.LENGTH_SHORT).show();
                Log.e(TAG, "something went wrong", ex);
            }
        }


        if (oldTopicName == null || !oldTopicName.equals(mTopicName)) {
            showFragment(FRAGMENT_MESSAGES, false);
        }
    }

    @Override
    public void onPause() {
        Log.d(TAG, "onPause");
        super.onPause();
        mMessageSender.pause();
        if (mTypingAnimationTimer != null) {
            mTypingAnimationTimer.cancel();
            mTypingAnimationTimer = null;
        }

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
    public void onDestroy() {
        super.onDestroy();

        mMessageSender.shutdownNow();
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
            case R.id.action_view_contact: {
                showFragment(FRAGMENT_INFO, false);
                return true;
            }
            case R.id.action_topic_edit: {
                showFragment(FRAGMENT_ADD_TOPIC, false);
                return true;
            }
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private boolean isFragmentVisible(String tag) {
        Fragment fragment = getSupportFragmentManager().findFragmentByTag(tag);
        return fragment != null && fragment.isVisible();
    }

    void showFragment(String tag, boolean addToBackstack) {
        FragmentManager fm = getSupportFragmentManager();
        Fragment fragment = fm.findFragmentByTag(tag);
        if (fragment == null) {
            switch (tag) {
                case FRAGMENT_MESSAGES:
                    fragment = new MessagesFragment();
                    break;
                case FRAGMENT_INFO:
                    fragment = new TopicInfoFragment();
                    break;
                case FRAGMENT_ADD_TOPIC:
                    fragment = new CreateGroupFragment();
                    break;
                case FRAGMENT_EDIT_MEMBERS:
                    fragment = new EditMembersFragment();
                    break;
            }
        }
        if (fragment == null) {
            throw new NullPointerException();
        }

        Bundle args = new Bundle();
        args.putString("topic", mTopicName);
        args.putString("messageText", mMessageText);
        if (fragment.getArguments() != null) {
            fragment.getArguments().putAll(args);
        } else {
            fragment.setArguments(args);
        }

        if (!fragment.isVisible()) {
            FragmentTransaction trx = fm.beginTransaction();
            trx.replace(R.id.contentFragment, fragment, tag);
            if (addToBackstack) {
                trx.addToBackStack(tag);
            }
            trx.commit();
        }
    }

    public void sendKeyPress() {
        if (mTopic != null) {
            mTopic.noteKeyPress();
        }
    }

    public void submitForExecution(Runnable runnable) {
        mMessageSender.submit(runnable);
    }

    private class TListener extends Topic.Listener<VCard, String> {

        TListener() {
        }

        @Override
        public void onSubscribe(int code, String text) {
            // Topic name may change after subscription, i.e. new -> grpXXX
            mTopicName = mTopic.getName();
            /*
            MessagesFragment fragment = (MessagesFragment) getSupportFragmentManager().
                    findFragmentByTag(FRAGMENT_MESSAGES);
            if (fragment != null && fragment.isVisible()) {
                fragment.runLoader();
            }
            */
        }

        @Override
        public void onData(MsgServerData data) {
            MessagesFragment fragment = (MessagesFragment) getSupportFragmentManager().
                    findFragmentByTag(FRAGMENT_MESSAGES);
            if (fragment != null && fragment.isVisible()) {
                fragment.runLoader();
            }
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
                            MessagesFragment fragment = (MessagesFragment) getSupportFragmentManager().
                                    findFragmentByTag(FRAGMENT_MESSAGES);
                            if (fragment != null && fragment.isVisible()) {
                                fragment.notifyDataSetChanged();
                            }
                        }
                    });
                    break;
                case "kp":
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            // Show typing indicator as animation over avatar in toolbar
                            mTypingAnimationTimer = UiUtils.toolbarTypingIndicator(MessageActivity.this,
                                    mTypingAnimationTimer, TYPING_INDICATOR_DURATION);
                        }
                    });
                    break;
                default:
                    break;
            }
        }

        @Override
        public void onSubsUpdated() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    TopicInfoFragment fragment = (TopicInfoFragment) getSupportFragmentManager().
                            findFragmentByTag(FRAGMENT_INFO);

                    if (fragment != null && fragment.isVisible()) {
                        fragment.notifyDataSetChanged();
                    }
                }
            });
        }

        @Override
        public void onMetaDesc(final Description<VCard, String> desc) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    UiUtils.setupToolbar(MessageActivity.this, mTopic.getPub(), mTopic.getName(),
                            mTopic.getOnline());

                    TopicInfoFragment fragment = (TopicInfoFragment) getSupportFragmentManager().
                            findFragmentByTag(FRAGMENT_INFO);
                    if (fragment != null && fragment.isVisible()) {
                        fragment.notifyContentChanged();
                    }
                }
            });
        }

        @Override
        public void onContUpdate(final Subscription<VCard, String> sub) {
            onMetaDesc(null);
        }

        @Override
        public void onOnline(final boolean online) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    UiUtils.toolbarSetOnline(MessageActivity.this, mTopic.getOnline());
                }
            });

        }
    }

}
