package co.tinode.tindroid;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

public class SendToActivity extends AppCompatActivity implements FindFragment.ReadContactsPermissionChecker {
    private static final String TAG = "SendToActivity";

    // Limit the number of times permissions are requested per session.
    private boolean mReadContactsPermissionsAlreadyRequested = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Get intent, action and MIME type
        Intent intent = getIntent();
        String action = intent.getAction();
        String type = intent.getType();

        if (!Intent.ACTION_SEND.equals(action) || type == null ||
                intent.getParcelableExtra(Intent.EXTRA_STREAM) == null) {
            Log.d(TAG, "Unable to share this type of content");
            finish();
        }

        setContentView(R.layout.activity_send_to);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        final ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle(R.string.send_to);
            toolbar.setNavigationOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(SendToActivity.this, ChatsActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    startActivity(intent);
                    finish();
                }
            });
        }

        getSupportFragmentManager()
                .beginTransaction()
                .add(R.id.contentFragment, new FindFragment(new ContactSelectedListener()), "contacts")
                .commitAllowingStateLoss();
    }

    public boolean shouldRequestReadContactsPermission() {
        return !mReadContactsPermissionsAlreadyRequested;
    }

    public void setReadContactsPermissionRequested() {
        mReadContactsPermissionsAlreadyRequested = true;
    }

    private class ContactSelectedListener implements FindAdapter.ClickListener {
        @Override
        public void onClick(String topicName) {
            Intent initial = getIntent();
            String type = initial.getType();
            Uri uri = initial.getParcelableExtra(Intent.EXTRA_STREAM);
            Intent preview = new Intent(SendToActivity.this, MessageActivity.class);
            preview.setDataAndType(uri, type);
            preview.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            // See discussion here: https://github.com/tinode/tindroid/issues/39
            preview.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            preview.putExtra("topic", topicName);
            startActivity(preview);
            finish();
        }
    }
}
