package co.tinode.tindroid;

import android.app.Activity;
import android.os.Bundle;
import com.google.android.material.tabs.TabLayout;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;
import androidx.viewpager.widget.ViewPager;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

/**
 * Pager fragment for Chats and Contacts fragments in ContactsActivity.
 */
public class ContactsFragment extends Fragment {
    private static final String TAG = "ContactsFragment";

    private static final int TAB_CHATS = 0;
    static final int TAB_CONTACTS = 1;

    private ChatListFragment mChatList = null;
    private ContactListFragment mContacts = null;

    public ContactsFragment() {}

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(false);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        final Activity activity = getActivity();
        if (activity == null) {
            return null;
        }

        // Inflate the fragment layout
        View fragment = inflater.inflate(R.layout.fragment_contacts, container, false);

        ((AppCompatActivity) activity).setSupportActionBar((Toolbar) fragment.findViewById(R.id.toolbar));

        TabLayout tabLayout = fragment.findViewById(R.id.tabsContacts);
        tabLayout.setTabGravity(TabLayout.GRAVITY_FILL);

        final ViewPager viewPager = fragment.findViewById(R.id.tabPager);
        final PagerAdapter adapter = new PagerAdapter(getChildFragmentManager(), tabLayout.getTabCount());
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

        return fragment;
    }

    void selectTab(int pageIndex) {
        final Activity activity = getActivity();
        if (activity == null) {
            return;
        }

        final TabLayout tabLayout = activity.findViewById(R.id.tabsContacts);
        final ViewPager viewPager = activity.findViewById(R.id.tabPager);
        if (tabLayout != null && viewPager != null) {
            tabLayout.setScrollPosition(pageIndex, 0f, true);
            viewPager.setCurrentItem(pageIndex);
        }
    }

    void chatDatasetChanged() {
        if (mChatList != null && mChatList.isVisible()) {
            mChatList.datasetChanged();
        }
    }

    private class PagerAdapter extends FragmentStatePagerAdapter {
        int mNumOfTabs;

        PagerAdapter(FragmentManager fm, int numTabs) {
            super(fm);
            mNumOfTabs = numTabs;
        }

        @NonNull
        @Override
        public Fragment getItem(int position) {
            switch (position) {
                case TAB_CHATS:
                    if (mChatList == null) {
                        mChatList = new ChatListFragment();
                    }
                    return mChatList;
                case TAB_CONTACTS:
                    if (mContacts == null) {
                        mContacts = new ContactListFragment();
                    }
                    return mContacts;
                default:
                    throw new IllegalArgumentException("Unknown tab position " + position);
            }
        }

        @Override
        public int getCount() {
            return mNumOfTabs;
        }
    }
}
