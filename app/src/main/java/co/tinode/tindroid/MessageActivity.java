package co.tinode.tindroid;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;

import java.io.IOException;

import co.tinode.tinodesdk.PromisedReply;
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

    private String mTopicName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_messages);

        final Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);

        mTopicName = getIntent().getStringExtra("topic");

        @SuppressWarnings("unchecked")
        Topic<VCard,String,String> topic =
                (Topic<VCard,String,String>) getTinode().getTopic(mTopicName);
        if (topic != null) {
            VCard desc = topic.getPublic();
            toolbar.setTitle(desc.fn);
        } else {
            topic = new Topic<>(getTinode(), mTopicName,
                    new Topic.Listener<VCard,String,String>() {

                @Override
                public void onData(MsgServerData data) {

                }

                @Override
                public void onPres(MsgServerPres pres) {

                }

                @Override
                public void onInfo(MsgServerInfo info) {

                }

                @Override
                public void onMeta(MsgServerMeta meta) {

                }

                @Override
                public void onMetaSub(Subscription sub) {

                }

                @Override
                public void onMetaDesc(final Description<VCard,String> desc) {
                    MessageActivity.this.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            toolbar.setTitle(desc.pub.fn);
                        }
                    });
                }

                @Override
                public void onSubsUpdated() {

                }
            });
            getTinode().registerTopic(topic);
        }

        if (!topic.isSubscribed()) {
            try {
                topic.subscribe();
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

    public String getTopicName() {
        return mTopicName;
    }
}
