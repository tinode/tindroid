package co.tinode.tindroid;

import android.app.Activity;
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

import co.tinode.tindroid.account.Utils;
import co.tinode.tinodesdk.NotConnectedException;
import co.tinode.tinodesdk.Tinode;
import co.tinode.tinodesdk.Topic;
import co.tinode.tinodesdk.model.Description;
import co.tinode.tinodesdk.model.MsgServerData;
import co.tinode.tinodesdk.model.MsgServerInfo;
import co.tinode.tinodesdk.model.MsgServerPres;

/**
 * View to display a single conversation
 */
public class MessageActivity extends AppCompatActivity {

    private static final String TAG = "MessageActivity";

    private static final String FRAGMENT_MESSAGES = "msg";
    private static final String FRAGMENT_INFO = "info";
    private static final String FRAGMENT_EDIT_TOPIC = "edit_topic";

    private String mMessageText = null;

    private String mTopicName = null;
    private Topic<VCard, String, String> mTopic = null;

    // private MessagesFragment mMsgFragment = null;
    // private TopicInfoFragment mInfoFragment = null;
    // private EditGroupFragment mEditTopicFragment = null;

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
                if (!isFragmentVisible(FRAGMENT_MESSAGES)) {
                    showFragment(FRAGMENT_MESSAGES);
                } else {
                    Intent intent = new Intent(MessageActivity.this, ContactsActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                    startActivity(intent);
                }
            }
        });
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
            UiUtils.setupToolbar(this, mTopic.getPub(), mTopic.getTopicType(), mTopic.getOnline());
            mTopic.setListener(new TListener());

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
            Log.e(TAG, "Attempt to instantiate an unknown topic: " + mTopicName);
        }

        showFragment(FRAGMENT_MESSAGES);
    }

    @Override
    public void onPause() {
        Log.d(TAG, "onPause");
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
            case R.id.action_view_contact: {
                showFragment(FRAGMENT_INFO);
                return true;
            }
            case R.id.action_topic_edit: {
                showFragment(FRAGMENT_EDIT_TOPIC);
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

    private void showFragment(String tag) {
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
                case FRAGMENT_EDIT_TOPIC:
                    fragment = new EditGroupFragment();
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
            trx.commit();
        }
    }

    public void sendKeyPress() {
        if (mTopic != null) {
            mTopic.noteKeyPress();
        }
    }

    private class TListener extends Topic.Listener<VCard, String, String> {

        TListener() {}

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
                    // TODO(gene): show typing notification
                    Log.d(TAG, info.from + ": typing...");
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
            Log.d(TAG, "onMetaDesc!");
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    UiUtils.setupToolbar(MessageActivity.this, mTopic.getPub(), mTopic.getTopicType(),
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

}
