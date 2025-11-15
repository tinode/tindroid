package co.tinode.tindroid;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import java.io.IOException;
import java.util.ArrayList;

import co.tinode.tindroid.account.Utils;
import co.tinode.tindroid.media.Wallpapers;

import co.tinode.tinodesdk.Tinode;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class WallpaperFragment extends Fragment {
    private final static String TAG = "WallpaperFragment";

    private static final String PATH = "/img/bkg";
    private static final String META_INDEX = "/index.json";

    private static final int TAB_PATTERN = 0;
    private static final int TAB_IMAGE = 1;

    private String mBaseUrl;
    private ViewPager2 mViewPager;
    private TabLayout mTabLayout;
    private Button mRestoreDefault;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mBaseUrl = Cache.getTinode().getHttpOrigin() + PATH;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        Activity activity = requireActivity();
        Toolbar toolbar = activity.findViewById(R.id.toolbar);
        toolbar.setTitle(R.string.change_wallpapers);

        return inflater.inflate(R.layout.fragment_wallpapers, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mViewPager = view.findViewById(R.id.viewPager);
        mTabLayout = view.findViewById(R.id.tabLayout);
        mRestoreDefault = view.findViewById(R.id.buttonRestoreDefault);

        mRestoreDefault.setOnClickListener(v -> {
            saveWallpaper(requireContext(), "", 0);
            mViewPager.setCurrentItem(TAB_PATTERN);
            mViewPager.post(() -> {
                ImagePagerAdapter adapter = (ImagePagerAdapter) mViewPager.getAdapter();
                if (adapter != null) {
                    adapter.setSelectedItem(TAB_PATTERN, "");
                }
            });
        });
        fetchImageData();
    }

    @Override
    public void onResume() {
        super.onResume();
        syncSelectionFromPrefs();
    }

    // Called by child pages to enforce single-selection across tabs.
    void onGridItemSelected(int tab, String name) {
        ImagePagerAdapter adapter = (ImagePagerAdapter) mViewPager.getAdapter();
        if (adapter != null) {
            adapter.setSelectedItem(tab, name);
        }
    }

    private void fetchImageData() {
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(mBaseUrl + META_INDEX)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.w(TAG, "Failed to load wallpaper metadata", e);
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> showError());
                }
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        String json = response.body().string();
                        Wallpapers data = Tinode.getJsonMapper().readValue(json, Wallpapers.class);

                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> setupViewPager(data));
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to parse JSON", e);
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> showError());
                        }
                    }
                } else {
                    Log.e(TAG, "Unsuccessful response or null body: " + response.code() + " " + response.message());
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> showError());
                    }
                }
            }
        });
    }

    private void showError() {
        mViewPager.setVisibility(View.GONE);
        mTabLayout.setVisibility(View.GONE);
        mRestoreDefault.setVisibility(View.GONE);

        View v = getView();
        if (v == null) {
            return;
        }

        TextView errorView = v.findViewById(R.id.errorText);
        if (errorView != null) {
            errorView.setVisibility(View.VISIBLE);
        }
    }

    private void setupViewPager(Wallpapers data) {
        ImagePagerAdapter adapter = new ImagePagerAdapter(this, data, mBaseUrl);
        mViewPager.setAdapter(adapter);

        new TabLayoutMediator(mTabLayout, mViewPager,
                (tab, position) -> tab.setText(
                        position == TAB_PATTERN ?
                            getString(R.string.wp_pattern) :
                            getString(R.string.wp_image))).attach();

    }

    private void syncSelectionFromPrefs() {
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(requireContext());
        String name = pref.getString(Utils.PREFS_WALLPAPER, "");
        int tab = TextUtils.isEmpty(name) ? TAB_PATTERN :
                pref.getInt(Utils.PREFS_WALLPAPER_SIZE, 0) > 0 ? TAB_PATTERN : TAB_IMAGE;

        // Activate tab with selected wallpaper or pattern.
        mViewPager.setCurrentItem(tab, false);
        // Select appropriate item in the tab.
        ImagePagerAdapter adapter = (ImagePagerAdapter) mViewPager.getAdapter();
        if (adapter != null) {
            Log.i(TAG, "Setting selected item from PREFS: " + name + " in tab " + tab);
            adapter.setSelectedItem(tab, name);
        }
    }

    private static void saveWallpaper(Context context, String name, int size) {
        SharedPreferences.Editor pref = PreferenceManager.getDefaultSharedPreferences(context).edit();
        pref.putString(Utils.PREFS_WALLPAPER, name);
        pref.putInt(Utils.PREFS_WALLPAPER_SIZE, size);
        pref.apply();
    }

    private static class ImagePagerAdapter extends FragmentStateAdapter {
        private static final int PAGE_COUNT = 2;

        private final Wallpapers mData;
        private final String mBaseUrl;
        private final ImageGridTabFragment[] mPages = new ImageGridTabFragment[PAGE_COUNT];
        private final String[] mSelectedItems = new String[]{null, null};

        ImagePagerAdapter(@NonNull Fragment fragment, Wallpapers data, String baseUrl) {
            super(fragment);
            mData = data;
            mBaseUrl = baseUrl;
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            ImageGridTabFragment f = position == TAB_PATTERN ?
                    ImageGridTabFragment.newInstance(mBaseUrl, getWPaperPatterns(), position) :
                    ImageGridTabFragment.newInstance(mBaseUrl, getWPaperImages(), position);
            mPages[position] = f;
            f.setSelectedItem(mSelectedItems[position]);
            return f;
        }

        @Override
        public int getItemCount() {
            return PAGE_COUNT;
        }

        // New API to set selection on a specific page.
        public void setSelectedItem(int tab, String name) {
            mSelectedItems[tab] = name;
            ImageGridTabFragment f = mPages[tab];
            if (f != null) {
                f.setSelectedItem(name);
            }
            // Deselect the image on the other tab.
            f = mPages[1 - tab];
            if (f != null) {
                f.setSelectedItem(null);
            }
        }

        private ArrayList<Wallpapers.WPaper> getWPaperPatterns() {
            ArrayList<Wallpapers.WPaper> wPapers = new ArrayList<>();
            if (mData != null && mData.patterns != null) {
                for (Wallpapers.WPaper p : mData.patterns) {
                    Wallpapers.WPaper wp = new Wallpapers.WPaper();
                    wp.name = p.name;
                    wp.size = p.size;
                    wPapers.add(wp);
                }
            }
            return wPapers;
        }

        private ArrayList<Wallpapers.WPaper> getWPaperImages() {
            ArrayList<Wallpapers.WPaper> wPapers = new ArrayList<>();
            if (mData != null && mData.wallpapers != null) {
                for (Wallpapers.WPaper w : mData.wallpapers) {
                    Wallpapers.WPaper wp = new Wallpapers.WPaper();
                    wp.name = w.name;
                    wp.preview = w.preview;
                    wPapers.add(wp);
                }
            }
            return wPapers;
        }
    }

    public static class ImageGridTabFragment extends Fragment {
        private static final String TAG = "ImageGridTabFragment";

        private static final String ARG_URLS = "urls";
        private static final String ARG_INDEX = "index";
        private static final String ARG_BASE_URL = "baseUrl";

        @Nullable private String mPendingSelection;
        @Nullable private WallpaperAdapter mAdapter;

        static ImageGridTabFragment newInstance(String baseUrl, ArrayList<Wallpapers.WPaper> wPapers, int index) {
            ImageGridTabFragment fragment = new ImageGridTabFragment();
            Bundle args = new Bundle();
            args.putString(ARG_BASE_URL, baseUrl);
            args.putParcelableArrayList(ARG_URLS, wPapers);
            args.putInt(ARG_INDEX, index);
            fragment.setArguments(args);
            return fragment;
        }

        @Nullable
        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                                 @Nullable Bundle savedInstanceState) {
            return inflater.inflate(R.layout.wallpaper_grid, container, false);
        }

        @Override
        public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);

            RecyclerView recyclerView = view.findViewById(R.id.recyclerView);
            recyclerView.setLayoutManager(new GridLayoutManager(requireContext(), 3));
            recyclerView.setHasFixedSize(true);

            Bundle args = getArguments();
            final ArrayList<Wallpapers.WPaper> parcelList = args != null ?
                    args.getParcelableArrayList(ARG_URLS) : new ArrayList<>();
            final int tabIndex = args != null ? args.getInt(ARG_INDEX, 0) : 0;
            final String baseUrl = args != null ? args.getString(ARG_BASE_URL, "") : "";

            mAdapter = new WallpaperAdapter(parcelList, baseUrl,
                    (name, size) -> {
                Context context = requireContext();
                saveWallpaper(context, name, size);
                Fragment parent = getParentFragment();
                if (parent instanceof WallpaperFragment) {
                    ((WallpaperFragment) parent).onGridItemSelected(tabIndex, name);
                }
            });

            mAdapter.setSelectedItem(mPendingSelection);
            recyclerView.setAdapter(mAdapter);
        }

        @Override
        public void onDestroyView() {
            super.onDestroyView();
            // Avoid holding a stale adapter reference.
            mAdapter = null;
        }

        public void setSelectedItem(@Nullable String name) {
            if (mAdapter == null) {
                mPendingSelection = name;
                return;
            }
            mAdapter.setSelectedItem(name);
        }
    }
}

