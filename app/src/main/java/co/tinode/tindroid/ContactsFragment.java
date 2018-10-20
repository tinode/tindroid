package co.tinode.tindroid;

import android.os.Bundle;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

/**
 * Pager fragment for Chats and Contacts fragments in ContactsActivity.
 */
public class ContactsFragment extends Fragment {
    private static final String TAG = "ContactsFragment";

    public static final int TAB_CHATS = 0;
    public static final int TAB_CONTACTS = 1;

    public ContactsFragment() {}

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(false);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        Log.d(TAG, "ContactsFragment.onCreateView");

        // Inflate the fragment layout
        View fragment = inflater.inflate(R.layout.fragment_contacts, container, false);

        ((AppCompatActivity) getActivity()).setSupportActionBar((Toolbar) fragment.findViewById(R.id.toolbar));

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

    public void selectTab(int pageIndex) {
        final TabLayout tabLayout = getActivity().findViewById(R.id.tabsContacts);
        final ViewPager viewPager = getActivity().findViewById(R.id.tabPager);
        if (tabLayout != null && viewPager != null) {
            tabLayout.setScrollPosition(pageIndex, 0f, true);
            viewPager.setCurrentItem(pageIndex);
        }
    }

    private class PagerAdapter extends FragmentStatePagerAdapter {
        int mNumOfTabs;
        Fragment mChatList;
        Fragment mContacts;

        PagerAdapter(FragmentManager fm, int numTabs) {
            super(fm);
            mNumOfTabs = numTabs;
        }

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
                    return null;
            }
        }

        @Override
        public int getCount() {
            return mNumOfTabs;
        }
    }
}
