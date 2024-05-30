package co.tinode.tindroid;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import co.tinode.tindroid.db.BaseDb;

/**
 * Splash screen on startup
 */
public class InitRouterActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // No need to check for live connection here.

        // Send user to appropriate screen:
        // 1. If we have an account and no credential validation is needed, send to ChatsActivity.
        // 2. If we don't have an account or credential validation is required send to LoginActivity.
        Intent launch = new Intent(this, BaseDb.getInstance().isReady() ?
                ChatsActivity.class : LoginActivity.class);
        launch.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(launch);
        finish();
    }
}
