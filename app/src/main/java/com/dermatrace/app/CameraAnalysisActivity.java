package com.dermatrace.app;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.dermatrace.app.ml.DermaTraceModelManager;
import com.dermatrace.app.model.AnalysisResult;
import com.dermatrace.app.util.AppPreferences;
import com.dermatrace.app.util.BitmapUtils;
import com.dermatrace.app.util.FileUtils;
import com.dermatrace.app.util.UiUtils;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.google.firebase.auth.FirebaseAuth;

public class CameraAnalysisActivity extends AppCompatActivity {
    public static final String EXTRA_LESION_ID = "lesion_id";
    public static final String EXTRA_PREVIOUS_IMAGE_PATH = "previous_image_path";
    public static final String EXTRA_EXISTING_IMAGE_PATH = "existing_image_path";
    // A new lesion is only created after a successful scan (no empty lesions after retakes or failures).
    public static final String EXTRA_NEW_PATIENT_ID = "new_patient_id";
    public static final String EXTRA_NEW_LESION_LABEL = "new_lesion_label";
    public static final String EXTRA_NEW_LESION_BODY = "new_lesion_body";
    public static final String EXTRA_NEW_LESION_NOTES = "new_lesion_notes";

    private PreviewView previewView;
    private MaterialButton captureButton, importButton, ghostButton, flipButton;
    private TextView statusText;
    private CircularProgressIndicator progress;
    private ImageView ghostOverlay;

    private ImageCapture imageCapture;
    private ProcessCameraProvider cameraProvider;
    private CameraSelector currentSelector = CameraSelector.DEFAULT_BACK_CAMERA;
    private ExecutorService executor;
    private DermaTraceModelManager modelManager;
    private boolean cameraReady, modelReady, inferenceRunning, ghostVisible = true;
    private long lesionId = -1;
    private String pendingExistingPath;

    private final ActivityResultLauncher<String> cameraPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) startCamera();
                else {
                    Toast.makeText(this, "Camera permission was denied. You can still choose an existing image.", Toast.LENGTH_LONG).show();
                    updateEnabled();
                }
            });

    private final ActivityResultLauncher<String[]> imagePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), uri -> { if (uri != null) analyzeImportedImage(uri); });

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            Intent login = new Intent(this, LoginActivity.class);
            login.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                    Intent.FLAG_ACTIVITY_CLEAR_TASK |
                    Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(login);
            finish();
            return;
        }

        setContentView(R.layout.activity_camera_analysis);
        UiUtils.applySystemBars(this, findViewById(R.id.cameraRoot));

        lesionId = getIntent().getLongExtra(EXTRA_LESION_ID, -1);
        String previousPath = getIntent().getStringExtra(EXTRA_PREVIOUS_IMAGE_PATH);
        pendingExistingPath = getIntent().getStringExtra(EXTRA_EXISTING_IMAGE_PATH);

        previewView = findViewById(R.id.previewView);
        captureButton = findViewById(R.id.btnCapture);
        importButton = findViewById(R.id.btnImportImage);
        ghostButton = findViewById(R.id.btnGhostToggle);
        ghostOverlay = findViewById(R.id.imgGhostOverlay);
        flipButton = findViewById(R.id.btnFlipCamera);
        statusText = findViewById(R.id.txtCameraStatus);
        progress = findViewById(R.id.progressInference);

        if (previousPath != null && !previousPath.isEmpty()) {
            ghostOverlay.setImageBitmap(com.dermatrace.app.util.BitmapUtils.loadSampledOriented(previousPath, 1280));
            ghostOverlay.setVisibility(View.VISIBLE);
            ghostButton.setVisibility(View.VISIBLE);
            ghostButton.setOnClickListener(v -> {
                ghostVisible = !ghostVisible;
                ghostOverlay.setVisibility(ghostVisible ? View.VISIBLE : View.GONE);
                ghostButton.setText(ghostVisible ? "Hide previous-photo guide" : "Show previous-photo guide");
            });
        } else {
            ghostOverlay.setVisibility(View.GONE);
            ghostButton.setVisibility(View.GONE);
        }

        // Normal file analysis is available from New Analysis. This is a convenient
        // second shortcut on the camera screen and uses the same save-to-history path.
        importButton.setVisibility(View.VISIBLE);
        importButton.setText("Gallery");

        executor = Executors.newSingleThreadExecutor();
        modelManager = new DermaTraceModelManager(this);
        captureButton.setEnabled(false);
        importButton.setEnabled(false);
        captureButton.setOnClickListener(v -> captureAndAnalyze());
        importButton.setOnClickListener(v -> imagePickerLauncher.launch(new String[]{"image/*"}));
        if (flipButton != null) flipButton.setOnClickListener(v -> flipCamera());

        modelManager.initialize(new DermaTraceModelManager.InitCallback() {
            @Override public void onReady() {
                modelReady = true;
                runOnUiThread(() -> {
                    if (pendingExistingPath != null && !pendingExistingPath.isEmpty()) {
                        statusText.setText("AI ready • analysing selected image…");
                        String path = pendingExistingPath;
                        pendingExistingPath = null;
                        executor.execute(() -> analyzePath(path));
                    } else {
                        statusText.setText(cameraReady ? "AI ready • Center the full lesion inside the guide" : "AI ready • Waiting for camera");
                        updateEnabled();
                    }
                });
            }
            @Override public void onError(Exception e) {
                runOnUiThread(() -> {
                    statusText.setText("AI initialization failed");
                    Toast.makeText(CameraAnalysisActivity.this, e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });

        // If we arrived with an already-selected file, camera permission is unnecessary.
        if (pendingExistingPath == null || pendingExistingPath.isEmpty()) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) startCamera();
            else cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                cameraProvider = future.get();
                bindCameraUseCases();
                cameraReady = true;
                statusText.setText(modelReady ? "AI ready • Center the full lesion inside the guide" : "Camera ready • Loading AI models…");
                updateEnabled();
            } catch (Exception e) {
                Toast.makeText(this, "Unable to start camera: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }


    private void bindCameraUseCases() {
        if (cameraProvider == null) return;
        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());
        imageCapture = new ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build();
        cameraProvider.unbindAll();
        cameraProvider.bindToLifecycle(this, currentSelector, preview, imageCapture);
    }

    private void flipCamera() {
        if (cameraProvider == null || inferenceRunning) return;
        currentSelector = currentSelector == CameraSelector.DEFAULT_BACK_CAMERA
                ? CameraSelector.DEFAULT_FRONT_CAMERA : CameraSelector.DEFAULT_BACK_CAMERA;
        try {
            bindCameraUseCases();
        } catch (Exception e) {
            currentSelector = CameraSelector.DEFAULT_BACK_CAMERA;
            try { bindCameraUseCases(); } catch (Exception ignored) {}
            Toast.makeText(this, "This camera is not available.", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateEnabled() {
        captureButton.setEnabled(cameraReady && modelReady && !inferenceRunning);
        importButton.setEnabled(modelReady && !inferenceRunning);
    }

    private void setRunning(boolean running, String message) {
        inferenceRunning = running;
        progress.setVisibility(running ? View.VISIBLE : View.GONE);
        if (message != null) statusText.setText(message);
        updateEnabled();
    }

    private void captureAndAnalyze() {
        if (imageCapture == null || !modelReady || inferenceRunning) return;
        setRunning(true, "Capturing image…");
        File dir = new File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "DermaTrace");
        if (!dir.exists()) dir.mkdirs();
        File file = new File(dir, "lesion_" + System.currentTimeMillis() + ".jpg");

        imageCapture.takePicture(new ImageCapture.OutputFileOptions.Builder(file).build(), executor,
                new ImageCapture.OnImageSavedCallback() {
                    @Override public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                        analyzePath(file.getAbsolutePath());
                    }
                    @Override public void onError(@NonNull ImageCaptureException exception) {
                        runOnUiThread(() -> {
                            setRunning(false, "Capture failed • Please retry");
                            Toast.makeText(CameraAnalysisActivity.this, exception.getMessage(), Toast.LENGTH_LONG).show();
                        });
                    }
                });
    }

    private void analyzeImportedImage(Uri uri) {
        setRunning(true, "Preparing selected image…");
        executor.execute(() -> {
            try {
                String copiedPath = FileUtils.copyUriToAppPictures(this, uri);
                analyzePath(copiedPath);
            } catch (Exception e) { showAnalysisError(e); }
        });
    }

    private void analyzePath(String path) {
        runOnUiThread(() -> setRunning(true, "Running on-device AI analysis…"));
        try {
            Bitmap bitmap = BitmapUtils.loadCorrectlyOriented(path);
            AnalysisResult result = modelManager.analyze(bitmap);
            runOnUiThread(() -> {
                setRunning(false, "Analysis complete");
                Intent i = new Intent(this, ResultActivity.class);
                i.putExtra(ResultActivity.EXTRA_IMAGE_PATH, path);
                i.putExtra(ResultActivity.EXTRA_RESULT, result);
                i.putExtra(ResultActivity.EXTRA_LESION_ID, lesionId);
                i.putExtra(EXTRA_NEW_PATIENT_ID, getIntent().getLongExtra(EXTRA_NEW_PATIENT_ID, -1));
                i.putExtra(EXTRA_NEW_LESION_LABEL, getIntent().getStringExtra(EXTRA_NEW_LESION_LABEL));
                i.putExtra(EXTRA_NEW_LESION_BODY, getIntent().getStringExtra(EXTRA_NEW_LESION_BODY));
                i.putExtra(EXTRA_NEW_LESION_NOTES, getIntent().getStringExtra(EXTRA_NEW_LESION_NOTES));
                startActivity(i);
            });
        } catch (Exception e) { showAnalysisError(e);
        } catch (OutOfMemoryError oom) {
            showAnalysisError(new Exception("Not enough memory to analyse this image. Try a smaller photo."));
        }
    }

    private void showAnalysisError(Exception e) {
        runOnUiThread(() -> {
            setRunning(false, "Analysis failed • Please retry");
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
        });
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        if (executor != null) executor.shutdown();
        if (modelManager != null) modelManager.close();
    }
}
