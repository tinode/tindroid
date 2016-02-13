package co.tinode.tindroid;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.widget.Toast;

import java.io.IOError;
import java.io.IOException;

import co.tinode.tinodesdk.MeTopic;

/**
 * Created by gsokolov on 2/4/16.
 */
public class ContactsActivity extends AppCompatActivity {

    private static final String TAG = "ContactsActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_contacts);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        MeTopic<VCard,String,String> me = new MeTopic<>(InmemoryCache.getTinode(), String.class, null);
        InmemoryCache.getTinode().registerTopic(me);
        try {
            me.subscribe();
        } catch (IOException err) {
            Log.i(TAG, "connection failed :( " + err.getMessage());
            Toast.makeText(getApplicationContext(),
                    "Failed to login", Toast.LENGTH_LONG).show();
        }
    }
}