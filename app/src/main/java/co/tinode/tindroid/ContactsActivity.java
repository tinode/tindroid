package co.tinode.tindroid;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.widget.Toast;

import java.io.IOException;
import java.util.ArrayList;

import co.tinode.tinodesdk.MeTopic;
import co.tinode.tinodesdk.Topic;
import co.tinode.tinodesdk.model.Description;
import co.tinode.tinodesdk.model.Invitation;
import co.tinode.tinodesdk.model.MsgServerData;
import co.tinode.tinodesdk.model.MsgServerInfo;
import co.tinode.tinodesdk.model.MsgServerMeta;
import co.tinode.tinodesdk.model.MsgServerPres;
import co.tinode.tinodesdk.model.Subscription;

/**
 * Display user's list of contacts
 */

public class ContactsActivity extends AppCompatActivity {

    private static final String TAG = "ContactsActivity";

    protected ContactsListAdapter mContactsAdapter;
    protected ArrayList<String> mContactIndex;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_contacts);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        mContactIndex = new ArrayList<>();
        mContactsAdapter = new ContactsListAdapter(this, mContactIndex);

        MeTopic<VCard,String,String> me = new MeTopic<>(InmemoryCache.getTinode(),
                new Topic.Listener<VCard, String, Invitation<String>>() {
            @Override
            public void onSubscribe(int code, String text) {

            }

            @Override
            public void onLeave(int code, String text) {

            }

            @Override
            public void onData(MsgServerData<Invitation<String>> data) {

            }

            @Override
            public void onPres(MsgServerPres pres) {

            }

            @Override
            public void onInfo(MsgServerInfo info) {

            }

            @Override
            public void onMeta(MsgServerMeta<VCard, String> meta) {

            }

            @Override
            public void onMetaSub(Subscription<VCard, String> sub) {
                sub.pub.constructBitmap();
                mContactIndex.add(sub.topic);
            }

            @Override
            public void onMetaDesc(Description<VCard, String> desc) {

            }

            @Override
            public void onSubsUpdated() {
                ContactsActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mContactsAdapter.notifyDataSetChanged();
                    }
                });
            }
        });
        // Public, Private, Info in Invite<Info>
        me.setTypes(VCard.class, String.class, String.class);
        InmemoryCache.getTinode().registerTopic(me);
        try {
            me.subscribe();
        } catch (IOException err) {
            Log.i(TAG, "connection failed :( " + err.getMessage());
            Toast.makeText(getApplicationContext(),
                    "Failed to login", Toast.LENGTH_LONG).show();
        }
    }

    protected ContactsListAdapter getContactsAdapter() {
        return mContactsAdapter;
    }

    @SuppressWarnings("unchecked")
    protected Subscription<VCard,String> getContactByPos(int pos) {
        return (Subscription<VCard,String>) InmemoryCache.getTinode().getMeTopic()
                .getSubscription(mContactIndex.get(pos));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_acc_settings, menu);
        return true;
    }
}