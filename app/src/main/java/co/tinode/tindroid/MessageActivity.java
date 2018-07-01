package co.tinode.tindroid;

import android.app.DownloadManager;
import android.app.NotificationManager;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.content.FileProvider;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import java.io.File;
import java.net.URI;
import java.util.Map;
import java.util.Timer;

import co.tinode.tindroid.account.Utils;
import co.tinode.tindroid.media.VxCard;
import co.tinode.tinodesdk.ComTopic;
import co.tinode.tinodesdk.NotConnectedException;
import co.tinode.tinodesdk.PromisedReply;
import co.tinode.tinodesdk.Tinode;
import co.tinode.tinodesdk.model.Description;
import co.tinode.tinodesdk.model.MsgServerData;
import co.tinode.tinodesdk.model.MsgServerInfo;
import co.tinode.tinodesdk.model.MsgServerPres;
import co.tinode.tinodesdk.model.PrivateType;
import co.tinode.tinodesdk.model.ServerMessage;
import co.tinode.tinodesdk.model.Subscription;

/**
 * View to display a single conversation
 */
public class MessageActivity extends AppCompatActivity {

    private static final String TAG = "MessageActivity";

    static final String FRAGMENT_MESSAGES = "msg";
    static final String FRAGMENT_INVALID ="invalid";
    static final String FRAGMENT_INFO = "info";
    static final String FRAGMENT_ADD_TOPIC = "add_topic";
    static final String FRAGMENT_EDIT_MEMBERS = "edit_members";
    static final String FRAGMENT_VIEW_IMAGE ="view_image";

    // How long a typing indicator should play its animation, milliseconds.
    private static final int TYPING_INDICATOR_DURATION = 4000;
    private Timer mTypingAnimationTimer;

    private String mMessageText = null;

    private String mTopicName = null;
    private ComTopic<VxCard> mTopic = null;

    private PausableSingleThreadExecutor mMessageSender = null;

    private DownloadManager mDownloadMgr = null;
    private long mDownloadId = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_messages);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ActionBar ab = getSupportActionBar();
        if (ab != null) {
            ab.setDisplayHomeAsUpEnabled(true);
            ab.setDisplayShowHomeEnabled(true);
        }
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isFragmentVisible(FRAGMENT_EDIT_MEMBERS)) {
                    showFragment(FRAGMENT_INFO, false, null);
                } else if (!isFragmentVisible(FRAGMENT_MESSAGES) && !isFragmentVisible(FRAGMENT_INVALID)) {
                    showFragment(FRAGMENT_MESSAGES, false, null);
                } else {
                    Intent intent = new Intent(MessageActivity.this, ContactsActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                    startActivity(intent);
                }
            }
        });

        mDownloadMgr = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
        registerReceiver(onComplete, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
        registerReceiver(onNotificationClick, new IntentFilter(DownloadManager.ACTION_NOTIFICATION_CLICKED));

        mMessageSender = new PausableSingleThreadExecutor();
        mMessageSender.pause();
    }

    @Override
    @SuppressWarnings("unchecked")
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
        }

        // Cancel all pending notifications addressed to the current topic
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.cancel(mTopicName, 0);
        }
        mMessageText = intent.getStringExtra(Intent.EXTRA_TEXT);

        // Get a known topic.
        mTopic = (ComTopic<VxCard>) tinode.getTopic(mTopicName);
        if (mTopic != null) {
            UiUtils.setupToolbar(this, mTopic.getPub(), mTopicName, mTopic.getOnline());
            showFragment(FRAGMENT_MESSAGES, false, null);
        } else {
            // New topic by name, either an actual grp* or p2p* topic name or a usr*
            Log.i(TAG, "Attempt to instantiate an unknown topic: " + mTopicName);
            mTopic = (ComTopic<VxCard>) tinode.newTopic(mTopicName, null);
            showFragment(FRAGMENT_INVALID, false, null);
        }
        mTopic.setListener(new TListener());

        if (!mTopic.isAttached()) {
            try {
                mTopic.subscribe(null,
                        mTopic.getMetaGetBuilder()
                                .withGetDesc()
                                .withGetSub()
                                .withGetData()
                                .withGetDel()
                                .build()).thenApply(new PromisedReply.SuccessListener<ServerMessage>() {
                    @Override
                    public PromisedReply<ServerMessage> onSuccess(ServerMessage result) {
                        UiUtils.setupToolbar(MessageActivity.this, mTopic.getPub(),
                                mTopicName, mTopic.getOnline());
                        showFragment(FRAGMENT_MESSAGES, false, null);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                MessagesFragment fragmsg = (MessagesFragment) getSupportFragmentManager()
                                        .findFragmentByTag(FRAGMENT_MESSAGES);
                                fragmsg.topicSubscribed();
                            }
                        });
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
                }, new PromisedReply.FailureListener<ServerMessage>() {
                    @Override
                    public PromisedReply<ServerMessage> onFailure(Exception err) {
                        showFragment(FRAGMENT_INVALID, false, null);
                        return null;
                    }
                });
            } catch (NotConnectedException ignored) {
                Log.d(TAG, "Offline mode, ignore");
            } catch (Exception ex) {
                Toast.makeText(this, R.string.action_failed, Toast.LENGTH_SHORT).show();
                Log.e(TAG, "something went wrong", ex);
            }
        } else {
            MessagesFragment fragmsg = (MessagesFragment) getSupportFragmentManager()
                    .findFragmentByTag(FRAGMENT_MESSAGES);
            fragmsg.topicSubscribed();
        }
    }

    @Override
    public void onPause() {
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
        unregisterReceiver(onComplete);
        unregisterReceiver(onNotificationClick);
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
        if (mTopic == null || !mTopic.isValid()) {
            return false;
        }

        int id = item.getItemId();
        switch (id) {
            case R.id.action_view_contact: {
                showFragment(FRAGMENT_INFO, false, null);
                return true;
            }
            case R.id.action_topic_edit: {
                showFragment(FRAGMENT_ADD_TOPIC, false, null);
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

    void showFragment(String tag, boolean addToBackstack, Bundle args) {
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
                case FRAGMENT_VIEW_IMAGE:
                    fragment = new ImageViewFragment();
                    break;
                case FRAGMENT_INVALID:
                    fragment = new InvalidTopicFragment();
                    break;
            }
        }
        if (fragment == null) {
            throw new NullPointerException();
        }

        args = args != null ? args : new Bundle();
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

    public void startDownload(Uri uri, String fname, String mime, Map<String,String> headers) {
        Environment
                .getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                .mkdirs();

        DownloadManager.Request req = new DownloadManager.Request(uri);
        if (headers != null) {
            for (Map.Entry<String,String> entry : headers.entrySet()) {
                req.addRequestHeader(entry.getKey(), entry.getValue());
            }
        }

        mDownloadId = mDownloadMgr.enqueue(
                req.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI |
                        DownloadManager.Request.NETWORK_MOBILE)
                .setMimeType(mime)
                .setAllowedOverRoaming(false)
                .setTitle(fname)
                .setDescription(getString(R.string.download_title))
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setVisibleInDownloadsUi(true)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fname));
    }

    BroadcastReceiver onComplete=new BroadcastReceiver() {
        public void onReceive(Context ctx, Intent intent) {
            if (DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(intent.getAction()) &&
                intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, 0) == mDownloadId) {
                DownloadManager.Query query = new DownloadManager.Query();
                query.setFilterById(mDownloadId);
                Cursor c = mDownloadMgr.query(query);
                if (c.moveToFirst()) {
                    if (DownloadManager.STATUS_SUCCESSFUL == c.getInt(c.getColumnIndex(DownloadManager.COLUMN_STATUS))) {
                        URI fileUri = URI.create(c.getString(c.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)));
                        String mimeType = c.getString(c.getColumnIndex(DownloadManager.COLUMN_MEDIA_TYPE));
                        intent = new Intent();
                        intent.setAction(android.content.Intent.ACTION_VIEW);
                        intent.setDataAndType(FileProvider.getUriForFile(MessageActivity.this,
                                "co.tinode.tindroid.provider", new File(fileUri)), mimeType);
                        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        try {
                            startActivity(intent);
                        } catch (ActivityNotFoundException ignored) {
                            startActivity(new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS));
                        }
                    }
                }
                c.close();
            }
        }
    };

    BroadcastReceiver onNotificationClick=new BroadcastReceiver() {
        public void onReceive(Context ctxt, Intent intent) {
            Log.d(TAG, "onNotificationClick" + intent.getExtras());
        }
    };

    private class TListener extends ComTopic.ComListener<VxCard> {

        TListener() {
        }

        @Override
        public void onSubscribe(int code, String text) {
            // Topic name may change after subscription, i.e. new -> grpXXX
            mTopicName = mTopic.getName();
        }

        @Override
        public void onData(MsgServerData data) {
            final MessagesFragment fragment = (MessagesFragment) getSupportFragmentManager().
                    findFragmentByTag(FRAGMENT_MESSAGES);
            if (fragment != null && fragment.isVisible()) {
                fragment.runMessagesLoader();
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
        public void onMetaDesc(final Description<VxCard,PrivateType> desc) {
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
        public void onContUpdate(final Subscription<VxCard,PrivateType> sub) {
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
