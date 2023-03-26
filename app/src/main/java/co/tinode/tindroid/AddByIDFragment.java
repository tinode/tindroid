package co.tinode.tindroid;

import android.Manifest;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.ViewFlipper;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.camera.view.PreviewView;
import androidx.core.content.res.ResourcesCompat;
import androidx.fragment.app.Fragment;

import co.tinode.tindroid.widgets.QRCodeScanner;
import io.nayuki.qrcodegen.QrCode;

public class AddByIDFragment extends Fragment {
    private static final String TOPIC_URI_PREFIX = "tinode:topic/";
    private static final int FRAME_QRCODE = 0;
    private static final int FRAME_CAMERA = 1;

    private static final int QRCODE_SCALE = 10;
    private static final int QRCODE_BORDER = 1;

    private static final int QRCODE_FG_COLOR = Color.BLACK;
    private static final int QRCODE_BG_COLOR = Color.WHITE;

    private QRCodeScanner mQrScanner = null;

    private PreviewView mCameraPreview;

    private final ActivityResultLauncher<String> mRequestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                // Check if permission is granted.
                if (isGranted) {
                    mQrScanner.startCamera(AddByIDFragment.this, mCameraPreview);
                }
            });

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_add_by_id, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        final AppCompatActivity activity = (AppCompatActivity) requireActivity();

        view.findViewById(R.id.confirm).setOnClickListener(confirm -> {
            TextView editor = activity.findViewById(R.id.editId);
            if (editor != null) {
                String id = editor.getText().toString();
                if (TextUtils.isEmpty(id)) {
                    editor.setError(getString(R.string.id_required));
                } else {
                    goToTopic(id);
                }
            }
        });

        final String myID = Cache.getTinode().getMyId();
        final ViewFlipper qrFrame = view.findViewById(R.id.qrFrame);
        final ImageView qrCodeImageView = view.findViewById(R.id.qrCodeImageView);
        generateQRCode(qrCodeImageView, TOPIC_URI_PREFIX + myID);

        ColorStateList buttonColor = new ColorStateList(
                new int[][]{
                        new int[]{android.R.attr.state_selected},
                        new int[]{}
                },
                new int[] {
                        ResourcesCompat.getColor(activity.getResources(), R.color.button_background, null),
                        ResourcesCompat.getColor(activity.getResources(), R.color.colorButtonNormal, null)
                }
        );
        // QR Code generation and scanning.
        final AppCompatImageButton displayCodeButton = view.findViewById(R.id.displayCode);
        displayCodeButton.setBackgroundTintList(buttonColor);
        displayCodeButton.setSelected(true);
        final AppCompatImageButton scanCodeButton = view.findViewById(R.id.scanCode);
        scanCodeButton.setBackgroundTintList(buttonColor);
        scanCodeButton.setSelected(false);
        final TextView caption = view.findViewById(R.id.caption);
        caption.setText(R.string.my_code);

        mCameraPreview = view.findViewById(R.id.cameraPreviewView);
        displayCodeButton.setOnClickListener(button -> {
            if (qrFrame.getDisplayedChild() == FRAME_QRCODE) {
                return;
            }
            caption.setText(R.string.my_code);
            displayCodeButton.setSelected(true);
            scanCodeButton.setSelected(false);
            qrFrame.setDisplayedChild(FRAME_QRCODE);
            if (mQrScanner != null) {
                mQrScanner.stopCamera();
            }
        });
        scanCodeButton.setOnClickListener(button -> {
            if (qrFrame.getDisplayedChild() == FRAME_CAMERA) {
                return;
            }
            caption.setText(R.string.scan_code);
            displayCodeButton.setSelected(false);
            scanCodeButton.setSelected(true);
            qrFrame.setDisplayedChild(FRAME_CAMERA);

            if (mQrScanner == null) {
                mQrScanner = new QRCodeScanner(activity, TOPIC_URI_PREFIX, this::goToTopic);
            }

            if (!UiUtils.isPermissionGranted(activity, Manifest.permission.CAMERA)) {
                mRequestPermissionLauncher.launch(Manifest.permission.CAMERA);
                return;
            }

            mQrScanner.startCamera(this, mCameraPreview);
        });
    }

    private void goToTopic(String id) {
        Intent it = new Intent(requireActivity(), MessageActivity.class);
        it.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        it.putExtra(Const.INTENT_EXTRA_TOPIC, id);
        startActivity(it);
    }

    private void generateQRCode(ImageView view, String uri) {
        QrCode qr = QrCode.encodeText(uri, QrCode.Ecc.LOW);
        view.setImageBitmap(toImage(qr));
    }

    private static Bitmap toImage(QrCode qr) {
        Bitmap result = Bitmap.createBitmap((qr.size + QRCODE_BORDER * 2) * QRCODE_SCALE,
            (qr.size + QRCODE_BORDER * 2) * QRCODE_SCALE, Bitmap.Config.ARGB_8888);
        for (int y = 0; y < result.getHeight(); y++) {
            for (int x = 0; x < result.getWidth(); x++) {
                boolean color = qr.getModule(x / QRCODE_SCALE - QRCODE_BORDER, y / QRCODE_SCALE - QRCODE_BORDER);
                result.setPixel(x, y, color ? QRCODE_FG_COLOR : QRCODE_BG_COLOR);
            }
        }
        return result;
    }
}
