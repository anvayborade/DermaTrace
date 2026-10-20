package com.dermatrace.app;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.dermatrace.app.adapter.HistoryAdapter;
import com.dermatrace.app.data.DermaTraceDbHelper;
import com.dermatrace.app.model.AssessmentRecord;
import com.dermatrace.app.model.Lesion;
import com.dermatrace.app.model.Patient;
import com.dermatrace.app.reminder.FollowUpScheduler;
import com.dermatrace.app.util.AppPreferences;
import com.google.android.material.button.MaterialButton;

import java.util.List;

public class LesionDetailActivity extends BaseDrawerActivity {
    public static final String EXTRA_LESION_ID = "lesion_id";
    private static final int REQ_NOTIFICATIONS = 50;

    private long lesionId;
    private DermaTraceDbHelper db;
    private Lesion lesion;
    private Patient patient;
    private HistoryAdapter adapter;
    private TextView latestText, empty, bodyText, notesText;
    private AssessmentRecord contextRecord;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setDrawerContentView(R.layout.activity_lesion_detail, R.id.toolbarLesionDetail);

        lesionId = getIntent().getLongExtra(EXTRA_LESION_ID, -1);
        db = new DermaTraceDbHelper(this);
        lesion = db.getLesion(lesionId);
        if (lesion == null) { finish(); return; }
        patient = db.getPatient(lesion.patientId);

        bodyText = findViewById(R.id.txtLesionBody);
        notesText = findViewById(R.id.txtLesionNotes);
        latestText = findViewById(R.id.txtLatestAssessment);
        empty = findViewById(R.id.txtTimelineEmpty);

        ListView list = findViewById(R.id.listLesionTimeline);
        adapter = new HistoryAdapter(this, db, db.getAssessmentsForLesion(lesionId));
        list.setAdapter(adapter);
        list.setDivider(null);
        list.setDividerHeight(0);
        list.setClipToPadding(false);
        list.setOnItemClickListener((p, v, pos, id) -> openAssessment(adapter.getItem(pos).id));
        list.setOnItemLongClickListener((parent, view, position, id) -> {
            showAssessmentPopup(view, adapter.getItem(position));
            return true;
        });

        ((MaterialButton)findViewById(R.id.btnFollowUpScan)).setOnClickListener(v -> startFollowUp());
        ((MaterialButton)findViewById(R.id.btnCompareLatest)).setOnClickListener(v -> compareLatest());
        ((MaterialButton)findViewById(R.id.btnSetReminder)).setOnClickListener(v -> scheduleReminder());
        ((MaterialButton)findViewById(R.id.btnLesionReports)).setOnClickListener(v -> openReports());
        ((MaterialButton)findViewById(R.id.btnEditLesion)).setOnClickListener(v -> showEditLesion());
        ((MaterialButton)findViewById(R.id.btnDeleteLesion)).setOnClickListener(v -> confirmDeleteLesion());
        refresh();
    }

    @Override protected void onResume() { super.onResume(); refresh(); }

    private void refresh() {
        lesion = db.getLesion(lesionId);
        if (lesion == null) { finish(); return; }
        patient = db.getPatient(lesion.patientId);
        drawerToolbar.setTitle(lesion.label);
        drawerToolbar.setSubtitle(patient == null ? lesion.bodyLocation : patient.name + " • " + lesion.bodyLocation);
        bodyText.setText("Body location: " + lesion.bodyLocation);
        notesText.setText(lesion.notes == null || lesion.notes.isEmpty() ? "No lesion notes" : lesion.notes);

        List<AssessmentRecord> items = db.getAssessmentsForLesion(lesionId);
        adapter.setItems(items);
        empty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
        AssessmentRecord latest = db.getLatestAssessmentForLesion(lesionId);
        if (latest == null) latestText.setText("No assessment yet — take the first scan.");
        else latestText.setText("Latest AI result: " + friendlyConcern(latest.triageDisplayLabel) + " • " + Math.round(latest.higherConcernProbability * 100f) + "% concern score");
    }

    private void startFollowUp() {
        AssessmentRecord latest = db.getLatestAssessmentForLesion(lesionId);
        Intent i = new Intent(this, CameraAnalysisActivity.class);
        i.putExtra(CameraAnalysisActivity.EXTRA_LESION_ID, lesionId);
        if (latest != null && latest.imagePath != null) i.putExtra(CameraAnalysisActivity.EXTRA_PREVIOUS_IMAGE_PATH, latest.imagePath);
        startActivity(i);
    }

    private void compareLatest() {
        AssessmentRecord[] pair = db.getLatestTwoAssessments(lesionId);
        if (pair[0] == null || pair[1] == null) {
            Toast.makeText(this, "Take at least two scans of this lesion before comparing.", Toast.LENGTH_LONG).show();
            return;
        }
        Intent i = new Intent(this, ComparisonActivity.class);
        i.putExtra(ComparisonActivity.EXTRA_ASSESSMENT_ID, pair[0].id);
        startActivity(i);
    }

    private void scheduleReminder() {
        requestNotificationPermissionIfNeeded();
        int days = AppPreferences.getDefaultFollowUpDays(this);
        FollowUpScheduler.scheduleDays(this, lesionId, lesion.label, days);
        Toast.makeText(this, FollowUpScheduler.scheduledMessage(this, days), Toast.LENGTH_LONG).show();
    }

    private void openReports() {
        Intent i = new Intent(this, ReportsActivity.class);
        i.putExtra(ReportsActivity.EXTRA_LESION_ID, lesionId);
        startActivity(i);
    }

    private void showEditLesion() {
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_add_lesion, null);
        EditText label = v.findViewById(R.id.edtDialogLesionLabel);
        Spinner body = v.findViewById(R.id.spinnerDialogBodyLocation);
        EditText notes = v.findViewById(R.id.edtDialogLesionNotes);
        ArrayAdapter<CharSequence> bodyAdapter = ArrayAdapter.createFromResource(this, R.array.body_locations, R.layout.spinner_dialog_item_dermatrace);
        bodyAdapter.setDropDownViewResource(R.layout.spinner_dialog_dropdown_dermatrace);
        body.setAdapter(bodyAdapter);
        label.setText(lesion.label);
        notes.setText(lesion.notes);
        setSpinnerToValue(body, lesion.bodyLocation);

        new AlertDialog.Builder(this)
                .setTitle("Edit lesion")
                .setView(v)
                .setPositiveButton("Update", (d, which) -> {
                    db.updateLesion(lesionId, label.getText().toString(), String.valueOf(body.getSelectedItem()), notes.getText().toString());
                    refresh();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmDeleteLesion() {
        new AlertDialog.Builder(this)
                .setTitle("Delete lesion?")
                .setMessage("This permanently deletes this lesion and all its assessments from SQLite.")
                .setPositiveButton("Delete", (d, w) -> {
                    FollowUpScheduler.cancel(this, lesionId);
                    db.deleteLesion(lesionId);
                    finish();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showAssessmentPopup(View anchor, AssessmentRecord record) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.inflate(R.menu.history_actions_menu);
        popup.setOnMenuItemClickListener(item -> handleAssessmentAction(item.getItemId(), record));
        popup.show();
    }

    private boolean handleAssessmentAction(int id, AssessmentRecord record) {
        if (id == R.id.action_view) { openAssessment(record.id); return true; }
        if (id == R.id.action_edit_notes) { editAssessmentNote(record); return true; }
        if (id == R.id.action_compare) {
            Intent i = new Intent(this, ComparisonActivity.class);
            i.putExtra(ComparisonActivity.EXTRA_ASSESSMENT_ID, record.id);
            startActivity(i); return true;
        }
        if (id == R.id.action_reports || id == R.id.action_export_pdf || id == R.id.action_export_txt || id == R.id.action_share) {
            Intent i = new Intent(this, ReportViewerActivity.class);
            i.putExtra(ReportViewerActivity.EXTRA_ASSESSMENT_ID, record.id);
            startActivity(i); return true;
        }
        if (id == R.id.action_delete) {
            new AlertDialog.Builder(this)
                    .setTitle("Delete assessment?")
                    .setPositiveButton("Delete", (d, w) -> { db.deleteAssessment(record.id); refresh(); })
                    .setNegativeButton("Cancel", null).show();
            return true;
        }
        return false;
    }

    private void editAssessmentNote(AssessmentRecord record) {
        EditText input = new EditText(this);
        input.setText(record.userNotes == null ? "" : record.userNotes);
        input.setHint("Optional assessment note");
        input.setMaxLines(6);
        new AlertDialog.Builder(this)
                .setTitle("Edit assessment note")
                .setView(input)
                .setPositiveButton("Update", (d, w) -> { db.updateAssessmentNotes(record.id, input.getText().toString()); refresh(); })
                .setNegativeButton("Cancel", null).show();
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATIONS);
        }
    }

    private void openAssessment(long id) {
        Intent i = new Intent(this, ResultActivity.class);
        i.putExtra(ResultActivity.EXTRA_ASSESSMENT_ID, id);
        startActivity(i);
    }

    private String friendlyConcern(String v) {
        if (v == null) return "AI result";
        if (v.contains("HIGHER")) return "Higher model concern";
        if (v.contains("BORDERLINE") || v.contains("INCONCLUSIVE")) return "Inconclusive";
        return "Lower model concern";
    }

    private void setSpinnerToValue(Spinner spinner, String value) {
        if (value == null) return;
        for (int i = 0; i < spinner.getCount(); i++) {
            if (value.equalsIgnoreCase(String.valueOf(spinner.getItemAtPosition(i)))) { spinner.setSelection(i); return; }
        }
    }
}
