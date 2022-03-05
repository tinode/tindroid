package co.tinode.tinui;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

public class StartChatFragment extends Fragment {
    private static final int COUNT_OF_TABS = 3;
    private static final int TAB_SEARCH = 0;
    private static final int TAB_NEW_GROUP = 1;
    private static final int TAB_BY_ID = 2;

    private static final int[] TAB_NAMES = new int[] {R.string.find, R.string.group, R.string.by_id};

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.tinui_fragment_create, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstance) {
        final FragmentActivity activity = getActivity();
        if (activity == null) {
            return;
        }

        int initialTab = 0;
        if (savedInstance != null) {
            initialTab = savedInstance.getInt("activeTab");
        }

        final TabLayout tabLayout = view.findViewById(R.id.tabsCreationOptions);
        final ViewPager2 viewPager = view.findViewById(R.id.tabPager);
        viewPager.setAdapter(new PagerAdapter(activity));
        // This has no effect. It looks like an Android bug.
        viewPager.setCurrentItem(initialTab, false);
        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> tab.setText(TAB_NAMES[position])).attach();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);

        Log.w("StartChatFragment", "onSaveInstanceState");

        final FragmentActivity activity = getActivity();
        if (activity == null) {
            return;
        }

        final TabLayout tabLayout = activity.findViewById(R.id.tabsCreationOptions);
        if (tabLayout != null) {
            outState.putInt("activeTab", tabLayout.getSelectedTabPosition());
        }
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
