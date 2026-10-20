package com.dermatrace.app;

import android.content.Intent;
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
import android.text.Editable;
import android.text.TextWatcher;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import com.dermatrace.app.model.FollowUpRecord;
import com.dermatrace.app.model.AssessmentRecord;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.PopupMenu;

import com.dermatrace.app.adapter.PatientAdapter;
import com.dermatrace.app.data.DermaTraceDbHelper;
import com.dermatrace.app.model.Patient;
import com.dermatrace.app.model.Lesion;
import com.dermatrace.app.reminder.FollowUpScheduler;
import com.google.android.material.button.MaterialButton;

import java.util.List;

public class PatientsActivity extends BaseDrawerActivity {
    private DermaTraceDbHelper db;
    private PatientAdapter adapter;
    private TextView empty;
    private Patient contextPatient;
    private String selectedFilter = "ALL";
    private String searchQuery = "";

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setDrawerContentView(R.layout.activity_patients, R.id.toolbarPatients);

        db = new DermaTraceDbHelper(this);
        ListView list = findViewById(R.id.listPatients);
        empty = findViewById(R.id.txtPatientsEmpty);
        adapter = new PatientAdapter(this, db, db.getPatients());
        list.setAdapter(adapter);
        list.setDivider(null);
        list.setDividerHeight(0);
        list.setClipToPadding(false);

        ((MaterialButton)findViewById(R.id.btnAddPatient)).setOnClickListener(v -> showPatientDialog(null));

        EditText search = findViewById(R.id.edtPatientSearch);
        if (search != null) search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            public void onTextChanged(CharSequence s, int st, int b, int c) {
                searchQuery = s.toString();
                refresh();
            }
            public void afterTextChanged(Editable e) {}
        });

        bindStatusFilter(R.id.chipPatientsAll, "ALL");
        bindStatusFilter(R.id.chipPatientsActive, "ACTIVE");
        bindStatusFilter(R.id.chipPatientsFollowup, "FOLLOWUP");
        bindStatusFilter(R.id.chipPatientsNoScans, "NONE");

        list.setOnItemClickListener((parent, view, position, id) -> open(adapter.getItem(position)));
        list.setOnItemLongClickListener((parent, view, position, id) -> {
            showPopup(view, adapter.getItem(position));
            return true;
        });
        refresh();
    }

    @Override protected void onResume() { super.onResume(); refresh(); }

    private void bindStatusFilter(int id, String kind) {
        View view = findViewById(id);
        if (view != null) view.setOnClickListener(v -> {
            selectedFilter = kind;
            refresh();
        });
    }

    private void updateFilterAppearance() {
        int[] ids = {R.id.chipPatientsAll, R.id.chipPatientsActive,
                R.id.chipPatientsFollowup, R.id.chipPatientsNoScans};
        String[] types = {"ALL", "ACTIVE", "FOLLOWUP", "NONE"};
        for (int i = 0; i < ids.length; i++) {
            TextView chip = findViewById(ids[i]);
            if (chip == null) continue;
            boolean selected = types[i].equals(selectedFilter);
            int bg = R.drawable.bg_chip_outline;
            int fg = R.color.dt_text_muted;
            if (selected) {
                if ("ACTIVE".equals(selectedFilter)) {
                    bg = R.drawable.bg_chip_active_selected;
                    fg = R.color.dt_success;
                } else if ("FOLLOWUP".equals(selectedFilter)) {
                    bg = R.drawable.bg_chip_followup_selected;
                    fg = R.color.dt_accent_c;
                } else if ("NONE".equals(selectedFilter)) {
                    bg = R.drawable.bg_chip_neutral_selected;
                    fg = R.color.dt_text_muted;
                } else {
                    bg = R.drawable.bg_chip_selected;
                    fg = R.color.dt_white;
                }
            }
            chip.setBackgroundResource(bg);
            chip.setTextColor(androidx.core.content.ContextCompat.getColor(this, fg));
            chip.setTypeface(null, selected ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        }
    }

    private void refresh() {
        if (adapter == null || db == null) return;
        List<Patient> allPatients = db.getPatients();
        List<AssessmentRecord> assessments = db.getAssessments();
        List<FollowUpRecord> reminders = db.getActiveFollowUps();
        Map<Long, String> statuses = new HashMap<>();
        Set<Long> scannedPatientIds = new HashSet<>();
        Set<Long> followupPatientIds = new HashSet<>();
        Map<Long, Long> lesionOwners = new HashMap<>();
        for (Patient patient : allPatients) {
            for (Lesion lesion : db.getLesionsForPatient(patient.id)) {
                lesionOwners.put(lesion.id, patient.id);
            }
        }
        for (AssessmentRecord scan : assessments) {
            Long owner = lesionOwners.get(scan.lesionId);
            if (owner != null) scannedPatientIds.add(owner);
        }
        for (FollowUpRecord reminder : reminders) {
            Long owner = lesionOwners.get(reminder.lesionId);
            // A reminder counts as follow-up only for a profile with at least one saved scan.
            if (owner != null && scannedPatientIds.contains(owner)) followupPatientIds.add(owner);
        }
        for (Patient patient : allPatients) {
            statuses.put(patient.id,
                    followupPatientIds.contains(patient.id) ? "FOLLOWUP"
                            : scannedPatientIds.contains(patient.id) ? "ACTIVE" : "NONE");
        }
        adapter.setStatuses(statuses);
        List<Patient> visible = new ArrayList<>();
        String query = searchQuery == null ? "" : searchQuery.trim().toLowerCase(java.util.Locale.ROOT);
        for (Patient patient : allPatients) {
            String searchText = (patient.name + " " + patient.age + " " + patient.sex)
                    .toLowerCase(java.util.Locale.ROOT);
            if (!searchText.contains(query)) continue;
            if (!"ALL".equals(selectedFilter) && !selectedFilter.equals(statuses.get(patient.id))) continue;
            visible.add(patient);
        }
        adapter.setItems(visible);
        empty.setText(visible.isEmpty() ? (allPatients.isEmpty()
                ? "No patients yet. Add a patient to begin tracking lesions."
                : "No profiles match this filter. Try All or another search.") : "");
        empty.setVisibility(visible.isEmpty() ? View.VISIBLE : View.GONE);
        updateFilterAppearance();
    }

    private void showPopup(View anchor, Patient patient) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.inflate(R.menu.patient_actions_menu);
        popup.setOnMenuItemClickListener(item -> handleAction(item.getItemId(), patient));
        popup.show();
    }

    private boolean handleAction(int id, Patient p) {
        if (id == R.id.patient_view) { open(p); return true; }
        if (id == R.id.patient_edit) { showPatientDialog(p); return true; }
        if (id == R.id.patient_delete) {
            new AlertDialog.Builder(this)
                    .setTitle("Delete patient?")
                    .setMessage("This also deletes all lesions and saved assessments belonging to this patient from SQLite.")
                    .setPositiveButton("Delete", (d, w) -> {
                        for (Lesion lesion : db.getLesionsForPatient(p.id)) FollowUpScheduler.cancel(this, lesion.id);
                        db.deletePatient(p.id);
                        refresh();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            return true;
        }
        return false;
    }

    private void open(Patient p) {
        Intent i = new Intent(this, PatientDetailActivity.class);
        i.putExtra(PatientDetailActivity.EXTRA_PATIENT_ID, p.id);
        startActivity(i);
    }

    private void showPatientDialog(Patient existing) {
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_add_patient, null);
        EditText name = v.findViewById(R.id.edtDialogPatientName);
        EditText age = v.findViewById(R.id.edtDialogPatientAge);
        Spinner sex = v.findViewById(R.id.spinnerDialogPatientSex);
        EditText notes = v.findViewById(R.id.edtDialogPatientNotes);
        ArrayAdapter<CharSequence> sexAdapter = ArrayAdapter.createFromResource(this, R.array.sex_options, R.layout.spinner_dialog_item_dermatrace);
        sexAdapter.setDropDownViewResource(R.layout.spinner_dialog_dropdown_dermatrace);
        sex.setAdapter(sexAdapter);

        if (existing != null) {
            name.setText(existing.name);
            age.setText(existing.age);
            notes.setText(existing.notes);
            setSpinnerToValue(sex, existing.sex);
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(existing == null ? "Add patient / profile" : "Edit patient / profile")
                .setView(v)
                .setPositiveButton(existing == null ? "Save" : "Update", null)
                .setNegativeButton("Cancel", null)
                .create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(btn -> {
            if (name.getText().toString().trim().isEmpty()) { name.setError("Enter a name for this patient"); return; }
            if (existing == null) {
                db.addPatient(name.getText().toString(), age.getText().toString(), String.valueOf(sex.getSelectedItem()), notes.getText().toString());
            } else {
                db.updatePatient(existing.id, name.getText().toString(), age.getText().toString(), String.valueOf(sex.getSelectedItem()), notes.getText().toString());
            }
            refresh();
            dialog.dismiss();
        });
    }

    private void setSpinnerToValue(Spinner spinner, String value) {
        if (value == null) return;
        for (int i = 0; i < spinner.getCount(); i++) {
            if (value.equalsIgnoreCase(String.valueOf(spinner.getItemAtPosition(i)))) {
                spinner.setSelection(i); return;
            }
        }
    }
}
