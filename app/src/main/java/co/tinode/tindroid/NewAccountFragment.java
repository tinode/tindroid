package co.tinode.tindroid;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v4.app.Fragment;
import android.support.v7.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.IOException;

import static android.app.Activity.RESULT_OK;

/**
 * Fragment for managing registration of a new account.
 */
public class NewAccountFragment extends Fragment {
    private static final String TAG = "NewAccountFragment";
    private static final int SELECT_PICTURE = 1;
    private static final int BITMAP_SIZE = 128;

    public NewAccountFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        setHasOptionsMenu(false);

        ((AppCompatActivity) getActivity()).getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        // ((AppCompatActivity) getActivity()).getSupportActionBar().setDisplayShowHomeEnabled(true);

        return inflater.inflate(R.layout.fragment_newaccount, container, false);
    }

    @Override
    public void onActivityCreated(Bundle savedInstance) {
        super.onActivityCreated(savedInstance);

        // Get avatar from the gallery
        // TODO(gene): add support for taking a picture
        getActivity().findViewById(R.id.upload_avatar).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                uploadAvatar();
            }
        });
    }

    private void uploadAvatar() {
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(Intent.createChooser(intent, getString(R.string.select_image)),
                SELECT_PICTURE);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == SELECT_PICTURE && resultCode == RESULT_OK) {
            ImageView avatar = (ImageView) getActivity().findViewById(R.id.imageAvatar);
            try {
                Bitmap bmp = MediaStore.Images.Media.getBitmap(getActivity().getContentResolver(),
                        data.getData());
                int width = bmp.getWidth();
                int height = bmp.getHeight();
                if (width > height) {
                    width = width * BITMAP_SIZE / height;
                    height = BITMAP_SIZE;
                    // Sanity check
                    width = width > 1024 ? 1024 : width;
                } else {
                    height = height * BITMAP_SIZE / width;
                    width = BITMAP_SIZE;
                    height = height > 1024 ? 1024 : height;
                }
                // Scale down.
                bmp = Bitmap.createScaledBitmap(bmp, width, height, true);
                // Chop the square from the middle.
                bmp = Bitmap.createBitmap(bmp, width - BITMAP_SIZE, height - BITMAP_SIZE,
                        BITMAP_SIZE, BITMAP_SIZE);
                avatar.setImageBitmap(bmp);
            } catch (IOException ex) {
                Toast.makeText(getActivity(), getString(R.string.image_is_missing),
                        Toast.LENGTH_SHORT).show();
            }
        }
    }
}
