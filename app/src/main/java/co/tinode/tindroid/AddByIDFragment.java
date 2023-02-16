package co.tinode.tindroid;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.Image;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.ViewFlipper;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.fragment.app.Fragment;

import io.nayuki.qrcodegen.QrCode;

public class AddByIDFragment extends Fragment {
    private final static String TAG = "AddByIDFragment";

    private static final int FRAME_QRCODE = 0;
    private static final int FRAME_CAMERA = 1;

    private static final int QRCODE_SCALE = 10;
    private static final int QRCODE_BORDER = 4;

    private static final int QRCODE_FG_COLOR = Color.BLACK;
    private static final int QRCODE_BG_COLOR = Color.WHITE;

    private final ExecutorService mQRCodeAnalysisExecutor = Executors.newSingleThreadExecutor();
    private final BarcodeScannerOptions mBarcodeScannerOptions =
            new BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build();

    private boolean mIsCameraActive = false;
    private boolean mIsScanning = false;

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
                    goToTopic(activity, id);
                }
            }
        });

        final String myID = Cache.getTinode().getMyId();
        final ViewFlipper qrFrame = view.findViewById(R.id.qrFrame);
        final ImageView qrCodeImageView = view.findViewById(R.id.qrCodeImageView);
        generateQRCode(qrCodeImageView, "tinode:topic/" + myID);

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

        displayCodeButton.setOnClickListener(button -> {
            if (qrFrame.getDisplayedChild() == FRAME_QRCODE) {
                return;
            }
            caption.setText(R.string.my_code);
            displayCodeButton.setSelected(true);
            scanCodeButton.setSelected(false);
            qrFrame.setDisplayedChild(FRAME_QRCODE);
            mIsCameraActive = false;
        });
        scanCodeButton.setOnClickListener(button -> {
            if (qrFrame.getDisplayedChild() == FRAME_CAMERA) {
                return;
            }
            caption.setText(R.string.scan_code);
            displayCodeButton.setSelected(false);
            scanCodeButton.setSelected(true);
            qrFrame.setDisplayedChild(FRAME_CAMERA);
            startCamera(view.findViewById(R.id.cameraPreviewView));
        });
    }

    private void goToTopic(final Activity activity, String id) {
        Intent it = new Intent(activity, MessageActivity.class);
        it.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        it.putExtra(Const.INTENT_EXTRA_TOPIC, id);
        startActivity(it);
    }

    private void startCamera(PreviewView previewView) {
        if (mIsCameraActive) {
            return;
        }
        mIsCameraActive = true;

        Context context = requireContext();
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(requireContext());
        cameraProviderFuture.addListener(
                () -> {
                    try {
                        ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                        Preview.Builder builder = new Preview.Builder();
                        Preview previewUseCase = builder.build();
                        previewUseCase.setSurfaceProvider(previewView.getSurfaceProvider());
                        cameraProvider.unbindAll();
                        ImageAnalysis analysisUseCase = new ImageAnalysis.Builder().build();
                        analysisUseCase.setAnalyzer(mQRCodeAnalysisExecutor, this::scanBarcodes);
                        cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA,
                                previewUseCase, analysisUseCase);
                    } catch (ExecutionException | InterruptedException e) {
                        Log.e(TAG, "Unable to initialize camera", e);
                    }
                },
                ContextCompat.getMainExecutor(context));
    }

    @OptIn(markerClass = ExperimentalGetImage.class)
    private void scanBarcodes(final ImageProxy imageProxy) {
        Image mediaImage = imageProxy.getImage();
        if (mediaImage == null || mIsScanning || !mIsCameraActive) {
            imageProxy.close();
            return;
        }

        InputImage image = InputImage.fromMediaImage(mediaImage,
                imageProxy.getImageInfo().getRotationDegrees());
        BarcodeScanner scanner = BarcodeScanning.getClient(mBarcodeScannerOptions);
        mIsScanning = true;
        scanner.process(image)
                .addOnSuccessListener(barcodes -> {
                    imageProxy.close();
                    mIsScanning = false;
                    for (Barcode barcode: barcodes) {
                        String rawValue = barcode.getRawValue();
                        if (rawValue == null) {
                            return;
                        }
                        if (rawValue.startsWith("tinode:topic/")) {
                            String id = rawValue.substring("tinode:topic/".length());
                            if (!TextUtils.isEmpty(id)) {
                                goToTopic(requireActivity(), id);
                            }
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    imageProxy.close();
                    mIsScanning = false;
                    Log.w(TAG, "Scanner error", e);
                });
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
