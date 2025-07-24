package co.tinode.tindroid;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProvider;

/**
 * Starting a new chat.
 */
public class StartChatActivity extends AppCompatActivity
        implements FindFragment.ReadContactsPermissionChecker,
        ImageViewFragment.AvatarCompletionHandler {

    static final String FRAGMENT_TABS = "tabs";
    static final String FRAGMENT_AVATAR_PREVIEW = "avatar_preview";

    // Limit the number of times permissions are requested per session.
    private boolean mReadContactsPermissionsAlreadyRequested = false;

    private AvatarViewModel mAvatarVM;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        UiUtils.setupSystemToolbar(this);

        setContentView(R.layout.activity_create);

        Toolbar toolbar = findViewById(R.id.toolbar);

        setSupportActionBar(toolbar);
        final ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle(R.string.action_new_chat);

            toolbar.setNavigationOnClickListener(v -> {
                Intent intent = new Intent(StartChatActivity.this, ChatsActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
                finish();
            });
        }

        // Add the default fragment.
        showFragment(FRAGMENT_TABS, null, false);

        // Initialize View Model to store avatar bitmap before it's sent to the server.
        mAvatarVM = new ViewModelProvider(this).get(AvatarViewModel.class);
    }

    public boolean shouldRequestReadContactsPermission() {
        return !mReadContactsPermissionsAlreadyRequested;
    }

    public void setReadContactsPermissionRequested() {
        mReadContactsPermissionsAlreadyRequested = true;
    }

    @Override
    public void onAcceptAvatar(String topicName, Bitmap avatar) {
        if (isDestroyed() || isFinishing()) {
            return;
        }

        mAvatarVM.setAvatar(avatar);
    }
    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        // This is needed because otherwise onSaveInstanceState is not called for fragments.
        super.onSaveInstanceState(outState);
    }

    void showFragment(String tag, Bundle args, Boolean addToBackstack) {
        if (isFinishing() || isDestroyed()) {
            return;
        }

        FragmentManager fm = getSupportFragmentManager();
        Fragment fragment = fm.findFragmentByTag(tag);
        if (fragment == null) {
            switch (tag) {
                case FRAGMENT_TABS:
                    fragment = new StartChatFragment();
                    break;
                case FRAGMENT_AVATAR_PREVIEW:
                    fragment = new ImageViewFragment();
                    if (args == null) {
                        args = new Bundle();
                    }
                    args.putBoolean(AttachmentHandler.ARG_AVATAR, true);
                    break;
                default:
                    throw new IllegalArgumentException();
            }
        }

        if (fragment.getArguments() != null) {
            fragment.getArguments().putAll(args);
        } else {
            fragment.setArguments(args);
        }

        FragmentTransaction tx = fm.beginTransaction()
                .replace(R.id.contentFragment, fragment)
                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
        if (addToBackstack) {
            tx = tx.addToBackStack(null);
        }
        tx.commitAllowingStateLoss();
    }
}
