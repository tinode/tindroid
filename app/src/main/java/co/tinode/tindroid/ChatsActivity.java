package co.tinode.tindroid;

import android.Manifest;
import android.accounts.Account;
import android.accounts.AccountManager;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;

import java.util.List;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import co.tinode.tindroid.account.ContactsManager;
import co.tinode.tindroid.account.Utils;
import co.tinode.tindroid.media.VxCard;
import co.tinode.tinodesdk.MeTopic;
import co.tinode.tinodesdk.Tinode;
import co.tinode.tinodesdk.Topic;
import co.tinode.tinodesdk.model.Credential;
import co.tinode.tinodesdk.model.Description;
import co.tinode.tinodesdk.model.MsgServerInfo;
import co.tinode.tinodesdk.model.MsgServerPres;
import co.tinode.tinodesdk.model.PrivateType;
import co.tinode.tinodesdk.model.Subscription;

/**
 * This activity owns 'me' topic.
 */
public class ChatsActivity extends AppCompatActivity implements UiUtils.ProgressIndicator {

    private static final String TAG = "ContactsActivity";

    static final String FRAGMENT_CHATLIST = "contacts";
    static final String FRAGMENT_ACCOUNT_INFO = "account_info";
    static final String FRAGMENT_ACC_HELP = "acc_help";
    static final String FRAGMENT_ACC_NOTIFICATIONS = "acc_notifications";
    static final String FRAGMENT_ACC_PERSONAL = "acc_personal";
    static final String FRAGMENT_ACC_SECURITY = "acc_security";
    static final String FRAGMENT_ACC_ABOUT = "acc_about";
    static final String FRAGMENT_ARCHIVE = "archive";

    private ContactsEventListener mTinodeListener = null;
    private MeListener mMeTopicListener = null;
    private MeTopic<VxCard> mMeTopic = null;

    private Account mAccount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_contacts);

        setSupportActionBar((Toolbar) findViewById(R.id.toolbar));

        ChatsFragment fragment = new ChatsFragment();
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.contentFragment, fragment, FRAGMENT_CHATLIST)
                .setPrimaryNavigationFragment(fragment)
                .commit();

        mMeTopic = Cache.getTinode().getOrCreateMeTopic();
        mMeTopicListener = new MeListener();
    }

    /**
     * onResume restores subscription to 'me' topic and sets listener.
     */
    @Override
    public void onResume() {
        super.onResume();

        final Tinode tinode = Cache.getTinode();
        mTinodeListener = new ContactsEventListener(tinode.isConnected());
        tinode.addListener(mTinodeListener);

        UiUtils.setupToolbar(this, null, null, false);

        if (!mMeTopic.isAttached()) {
            toggleProgressIndicator(true);
        }

        // This will issue a subscription request.
        if (!UiUtils.attachMeTopic(this, mMeTopicListener)) {
            toggleProgressIndicator(false);
        }
    }

    private void datasetChanged() {
        final FragmentManager fm = getSupportFragmentManager();
        Fragment fragment = fm.findFragmentByTag(FRAGMENT_CHATLIST);
        if (fragment == null || !fragment.isVisible()) {
            fragment = fm.findFragmentByTag(FRAGMENT_ARCHIVE);
        }
        if (fragment != null && fragment.isVisible()) {
            ((ChatsFragment) fragment).datasetChanged();
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        Cache.getTinode().removeListener(mTinodeListener);
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mMeTopic != null) {
            mMeTopic.setListener(null);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Enable options menu by returning true
        return true;
    }

    void showFragment(String tag) {
        if (isFinishing() || isDestroyed()) {
            return;
        }

        final FragmentManager fm = getSupportFragmentManager();
        Fragment fragment = fm.findFragmentByTag(tag);
        FragmentTransaction trx = fm.beginTransaction();
        if (fragment == null) {
            switch (tag) {
                case FRAGMENT_ACCOUNT_INFO:
                    fragment = new AccountInfoFragment();
                    break;
                case FRAGMENT_ACC_HELP:
                    fragment = new AccHelpFragment();
                    break;
                case FRAGMENT_ACC_NOTIFICATIONS:
                    fragment = new AccNotificationsFragment();
                    break;
                case FRAGMENT_ACC_PERSONAL:
                    fragment = new AccPersonalFragment();
                    break;
                case FRAGMENT_ACC_SECURITY:
                    fragment = new AccSecurityFragment();
                    break;
                case FRAGMENT_ACC_ABOUT:
                    fragment = new AccAboutFragment();
                    break;
                case FRAGMENT_ARCHIVE:
                    fragment = new ChatsFragment();
                    Bundle args = new Bundle();
                    args.putBoolean("archive", Boolean.TRUE);
                    fragment.setArguments(args);
                    break;
                case FRAGMENT_CHATLIST:
                    fragment = new ChatsFragment();
                    break;
                default:
                    throw new IllegalArgumentException("Failed to create fragment: unknown tag "+tag);
            }
        }

        trx.replace(R.id.contentFragment, fragment, tag)
                .addToBackStack(tag)
                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                .commit();
    }

    @Override
    public void toggleProgressIndicator(boolean on) {
        List<Fragment> fragments = getSupportFragmentManager().getFragments();
        for (Fragment f : fragments) {
            if (f instanceof UiUtils.ProgressIndicator) {
                ((UiUtils.ProgressIndicator) f).toggleProgressIndicator(on);
            }
        }
    }

    interface FormUpdatable {
        void updateFormValues(final AppCompatActivity activity, final MeTopic<VxCard> me);
    }

    // This is called on Websocket thread.
    private class MeListener extends UiUtils.MeEventListener {
        private void updateVisibleInfoFragment() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    List<Fragment> fragments = getSupportFragmentManager().getFragments();
                    for(Fragment f : fragments) {
                        if (f != null && f.isVisible() && f instanceof FormUpdatable) {
                            ((FormUpdatable) f).updateFormValues(ChatsActivity.this, mMeTopic);
                        }
                    }
                }
            });
        }

        @Override
        public void onInfo(MsgServerInfo info) {
            // FIXME: this is not supposed to happen.
            Log.e(TAG, "Contacts got onInfo update '" + info.what + "'");
        }

        @Override
        public void onPres(MsgServerPres pres) {
            if ("msg".equals(pres.what)) {
                datasetChanged();
            } else if ("off".equals(pres.what) || "on".equals(pres.what)) {
                datasetChanged();
            }
        }

        @Override
        public void onMetaSub(final Subscription<VxCard,PrivateType> sub) {
            if (sub.deleted == null) {
                if (sub.pub != null) {
                    sub.pub.constructBitmap();
                }

                if (!UiUtils.isPermissionGranted(ChatsActivity.this, Manifest.permission.WRITE_CONTACTS)) {
                    // We can't save contact if we don't have appropriate permission.
                    return;
                }

                if (mAccount == null) {
                    mAccount = Utils.getSavedAccount(ChatsActivity.this,
                            AccountManager.get(ChatsActivity.this), Cache.getTinode().getMyId());
                }
                if (Topic.getTopicTypeByName(sub.topic) == Topic.TopicType.P2P) {
                    ContactsManager.processContact(ChatsActivity.this,
                            ChatsActivity.this.getContentResolver(),
                            mAccount, sub.pub, null, sub.getUnique(), sub.deleted != null,
                            null, false);
                }
            }
        }

        @Override
        public void onMetaDesc(final Description<VxCard,PrivateType> desc) {
            if (desc.pub != null) {
                desc.pub.constructBitmap();
            }

            updateVisibleInfoFragment();
        }

        @Override
        public void onSubsUpdated() {
            datasetChanged();
        }

        @Override
        public void onSubscriptionError(Exception ex) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Fragment fragment = UiUtils.getVisibleFragment(getSupportFragmentManager());
                    if (fragment instanceof UiUtils.ProgressIndicator) {
                        ((UiUtils.ProgressIndicator) fragment).toggleProgressIndicator(false);
                    }
                }
            });
        }

        @Override
        public void onContUpdated(final String contact) {
            Log.d(TAG, "Contacts got onContUpdated update '" + contact + "'");
        }

        @Override
        public void onMetaTags(String[] tags) {
            updateVisibleInfoFragment();
        }

        @Override
        public  void onCredUpdated(Credential[] cred) {
            updateVisibleInfoFragment();
        }
    }

    private class ContactsEventListener extends UiUtils.EventListener {
        ContactsEventListener(boolean online) {
            super(ChatsActivity.this, online);
        }

        @Override
        public void onLogin(int code, String txt) {
            super.onLogin(code, txt);
            UiUtils.attachMeTopic(ChatsActivity.this, mMeTopicListener);
        }

        @Override
        public void onDisconnect(boolean byServer, int code, String reason) {
            super.onDisconnect(byServer, code, reason);

            // Update online status of contacts.
            datasetChanged();
        }
    }
}