package co.tinode.tindroid;

import android.net.Uri;
import android.os.Bundle;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.widget.Toast;

import co.tinode.tinodesdk.MeTopic;
import co.tinode.tinodesdk.Tinode;
import co.tinode.tinodesdk.Topic;
import co.tinode.tinodesdk.model.Description;
import co.tinode.tinodesdk.model.Invitation;
import co.tinode.tinodesdk.model.MsgServerData;
import co.tinode.tinodesdk.model.MsgServerInfo;
import co.tinode.tinodesdk.model.MsgServerPres;
import co.tinode.tinodesdk.model.Subscription;

/**
 * Display user's list of contacts
 */
public class ContactsActivity extends AppCompatActivity implements
        ContactsFragment.OnContactsInteractionListener {

    private static final String TAG = "ContactsActivity";

    protected ChatListAdapter mChatListAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_contacts);

        setSupportActionBar((Toolbar) findViewById(R.id.toolbar));

        TabLayout tabLayout = (TabLayout) findViewById(R.id.tabsContacts);
        tabLayout.setTabGravity(TabLayout.GRAVITY_FILL);

        final ViewPager viewPager = (ViewPager) findViewById(R.id.tabPager);
        final PagerAdapter adapter = new PagerAdapter(getSupportFragmentManager(), tabLayout.getTabCount());
        viewPager.setAdapter(adapter);
        viewPager.addOnPageChangeListener(new TabLayout.TabLayoutOnPageChangeListener(tabLayout));
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                viewPager.setCurrentItem(tab.getPosition());
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });

        mChatListAdapter = new ChatListAdapter(this);
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

    @Override
    public void onResume() {
        Log.d(TAG, "Contacts activity onResume");
        super.onResume();

        final Tinode tinode = Cache.getTinode();

        tinode.setListener(new UiUtils.EventListener(this));

        UiUtils.setupToolbar(this, null, Topic.TopicType.ME, false);

        MeTopic<VCard, String, String> me = tinode.getMeTopic();
        if (me == null) {
            Log.d(TAG, "Contacts activity: me is null");
            // The very first launch of the app.
            me = new MeTopic<>(tinode, new MeListener());
            me.setTypes(VCard.class, String.class, String.class);
        } else {
            Log.d(TAG, "Contacts activity: me is NOT null");
        }

        if (!me.isAttached()) {
            Log.d(TAG, "Contacts activity: me is NOT attached");
            try {
                me.subscribe(null, me
                        .subscribeParamGetBuilder()
                        .withGetDesc()
                        .withGetSub()
                        .withGetData()
                        .build());
            } catch (Exception err) {
                Log.i(TAG, "connection failed :( " + err.getMessage());
                Toast.makeText(getApplicationContext(),
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
        // Enable options menu by returnning true
        return true;
    }

    @Override
    public void onWindowFocusChanged(boolean focus) {
        super.onWindowFocusChanged(focus);

        Cache.activityVisible(focus);
    }

    private class MeListener extends Topic.Listener<VCard, String, Invitation<VCard,String>> {

        @Override
        public void onData(MsgServerData<Invitation<VCard,String>> data) {
            // TODO(gene): handle a chat invitation
            Log.d(TAG, "Contacts got an invitation to topic " + data.content.topic);
        }

        @Override
        public void onContactUpdate(final String what, final Subscription<VCard,String> sub) {
            datasetChanged();
        }

        @Override
        public void onInfo(MsgServerInfo info) {
            Log.d(TAG, "Contacts got onInfo update '" + info.what + "'");
        }

        @Override
        public void onPres(MsgServerPres pres) {
            if (pres.what.equals("msg")) {
                datasetChanged();
            } else if (pres.what.equals("off") || pres.what.equals("on")) {
                datasetChanged();
            }
        }

        @Override
        public void onMetaSub(Subscription<VCard, String> sub) {
            if (sub.pub != null) {
                sub.pub.constructBitmap();
            }
        }

        @Override
        public void onMetaDesc(final Description<VCard, String> desc) {
        }

        @Override
        public void onSubsUpdated() {
            Log.d(TAG, "Subs Updated");
            datasetChanged();
        }
    }

    public class PagerAdapter extends FragmentStatePagerAdapter {
        int mNumOfTabs;
        Fragment mChatList;
        Fragment mContacts;

        public PagerAdapter(FragmentManager fm, int numTabs) {
            super(fm);
            mNumOfTabs = numTabs;
        }

        @Override
        public Fragment getItem(int position) {
            switch (position) {
                case 0:
                    if (mChatList == null) {
                        mChatList = new ChatListFragment();
                    }
                    return mChatList;
                case 1:
                    if (mContacts == null) {
                        mContacts = new ContactsFragment();
                    }
                    return mContacts;
                default:
                    return null;
            }
        }

        @Override
        public int getCount() {
            return mNumOfTabs;
        }
    }
}