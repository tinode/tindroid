package co.tinode.tindroid;

import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import co.tinode.tindroid.media.VxCard;
import co.tinode.tinodesdk.MeTopic;
import co.tinode.tinodesdk.NotConnectedException;
import co.tinode.tinodesdk.NotSynchronizedException;
import co.tinode.tinodesdk.PromisedReply;
import co.tinode.tinodesdk.Tinode;
import co.tinode.tinodesdk.model.Description;
import co.tinode.tinodesdk.model.MsgServerInfo;
import co.tinode.tinodesdk.model.MsgServerPres;
import co.tinode.tinodesdk.model.PrivateType;
import co.tinode.tinodesdk.model.Subscription;

/**
 * Activity holds the following fragments:
 *   ContactsFragment
 *     ChatListFragment
 *     ContactListFragment
 *
 *   AccountInfoFragment
 *
 * This activity owns 'me' topic.
 */
public class ContactsActivity extends AppCompatActivity implements
        ContactListFragment.OnContactsInteractionListener {

    private static final String TAG = "ContactsActivity";

    static final String FRAGMENT_CONTACTS = "contacts";
    static final String FRAGMENT_EDIT_ACCOUNT = "edit_account";


    private ChatListAdapter mChatListAdapter;

    private MeListener mMeTopicListener = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_contacts);

        getSupportFragmentManager().beginTransaction()
                .replace(R.id.contentFragment, new ContactsFragment(), FRAGMENT_CONTACTS)
                .commit();

        mChatListAdapter = new ChatListAdapter(this);
        mMeTopicListener = new MeListener();
    }

    /**
     * This interface callback lets the main contacts list fragment notify
     * this activity that a contact has been selected.
     *
     * @param contactUri The contact Uri to the selected contact.
     */
    @Override
    public void onContactSelected(Uri contactUri) {
        // Otherwise single pane layout, start a new ContactDetailActivity with
        // the contact Uri
        //Intent intent = new Intent(this, ContactDetailActivity.class);
        //intent.setData(contactUri);
        //startActivity(intent);
    }

    /**
     * This interface callback lets the main contacts list fragment notify
     * this activity that a contact is no longer selected.
     */
    @Override
    public void onSelectionCleared() {
    }

    /**
     * onResume restores subscription to 'me' topic and sets listener.
     */
    @Override
    @SuppressWarnings("unchecked")
    public void onResume() {
        super.onResume();

        final Tinode tinode = Cache.getTinode();

        tinode.setListener(new UiUtils.EventListener(this, tinode.isConnected()));

        UiUtils.setupToolbar(this, null, null, false);

        MeTopic me = tinode.getMeTopic();
        if (me == null) {
            // The very first launch of the app.
            me = new MeTopic(tinode, mMeTopicListener);
            Log.d(TAG, "Initialized NEW 'me' topic");
        } else {
            me.setListener(mMeTopicListener);
            Log.d(TAG, "Loaded existing 'me' topic");
        }

        if (!me.isAttached()) {
            try {
                Log.d(TAG, "Trying to subscribe to me");
                setProgressIndicator(true);
                me.subscribe(null, me
                        .getMetaGetBuilder()
                        .withGetDesc()
                        .withGetSub()
                        .withGetData()
                        .build())
                        .thenApply(new PromisedReply.SuccessListener() {
                            @Override
                            public PromisedReply onSuccess(Object result) throws Exception {
                                Log.d(TAG, "onSuccess() called with: result = [" + result + "]");
                                setProgressIndicator(false);
                                return null;
                            }
                        }, new PromisedReply.FailureListener() {
                            @Override
                            public PromisedReply onFailure(Exception err) throws Exception {
                                Log.d(TAG, "onFailure() called with: err = [" + err + "]");
                                setProgressIndicator(false);
                                return null;
                            }
                        });
            } catch (NotSynchronizedException ignored) {
                setProgressIndicator(false);
                /* */
            } catch (NotConnectedException ignored) {
                /* offline - ignored */
                setProgressIndicator(false);
                Toast.makeText(this, R.string.no_connection, Toast.LENGTH_SHORT).show();
            } catch (Exception err) {
                Log.i(TAG, "Subscription failed", err);
                setProgressIndicator(false);
                Toast.makeText(this,
                        "Failed to attach", Toast.LENGTH_LONG).show();
            }
        } else {
            Log.d(TAG, "onResume() called: topic is attached");
        }
    }

    private void datasetChanged() {
        mChatListAdapter.resetContent();
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mChatListAdapter.notifyDataSetChanged();
            }
        });
    }

    @Override
    public void onPause() {
        super.onPause();

        Cache.getTinode().setListener(null);
    }

    @Override
    public void onStop() {
        super.onStop();

        MeTopic me = Cache.getTinode().getMeTopic();
        if (me != null) {
            me.setListener(null);
        }
    }

    /**
     * Show progress indicator based on current status
     * @param active should be true to show progress indicator
     */
    public void setProgressIndicator(final boolean active) {
        Log.d(TAG, "setProgressIndicator() called with: active = [" + active + "]");
        if (isFinishing() || isDestroyed()) {
            return;
        }
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ProgressBar progressBar = findViewById(R.id.toolbar_progress_bar);
                if (progressBar != null) {
                    progressBar.setVisibility(active ? View.VISIBLE : View.GONE);
                }
            }
        });
    }

    protected ChatListAdapter getChatListAdapter() {
        return mChatListAdapter;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Enable options menu by returning true
        return true;
    }

    /*
    @Override
    public void onWindowFocusChanged(boolean focus) {
        super.onWindowFocusChanged(focus);

        Cache.activityVisible(focus);
    }
    */

    private class MeListener extends MeTopic.MeListener<VxCard> {

        @Override
        public void onInfo(MsgServerInfo info) {
            Log.d(TAG, "Contacts got onInfo update '" + info.what + "'");
        }

        @Override
        public void onPres(MsgServerPres pres) {
            Log.d(TAG, "onPres() called with: pres = [" + pres + "]");
            if (pres.what.equals("msg")) {
                datasetChanged();
            } else if (pres.what.equals("off") || pres.what.equals("on")) {
                datasetChanged();
            }
        }

        @Override
        public void onMetaSub(final Subscription<VxCard,PrivateType> sub) {
            Log.d(TAG, "onMetaSub() called with: sub = [" + sub + "]");
            if (sub.pub != null) {
                sub.pub.constructBitmap();
            }
        }

        @Override
        public void onMetaDesc(final Description<VxCard,PrivateType> desc) {
            Log.d(TAG, "onMetaDesc() called with: desc = [" + desc + "]");
            if (desc.pub != null) {
                desc.pub.constructBitmap();
            }
        }

        @Override
        public void onSubsUpdated() {
            Log.d(TAG, "onSubsUpdated() called");
            datasetChanged();
        }

        @Override
        public void onContUpdate(final Subscription<VxCard,PrivateType> sub) {
            Log.d(TAG, "onContUpdate() called with: sub = [" + sub + "]");
            // Method makes no sense in context of MeTopic.
            throw new UnsupportedOperationException();
        }
    }

    void showAccountInfoFragment() {
        FragmentManager fm = getSupportFragmentManager();
        Fragment fragment = fm.findFragmentByTag(FRAGMENT_EDIT_ACCOUNT);
        FragmentTransaction trx = fm.beginTransaction();
        if (fragment == null) {
            fragment = new AccountInfoFragment();
            trx.add(R.id.contentFragment, fragment, FRAGMENT_EDIT_ACCOUNT);
        }
        trx.addToBackStack(FRAGMENT_EDIT_ACCOUNT)
                .show(fragment)
                .commit();
    }

    public void selectTab(final int pageIndex) {
        FragmentManager fm = getSupportFragmentManager();
        ContactsFragment contacts = (ContactsFragment) fm.findFragmentByTag(FRAGMENT_CONTACTS);
        contacts.selectTab(pageIndex);
    }
}