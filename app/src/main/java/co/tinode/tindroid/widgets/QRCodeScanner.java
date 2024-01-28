package co.tinode.tindroid.widgets;

import android.app.Activity;
import android.media.Image;
import android.text.TextUtils;
import android.util.Log;

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
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

public class QRCodeScanner {
    private final static String TAG = "QRCodeScanner";
    private final Activity mParent;
    private final String mPrefix;
    private final SuccessListener mSuccessListener;

    private final ExecutorService mQRCodeAnalysisExecutor = Executors.newSingleThreadExecutor();
    private final BarcodeScannerOptions mBarcodeScannerOptions =
            new BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build();
    private boolean mIsCameraActive = false;
    private boolean mIsScanning = false;
    ProcessCameraProvider mCameraProvider = null;

    public QRCodeScanner(@NonNull Activity context, String prefix, @NonNull SuccessListener listener) {
        mParent = context;
        mPrefix = prefix;
        mSuccessListener = listener;
    }

    public void startCamera(final LifecycleOwner lifecycleOwner, PreviewView previewView) {
        if (mIsCameraActive) {
            return;
        }

        mIsCameraActive = true;

        final ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(mParent);
        cameraProviderFuture.addListener(
                () -> {
                    try {
                        mCameraProvider = cameraProviderFuture.get();
                        Preview.Builder builder = new Preview.Builder();
                        Preview previewUseCase = builder.build();
                        previewUseCase.setSurfaceProvider(previewView.getSurfaceProvider());
                        mCameraProvider.unbindAll();
                        ImageAnalysis analysisUseCase = new ImageAnalysis.Builder().build();
                        analysisUseCase.setAnalyzer(mQRCodeAnalysisExecutor, this::scanBarcodes);
                        mCameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA,
                                previewUseCase, analysisUseCase);
                    } catch (ExecutionException | IllegalArgumentException | InterruptedException e) {
                        Log.e(TAG, "Unable to initialize camera", e);
                    }
                },
                ContextCompat.getMainExecutor(mParent));
    }

    public void stopCamera() {
        if (!mIsCameraActive) {
            return;
        }

        mIsCameraActive = false;
        if (mCameraProvider != null) {
            mCameraProvider.unbindAll();
        }
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
                        if (rawValue.startsWith(mPrefix)) {
                            String id = rawValue.substring(mPrefix.length());
                            if (!TextUtils.isEmpty(id)) {
                                mSuccessListener.onScanSuccessful(id);
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

    public interface SuccessListener {
        void onScanSuccessful(String id);
    }
}
