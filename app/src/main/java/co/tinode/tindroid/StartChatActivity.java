package co.tinode.tindroid;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

/**
 * Starting a new chat.
 */
public class StartChatActivity extends AppCompatActivity
        implements FindFragment.ReadContactsPermissionChecker, ImageViewFragment.AvatarCompletionHandler {

    private static final int COUNT_OF_TABS = 3;
    private static final int TAB_SEARCH = 0;
    private static final int TAB_NEW_GROUP = 1;
    private static final int TAB_BY_ID = 2;

    static final String FRAGMENT_TABS = "tabs";
    static final String FRAGMENT_AVATAR_PREVIEW = "avatar_preview";

    private static final int[] TAB_NAMES = new int[] {R.string.find, R.string.group, R.string.by_id};

    // Limit the number of times permissions are requested per session.
    private boolean mReadContactsPermissionsAlreadyRequested = false;

    private AvatarViewModel mAvatarVM;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create);

        // Add the default fragment.
        showFragment(FRAGMENT_TABS, null, false);

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

        final TabLayout tabLayout = findViewById(R.id.tabsCreationOptions);
        final ViewPager2 viewPager = findViewById(R.id.tabPager);
        viewPager.setAdapter(new PagerAdapter(this));
        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> tab.setText(TAB_NAMES[position])).attach();

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

    void showFragment(String tag, Bundle args, Boolean addToBackstack) {
        if (isFinishing() || isDestroyed()) {
            return;
        }

        FragmentManager fm = getSupportFragmentManager();
        Fragment fragment = fm.findFragmentByTag(tag);
        if (fragment == null) {
            switch (tag) {
                case FRAGMENT_TABS:
                    fragment = new LoginFragment();
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


    private static class PagerAdapter extends FragmentStateAdapter {
        PagerAdapter(FragmentActivity fa) {
            super(fa);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            switch (position) {
                case TAB_SEARCH:
                    return new FindFragment();
                case TAB_NEW_GROUP:
                    return new CreateGroupFragment();
                case TAB_BY_ID:
                    return new AddByIDFragment();
                default:
                    throw new IllegalArgumentException("Invalid TAB position " + position);
            }
        }

        @Override
        public int getItemCount() {
            return COUNT_OF_TABS;
        }
    }
}
