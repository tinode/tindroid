package co.tinode.tindroid;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;


/**
 * Fragment for expanded display of an inline image.
 */

public class ImageViewFragment extends Fragment {

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_view_image, container, false);
    }

    @Override
    public void onResume() {
        super.onResume();

        Bundle bundle = getArguments();
        byte[] bits = bundle.getByteArray("image");
        if (bits != null) {
            Bitmap bmp = BitmapFactory.decodeByteArray(bits, 0, bits.length);
            ((ImageView) getActivity().findViewById(R.id.image)).setImageDrawable(new BitmapDrawable(getResources(), bmp));
        }
    }
}
