package co.tinode.android.ui;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

public class SendToActivity extends AppCompatActivity
        implements FindFragment.ReadContactsPermissionChecker {
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
            toolbar.setNavigationOnClickListener(v -> {
                Intent intent1 = new Intent(SendToActivity.this, ChatsActivity.class);
                intent1.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent1);
                finish();
            });
        }

        getSupportFragmentManager()
                .beginTransaction()
                .add(R.id.contentFragment, new FindFragment(), "contacts")
                .commitAllowingStateLoss();
    }

    public boolean shouldRequestReadContactsPermission() {
        return !mReadContactsPermissionsAlreadyRequested;
    }

    public void setReadContactsPermissionRequested() {
        mReadContactsPermissionsAlreadyRequested = true;
    }
}
