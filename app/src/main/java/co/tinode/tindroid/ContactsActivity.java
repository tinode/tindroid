package co.tinode.tindroid;

import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.widget.Toast;

import co.tinode.tindroid.media.VCard;
import co.tinode.tinodesdk.MeTopic;
import co.tinode.tinodesdk.NotConnectedException;
import co.tinode.tinodesdk.NotSynchronizedException;
import co.tinode.tinodesdk.Tinode;
import co.tinode.tinodesdk.Topic;
import co.tinode.tinodesdk.model.Description;
import co.tinode.tinodesdk.model.MsgServerData;
import co.tinode.tinodesdk.model.MsgServerInfo;
import co.tinode.tinodesdk.model.MsgServerPres;
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
    public void onResume() {
        super.onResume();

        final Tinode tinode = Cache.getTinode();

        tinode.setListener(new UiUtils.EventListener(this, tinode.isConnected()));

        UiUtils.setupToolbar(this, null, null, false);

        MeTopic<VCard, String> me = tinode.getMeTopic();
        if (me == null) {
            // The very first launch of the app.
            me = new MeTopic<>(tinode, mMeTopicListener);
            me.setTypes(VCard.class, String.class);
            Log.d(TAG, "Initialized NEW 'me' topic");
        } else {
            me.setListener(mMeTopicListener);
            Log.d(TAG, "Loaded existing 'me' topic");
        }

        if (!me.isAttached()) {
            try {
                Log.d(TAG, "Trying to subscribe to me");
                me.subscribe(null, me
                        .getMetaGetBuilder()
                        .withGetDesc()
                        .withGetSub()
                        .withGetData()
                        .build());
            } catch (NotSynchronizedException ignored) {
                /* */
            } catch (NotConnectedException ignored) {
                /* offline - ignored */
                Toast.makeText(this, R.string.no_connection, Toast.LENGTH_SHORT).show();
            } catch (Exception err) {
                Log.i(TAG, "Subscription failed " + err.getMessage());
                Toast.makeText(this,
                        "Failed to attach", Toast.LENGTH_LONG).show();
            }
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

    protected ChatListAdapter getChatListAdapter() {
        return mChatListAdapter;
    }

    /*
    protected Subscription<VCard, String> getContactByPos(int pos) {
        MeTopic<VCard, String, String> me = Cache.getTinode().getMeTopic();
        return me.getSubscription(mContactIndex.get(pos));
    }
    */

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Enable options menu by returning true
        return true;
    }

    @Override
    public void onWindowFocusChanged(boolean focus) {
        super.onWindowFocusChanged(focus);

        Cache.activityVisible(focus);
    }

    private class MeListener extends Topic.Listener<VCard, String> {

        @Override
        public void onInfo(MsgServerInfo info) {
            Log.d(TAG, "Contacts got onInfo update '" + info.what + "'");
        }

        @Override
        public void onPres(MsgServerPres pres) {
            Log.d(TAG, "onPres, what=" + pres.what + ", topic=" + pres.topic);

            if (pres.what.equals("msg")) {
                datasetChanged();
            } else if (pres.what.equals("off") || pres.what.equals("on")) {
                datasetChanged();
            }
        }

        @Override
        public void onMetaSub(final Subscription<VCard, String> sub) {
            if (sub.pub != null) {
                sub.pub.constructBitmap();
            }
        }

        @Override
        public void onMetaDesc(final Description<VCard, String> desc) {
            if (desc.pub != null) {
                desc.pub.constructBitmap();
            }
        }

        @Override
        public void onSubsUpdated() {
            Log.d(TAG, "onSubsUpdated: datasetChanged");
            datasetChanged();
        }

        @Override
        public void onContUpdate(final Subscription<VCard, String> sub) {
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