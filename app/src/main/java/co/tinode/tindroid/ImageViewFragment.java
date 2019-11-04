package co.tinode.tindroid;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;


/**
 * Fragment for expanded display of an inline image.
 */

public class ImageViewFragment extends Fragment {

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_view_image, container, false);
    }


    @SuppressLint("SetTextI18n")
    @Override
    public void onResume() {
        super.onResume();

        Activity activity = getActivity();
        Bundle args = getArguments();
        if (activity == null || args == null) {
            return;
        }

        byte[] bits = args.getByteArray("image");
        if (bits != null) {
            Bitmap bmp = BitmapFactory.decodeByteArray(bits, 0, bits.length);
            String size = bmp.getWidth() + " \u00D7 " + bmp.getHeight() + "; ";
            ((ImageView) activity.findViewById(R.id.image)).setImageDrawable(new BitmapDrawable(getResources(), bmp));
            ((TextView) activity.findViewById(R.id.content_type)).setText(args.getString("mime"));
            ((TextView) activity.findViewById(R.id.file_name)).setText(args.getString("name"));
            ((TextView) activity.findViewById(R.id.image_size)).setText(size + UiUtils.bytesToHumanSize(bits.length));
        }
    }
}
