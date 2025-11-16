package co.tinode.tindroid;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.SeekBar;
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
import java.util.Date;
import java.util.List;

import co.tinode.tindroid.account.Utils;
import co.tinode.tindroid.media.Wallpapers;

import co.tinode.tindroid.widgets.BlurTransformation;
import co.tinode.tinodesdk.Tinode;

import coil.Coil;
import coil.request.ImageRequest;
import coil.target.Target;

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
    private ImageView mPreview;
    private int mBlur;

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

    @SuppressLint("SetTextI18n")
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mViewPager = view.findViewById(R.id.viewPager);
        mTabLayout = view.findViewById(R.id.tabLayout);
        mRestoreDefault = view.findViewById(R.id.buttonRestoreDefault);
        mPreview = view.findViewById(R.id.backgroundPreview);

        TextView loremIpsum = view.findViewById(R.id.messageText);
        loremIpsum.setText("Lorem ipsum dolor sit amet, consectetur adipiscing elit.");

        TextView dateDivider = view.findViewById(R.id.dateDivider);
        long now = System.currentTimeMillis();
        Date today = new Date();
        CharSequence date = DateUtils.getRelativeTimeSpanString(
                    now, today.getTime(), DateUtils.DAY_IN_MILLIS,
                    DateUtils.FORMAT_NUMERIC_DATE | DateUtils.FORMAT_SHOW_YEAR).toString().toUpperCase();
        dateDivider.setText(date);

        TextView meta = view.findViewById(R.id.messageMeta);
        meta.setText(UtilsString.timeOnly(requireContext(), today));

        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(requireContext());
        String name = pref.getString(Utils.PREFS_WALLPAPER, "");
        if (!TextUtils.isEmpty(name)) {
            assignBackgroundPreview(
                    name,
                    pref.getInt(Utils.PREFS_WALLPAPER_SIZE, 0),
                    pref.getInt(Utils.PREFS_WALLPAPER_BLUR, 0));
        }

        mRestoreDefault.setOnClickListener(v -> {
            saveWallpaper(requireContext(), "", 0, 0);
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
    void onGridItemSelected(int tab, String name, int size, int blur) {
        ImagePagerAdapter adapter = (ImagePagerAdapter) mViewPager.getAdapter();
        if (adapter != null) {
            adapter.setSelectedItem(tab, name);
        }
        assignBackgroundPreview(name, size, blur);
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
                Activity activity = getActivity();
                if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
                    return;
                }
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        String json = response.body().string();
                        Wallpapers data = Tinode.getJsonMapper().readValue(json, Wallpapers.class);
                        activity.runOnUiThread(() -> setupViewPager(data));
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to parse JSON", e);
                        activity.runOnUiThread(() -> showError());
                    }
                } else {
                    Log.e(TAG, "Unsuccessful response or null body: " + response.code() + " " + response.message());
                    activity.runOnUiThread(() -> showError());
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
        final String name = pref.getString(Utils.PREFS_WALLPAPER, "");
        final int size = pref.getInt(Utils.PREFS_WALLPAPER_SIZE, 0);
        int tab = TextUtils.isEmpty(name) ? TAB_PATTERN : size > 0 ? TAB_PATTERN : TAB_IMAGE;
        mBlur = pref.getInt(Utils.PREFS_WALLPAPER_BLUR, 0);

        // Activate tab with selected wallpaper or pattern.
        mViewPager.setCurrentItem(tab, false);

        mViewPager.post(() -> {
            // Select appropriate item in the tab.
            ImagePagerAdapter adapter = (ImagePagerAdapter) mViewPager.getAdapter();
            if (adapter == null) {
                return;
            }

            adapter.setSelectedItem(tab, name);
            adapter.setBlur(mBlur);

            String previewName = name;
            if (TextUtils.isEmpty(name)) {
                List<Wallpapers.WPaper> patterns = adapter.getWPaperPatterns();
                if (!patterns.isEmpty()) {
                    previewName = patterns.get(0).name;
                }
            }

            // Load and set background image using Coil
            if (!TextUtils.isEmpty(previewName)) {
                assignBackgroundPreview(previewName, size, mBlur);
            } else {
                // Clear background if no wallpaper is selected.
                mPreview.setBackground(null);
            }
        });
    }

    private void assignBackgroundPreview(final String name, final int size, final int blur) {
        Log.i(TAG, "Assigning background preview: " + name + ", size: " + size + ", blur: " + blur);
        Context context = requireContext();
        ImageRequest.Builder builder = new ImageRequest.Builder(context)
                .data(mBaseUrl + "/" + name);
        if (size > 0) {
            builder.size(UiUtils.dpToPx(context, size));
        } else if (blur > 0 && blur <= 5) {
            Log.i(TAG, "Applying blur: " + blur);
            float[] radius = new float[]{0, 1, 2, 4, 8, 16};
            builder.transformations(new BlurTransformation(context, radius[blur]));
        }
        ImageRequest request = builder.target(new Target() {
                    @Override
                    public void onSuccess(@NonNull Drawable result) {
                        if (result instanceof BitmapDrawable) {
                            if (size > 0) {
                                ((BitmapDrawable) result).setTileModeXY(Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
                            } else {
                                // TODO: Apply blur.
                                ((BitmapDrawable) result).setGravity(Gravity.CENTER);
                            }
                        }
                        mPreview.setImageDrawable(result);
                    }

                    @Override
                    public void onError(@Nullable Drawable error) {
                        mPreview.setImageDrawable(null);
                    }
                })
                .build();
        Coil.imageLoader(requireContext()).enqueue(request);
    }

    private static void saveWallpaper(Context context, String name, int size, int blur) {
        SharedPreferences.Editor pref = PreferenceManager.getDefaultSharedPreferences(context).edit();
        pref.putString(Utils.PREFS_WALLPAPER, name);
        pref.putInt(Utils.PREFS_WALLPAPER_SIZE, size);
        pref.putInt(Utils.PREFS_WALLPAPER_BLUR, blur);
        pref.apply();
        Log.i(TAG, "Saved wallpaper: " + name + ", size: " + size + ", blur: " + blur);
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

        // Set selection on one page and deselect on the other.
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

        public void setBlur(int blur) {
            for (ImageGridTabFragment f : mPages) {
                if (f != null) {
                    f.setBlur(blur);
                }
            }
        }

        List<Wallpapers.WPaper> getWPaperPatterns() {
            return mData.patterns != null ? mData.patterns : new ArrayList<>();
        }

        List<Wallpapers.WPaper> getWPaperImages() {
            return mData.wallpapers != null ? mData.wallpapers : new ArrayList<>();
        }
    }

    public static class ImageGridTabFragment extends Fragment {
        private static final String TAG = "ImageGridTabFragment";

        private static final String ARG_URLS = "urls";
        private static final String ARG_INDEX = "index";
        private static final String ARG_BASE_URL = "baseUrl";

        @Nullable private String mPendingSelection;
        @Nullable private WallpaperAdapter mAdapter;

        private String mName = "";
        private int mSize = 0;
        private int mBlur = 0;

        static ImageGridTabFragment newInstance(String baseUrl,
                                                List<Wallpapers.WPaper> wPapers,
                                                int index) {
            ImageGridTabFragment fragment = new ImageGridTabFragment();
            Bundle args = new Bundle();
            args.putString(ARG_BASE_URL, baseUrl);
            args.putParcelableArrayList(ARG_URLS, new ArrayList<>(wPapers));
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
            final int tabIndex = args != null ? args.getInt(ARG_INDEX, TAB_PATTERN) : TAB_PATTERN;
            final String baseUrl = args != null ? args.getString(ARG_BASE_URL, "") : "";

            if (tabIndex == TAB_PATTERN) {
                view.findViewById(R.id.section_blur).setVisibility(View.GONE);
            } else {
                view.findViewById(R.id.section_blur).setVisibility(View.VISIBLE);
                SeekBar seekBar = view.findViewById(R.id.blurWallpaper);
                seekBar.setProgress(mBlur);
                seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        mBlur = progress;
                        Context context = requireContext();
                        saveWallpaper(context, mName, mSize, mBlur);
                        Fragment parent = getParentFragment();
                        if (parent instanceof WallpaperFragment) {
                            ((WallpaperFragment) parent).onGridItemSelected(tabIndex, mName, mSize, mBlur);
                        }
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {
                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {
                    }
                });
            }

            mAdapter = new WallpaperAdapter(parcelList, baseUrl,
                    (name, size) -> {
                Context context = requireContext();
                mName = name;
                mSize = size;
                saveWallpaper(context, mName, mSize, mBlur);
                Fragment parent = getParentFragment();
                if (parent instanceof WallpaperFragment) {
                    ((WallpaperFragment) parent).onGridItemSelected(tabIndex, mName, mSize, mBlur);
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
            mName = name;
            if (mAdapter == null) {
                mPendingSelection = name;
                return;
            }
            mAdapter.setSelectedItem(name);
        }

        public void setBlur(int blur) {
            mBlur = blur;
            View view = getView();
            if (view == null) {
                return;
            }
            SeekBar seekBar = view.findViewById(R.id.blurWallpaper);
            seekBar.setProgress(mBlur);
        }
    }
}

