package com.dermatrace.app;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.provider.OpenableColumns;
import android.widget.ImageView;
import androidx.core.content.ContextCompat;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import com.dermatrace.app.model.Patient;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.dermatrace.app.data.DermaTraceDbHelper;
import com.dermatrace.app.util.FileUtils;
import com.google.android.material.button.MaterialButton;

public class NewAnalysisActivity extends BaseDrawerActivity {

    private Spinner patientSpinner;
    private final List<Patient> availablePatients = new ArrayList<>();
    private EditText lesionLabel;
    private EditText notes;
    private Spinner body;
    private Uri pendingUri;
    private boolean sourceCamera = true;
    private DermaTraceDbHelper db;
    private boolean launching;   // blocks double taps on Continue

    private final ActivityResultLauncher<String[]> imagePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri == null) return;
                pendingUri = uri;
                sourceCamera = false;
                TextView status = findViewById(R.id.txtSelectedImageStatus);
                if (status != null) status.setText("Image selected • tap Continue to analyse");
                updateSourceSelection();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setDrawerContentView(R.layout.activity_new_analysis, R.id.toolbarNewAnalysis);

        db = new DermaTraceDbHelper(this);
        if (savedInstanceState != null) {              // survive rotation
            sourceCamera = savedInstanceState.getBoolean("source_camera", true);
            String u = savedInstanceState.getString("pending_uri");
            if (u != null) pendingUri = Uri.parse(u);
        }
        patientSpinner = findViewById(R.id.spinnerPatientProfile);
        lesionLabel = findViewById(R.id.edtLesionLabel);
        notes = findViewById(R.id.edtLesionNotes);
        body = findViewById(R.id.spinnerBodyLocation);

        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                this, R.array.body_locations, R.layout.spinner_item_dermatrace);
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_dermatrace);
        body.setAdapter(adapter);

        loadPatientSuggestions();

        ((MaterialButton)findViewById(R.id.btnStartNewAnalysis)).setOnClickListener(v -> {
            sourceCamera = true;
            pendingUri = null;
            updateSourceSelection();
        });

        ((MaterialButton)findViewById(R.id.btnChooseExistingImage)).setOnClickListener(v ->
                imagePickerLauncher.launch(new String[]{"image/*"}));

        View addPatient = findViewById(R.id.btnAddNewPatientInline);
        if (addPatient != null) addPatient.setOnClickListener(v -> showQuickAddPatient());

        ((MaterialButton)findViewById(R.id.btnContinueAnalysis)).setOnClickListener(v -> continueAnalysis());

        View clearImage = findViewById(R.id.btnClearSelectedImage);
        if (clearImage != null) clearImage.setOnClickListener(v -> {
            pendingUri = null;
            sourceCamera = true;
            updateSourceSelection();
        });

        ((MaterialButton)findViewById(R.id.btnUseExistingLesion)).setOnClickListener(v ->
                startActivity(new Intent(this, PatientsActivity.class)));

        updateSourceSelection();
    }


    private void loadPatientSuggestions() {
        long selectedId = selectedPatientId();
        availablePatients.clear();
        availablePatients.addAll(db.getPatients());
        List<String> labels = new ArrayList<>();
        if (availablePatients.isEmpty()) labels.add("No patients yet — tap Add new patient");
        else for (Patient patient : availablePatients) labels.add(patient.name + "  •  #" + patient.id);
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(this,
                R.layout.spinner_item_dermatrace, labels);
        spinnerAdapter.setDropDownViewResource(R.layout.spinner_dropdown_dermatrace);
        patientSpinner.setAdapter(spinnerAdapter);
        for (int i = 0; i < availablePatients.size(); i++) {
            if (availablePatients.get(i).id == selectedId) {
                patientSpinner.setSelection(i); break;
            }
        }
    }

    private long selectedPatientId() {
        if (patientSpinner == null || availablePatients.isEmpty()) return -1;
        int position = patientSpinner.getSelectedItemPosition();
        return position >= 0 && position < availablePatients.size()
                ? availablePatients.get(position).id : -1;
    }

    @Override protected void onResume() {
        super.onResume();
        launching = false;
        if (db != null && patientSpinner != null) loadPatientSuggestions();
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putBoolean("source_camera", sourceCamera);
        if (pendingUri != null) out.putString("pending_uri", pendingUri.toString());
    }

    private void showQuickAddPatient() {
        EditText input = new EditText(this);
        input.setHint("Patient / profile name");
        int pad = (int)(20 * getResources().getDisplayMetrics().density);
        android.widget.FrameLayout holder = new android.widget.FrameLayout(this);
        holder.setPadding(pad, 0, pad, 0);
        holder.addView(input, new android.widget.FrameLayout.LayoutParams(-1, -2));
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Add patient")
                .setView(holder)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Add", null)
                .create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(btn -> {
            String name = input.getText().toString().trim();
            if (name.isEmpty()) { input.setError("Enter a name for this patient"); return; }
            long newId = db.addPatient(name, "", "", "");
            loadPatientSuggestions();
            for (int i = 0; i < availablePatients.size(); i++) {
                if (availablePatients.get(i).id == newId) {
                    patientSpinner.setSelection(i); break;
                }
            }
            dialog.dismiss();
        });
    }

    private void updateSourceSelection() {
        MaterialButton camera = findViewById(R.id.btnStartNewAnalysis);
        MaterialButton file = findViewById(R.id.btnChooseExistingImage);
        TextView status = findViewById(R.id.txtSelectedImageStatus);
        View card = findViewById(R.id.cardSelectedImage);
        boolean imageChosen = !sourceCamera && pendingUri != null;

        styleSourceButton(camera, sourceCamera);
        styleSourceButton(file, !sourceCamera);
        if (card != null) card.setVisibility(imageChosen ? View.VISIBLE : View.GONE);
        if (status != null) {
            status.setVisibility(imageChosen ? View.GONE : View.VISIBLE);
            if (sourceCamera) status.setText("Camera selected • tap Continue to open capture");
        }
        if (imageChosen) showSelectedPreview(pendingUri);
    }

    /** Selected source = tinted background, teal outline and teal icon/text; the other stays neutral. */
    private void styleSourceButton(MaterialButton b, boolean selected) {
        if (b == null) return;
        float dp = getResources().getDisplayMetrics().density;
        b.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, selected ? R.color.dt_primary_container : R.color.dt_surface)));
        b.setStrokeColor(ColorStateList.valueOf(ContextCompat.getColor(this, selected ? R.color.dt_primary : R.color.dt_border)));
        b.setStrokeWidth(Math.round((selected ? 2 : 1) * dp));
        b.setIconTint(ColorStateList.valueOf(ContextCompat.getColor(this, selected ? R.color.dt_primary_dark : R.color.dt_text_muted)));
        b.setTextColor(ContextCompat.getColor(this, selected ? R.color.dt_primary_dark : R.color.dt_text));
    }

    /** Thumbnail + file name + size, so the user can see which image was picked. */
    private void showSelectedPreview(Uri uri) {
        ImageView thumb = findViewById(R.id.imgSelectedThumb);
        TextView nameView = findViewById(R.id.txtSelectedFileName);
        TextView meta = findViewById(R.id.txtSelectedFileMeta);
        if (thumb == null || nameView == null || meta == null) return;
        String name = "Selected image";
        long size = -1;
        try (Cursor c = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE}, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                if (!c.isNull(0)) name = c.getString(0);
                if (!c.isNull(1)) size = c.getLong(1);
            }
        } catch (Exception ignored) {}
        nameView.setText(name);
        meta.setText("✓ Image selected" + (size > 0 ? " • " + formatSize(size) : ""));
        thumb.setImageDrawable(null);
        final Uri target = uri;
        new Thread(() -> {
            Bitmap b = com.dermatrace.app.util.BitmapUtils.loadThumbnail(this, target, 200);
            runOnUiThread(() -> { if (!isFinishing() && target.equals(pendingUri)) thumb.setImageBitmap(b); });
        }).start();
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024L * 1024L) return Math.max(1, bytes / 1024) + " KB";
        return String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0));
    }

    private void continueAnalysis() {
        if (selectedPatientId() <= 0) {
            Toast.makeText(this, "Add a patient/profile first, then select it.", Toast.LENGTH_LONG).show();
            return;
        }
        if (launching) return;
        if (sourceCamera) {
            Intent i = new Intent(this, CameraAnalysisActivity.class);
            putNewLesionExtras(i);
            launching = true;
            startActivity(i);
        } else {
            if (pendingUri == null) {
                Toast.makeText(this, "Choose an image first.", Toast.LENGTH_SHORT).show();
                return;
            }
            importAndAnalyze(pendingUri);
        }
    }

    /** The lesion row is created by ResultActivity only after a scan succeeds. */
    private void putNewLesionExtras(Intent i) {
        i.putExtra(CameraAnalysisActivity.EXTRA_LESION_ID, -1L);
        i.putExtra(CameraAnalysisActivity.EXTRA_NEW_PATIENT_ID, selectedPatientId());
        i.putExtra(CameraAnalysisActivity.EXTRA_NEW_LESION_LABEL, lesionLabel.getText().toString());
        i.putExtra(CameraAnalysisActivity.EXTRA_NEW_LESION_BODY, String.valueOf(body.getSelectedItem()));
        i.putExtra(CameraAnalysisActivity.EXTRA_NEW_LESION_NOTES, notes.getText().toString());
    }

    private void importAndAnalyze(Uri uri) {
        try {
            String path = FileUtils.copyUriToAppPictures(this, uri);
            Intent i = new Intent(this, CameraAnalysisActivity.class);
            putNewLesionExtras(i);
            i.putExtra(CameraAnalysisActivity.EXTRA_EXISTING_IMAGE_PATH, path);
            launching = true;
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, "Could not open selected image: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
