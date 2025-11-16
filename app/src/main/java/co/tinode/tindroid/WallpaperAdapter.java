package co.tinode.tindroid;

import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;

import org.jetbrains.annotations.Nullable;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import co.tinode.tindroid.media.Wallpapers;

import coil.Coil;
import coil.request.ImageRequest;
import coil.target.Target;

public class WallpaperAdapter extends RecyclerView.Adapter<WallpaperAdapter.VH> {
    private static final String TAG = "WallpaperAdapter";

    private final List<Wallpapers.WPaper> mWallpapers;
    private final OnImageClickListener mListener;
    private final String mBaseUrl;
    private int mSelectedPosition = RecyclerView.NO_POSITION;
    private final ColorMatrixColorFilter mInverter;

    public WallpaperAdapter(List<Wallpapers.WPaper> wallpapers, String baseUrl, OnImageClickListener listener) {
        mWallpapers = wallpapers;
        mBaseUrl = baseUrl;
        mListener = listener;

        ColorMatrix cm = new ColorMatrix();
        cm.set(new float[] {
                -1,  0,  0, 0, 255,  // R
                 0, -1,  0, 0, 255,  // G
                 0,  0, -1, 0, 255,  // B
                 0,  0,  0, 1,   0   // A
        });
        mInverter = new ColorMatrixColorFilter(cm);
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.wallpaper_grid_item, parent, false);
        return new VH(view, mBaseUrl, mInverter);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        Wallpapers.WPaper wp = mWallpapers.get(position);
        holder.bind(wp, position == mSelectedPosition, mListener);
    }

    @Override
    public int getItemCount() {
        return mWallpapers.size();
    }

    public interface OnImageClickListener {
        void onImageClick(String name, int size);
    }

    void setSelectedPosition(int position) {
        if (position == mSelectedPosition) {
            return;
        }
        int previousPosition = mSelectedPosition;
        mSelectedPosition = position;
        if (previousPosition != RecyclerView.NO_POSITION) {
            notifyItemChanged(previousPosition);
        }
        if (mSelectedPosition != RecyclerView.NO_POSITION) {
            notifyItemChanged(mSelectedPosition);
        }
    }

    // Set selected item by name.
    // If name is null, select nothing, if it's empty, select the first item.
    public void setSelectedItem(@Nullable String name) {
        if (name == null) {
            setSelectedPosition(RecyclerView.NO_POSITION);
        } else if (name.isEmpty()) {
            setSelectedPosition(0);
        } else {
            for (int i = 0; i < mWallpapers.size(); i++) {
                if (mWallpapers.get(i).name.equals(name)) {
                    setSelectedPosition(i);
                    return;
                }
            }
            setSelectedPosition(RecyclerView.NO_POSITION);
        }
    }

    static class VH extends RecyclerView.ViewHolder {
        final ImageView imageView;
        final ProgressBar progressBar;
        final View selectedOverlay;
        final ColorMatrixColorFilter inverter;
        final String baseUrl;

        VH(@NonNull View itemView, String baseUrl, ColorMatrixColorFilter cm) {
            super(itemView);
            inverter = cm;
            this.baseUrl = baseUrl;
            imageView = itemView.findViewById(R.id.imageView);
            imageView.setScaleType(ImageView.ScaleType.CENTER);
            progressBar = itemView.findViewById(R.id.progressBar);
            selectedOverlay = itemView.findViewById(R.id.selectedOverlay);
        }

        void bind(Wallpapers.WPaper wp, boolean isSelected, OnImageClickListener listener) {
            progressBar.setVisibility(View.VISIBLE);
            selectedOverlay.setVisibility(isSelected ? View.VISIBLE : View.GONE);

            ImageRequest.Builder builder = new ImageRequest.Builder(itemView.getContext())
                    .data(baseUrl + "/" + (TextUtils.isEmpty(wp.preview) ? wp.name : wp.preview));
            if (wp.size > 0) {
                builder = builder.size(UiUtils.dpToPx(itemView.getContext(), wp.size));
            }
            ImageRequest request = builder
                    .target(new Target() {
                        @Override
                        public void onStart(@Nullable Drawable placeholder) {
                            progressBar.setVisibility(View.VISIBLE);
                        }

                        @Override
                        public void onSuccess(@NonNull Drawable result) {
                            imageView.setImageDrawable(result);
                            if (UiUtils.isNightMode(imageView.getContext())) {
                                if (wp.size > 0) {
                                    // Invert pattern.
                                    applyInvert(imageView, inverter);
                                } else {
                                    // Darken the image view.
                                    imageView.setColorFilter(Color.rgb(192, 192, 192),
                                            PorterDuff.Mode.MULTIPLY);
                                }
                            } else {
                                imageView.setColorFilter(null);
                            }
                            progressBar.setVisibility(View.GONE);
                        }
                        @Override
                        public void onError(@Nullable Drawable error) {
                            progressBar.setVisibility(View.GONE);
                        }
                    }).build();

            Coil.imageLoader(itemView.getContext()).enqueue(request);

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    int position = getBindingAdapterPosition();
                    WallpaperAdapter adapter = (WallpaperAdapter) getBindingAdapter();
                    if (adapter != null) {
                        adapter.setSelectedPosition(position);
                    }
                    listener.onImageClick(wp.name, wp.size);
                }
            });
        }
    }

    // Invert image for night theme.
    private static void applyInvert(ImageView imageView, ColorMatrixColorFilter inverter) {
        imageView.setColorFilter(inverter);
    }
}

