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

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.PopupMenu;

import com.dermatrace.app.adapter.LesionAdapter;
import com.dermatrace.app.data.DermaTraceDbHelper;
import com.dermatrace.app.model.Lesion;
import com.dermatrace.app.model.Patient;
import com.dermatrace.app.reminder.FollowUpScheduler;
import com.google.android.material.button.MaterialButton;

import java.util.List;

public class PatientDetailActivity extends BaseDrawerActivity {
    public static final String EXTRA_PATIENT_ID = "patient_id";

    private long patientId;
    private DermaTraceDbHelper db;
    private LesionAdapter adapter;
    private TextView empty, meta;
    private Patient patient;
    private Lesion contextLesion;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setDrawerContentView(R.layout.activity_patient_detail, R.id.toolbarPatientDetail);

        patientId = getIntent().getLongExtra(EXTRA_PATIENT_ID, -1);
        db = new DermaTraceDbHelper(this);
        patient = db.getPatient(patientId);
        if (patient == null) { finish(); return; }

        drawerToolbar.setTitle(patient.name);
        meta = findViewById(R.id.txtPatientMeta);
        empty = findViewById(R.id.txtLesionsEmpty);

        ListView list = findViewById(R.id.listLesions);
        adapter = new LesionAdapter(this, db, db.getLesionsForPatient(patientId));
        list.setAdapter(adapter);
        list.setDivider(null);
        list.setDividerHeight(0);
        list.setClipToPadding(false);
        list.setOnItemClickListener((p, v, pos, id) -> openLesion(adapter.getItem(pos).id));
        list.setOnItemLongClickListener((parent, view, position, id) -> {
            showLesionPopup(view, adapter.getItem(position));
            return true;
        });

        ((MaterialButton)findViewById(R.id.btnAddLesion)).setOnClickListener(v -> showLesionDialog(null));
        ((MaterialButton)findViewById(R.id.btnEditPatient)).setOnClickListener(v -> showEditPatient());
        refresh();
    }

    @Override protected void onResume() { super.onResume(); refresh(); }

    private String buildMeta(Patient p) {
        String s = "";
        if (p.age != null && !p.age.isEmpty()) s += "Age " + p.age;
        if (p.sex != null && !p.sex.isEmpty()) s += (s.isEmpty() ? "" : " • ") + p.sex;
        if (p.notes != null && !p.notes.isEmpty()) s += (s.isEmpty() ? "" : "\n") + p.notes;
        return s.isEmpty() ? "Local patient/profile record" : s;
    }

    private void refresh() {
        patient = db.getPatient(patientId);
        if (patient == null) { finish(); return; }
        drawerToolbar.setTitle(patient.name);
        meta.setText(buildMeta(patient));
        List<Lesion> lesions = db.getLesionsForPatient(patientId);
        adapter.setItems(lesions);
        empty.setVisibility(lesions.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void showEditPatient() {
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_add_patient, null);
        EditText name = v.findViewById(R.id.edtDialogPatientName);
        EditText age = v.findViewById(R.id.edtDialogPatientAge);
        Spinner sex = v.findViewById(R.id.spinnerDialogPatientSex);
        EditText notes = v.findViewById(R.id.edtDialogPatientNotes);
        ArrayAdapter<CharSequence> sexAdapter = ArrayAdapter.createFromResource(this, R.array.sex_options, R.layout.spinner_dialog_item_dermatrace);
        sexAdapter.setDropDownViewResource(R.layout.spinner_dialog_dropdown_dermatrace);
        sex.setAdapter(sexAdapter);
        name.setText(patient.name); age.setText(patient.age); notes.setText(patient.notes);
        setSpinnerToValue(sex, patient.sex);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Edit patient / profile")
                .setView(v)
                .setPositiveButton("Update", null)
                .setNegativeButton("Cancel", null)
                .create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(btn -> {
            if (name.getText().toString().trim().isEmpty()) { name.setError("Enter a name for this patient"); return; }
            db.updatePatient(patientId, name.getText().toString(), age.getText().toString(), String.valueOf(sex.getSelectedItem()), notes.getText().toString());
            refresh();
            dialog.dismiss();
        });
    }

    private void showLesionPopup(View anchor, Lesion lesion) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.inflate(R.menu.lesion_actions_menu);
        popup.setOnMenuItemClickListener(item -> handleLesionAction(item.getItemId(), lesion));
        popup.show();
    }

    private boolean handleLesionAction(int id, Lesion lesion) {
        if (id == R.id.lesion_view) { openLesion(lesion.id); return true; }
        if (id == R.id.lesion_edit) { showLesionDialog(lesion); return true; }
        if (id == R.id.lesion_reports) {
            Intent i = new Intent(this, ReportsActivity.class);
            i.putExtra(ReportsActivity.EXTRA_LESION_ID, lesion.id);
            startActivity(i); return true;
        }
        if (id == R.id.lesion_delete) {
            new AlertDialog.Builder(this)
                    .setTitle("Delete lesion?")
                    .setMessage("This also deletes all assessments stored for this lesion.")
                    .setPositiveButton("Delete", (d, w) -> {
                        FollowUpScheduler.cancel(this, lesion.id);
                        db.deleteLesion(lesion.id);
                        refresh();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            return true;
        }
        return false;
    }

    private void showLesionDialog(Lesion existing) {
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_add_lesion, null);
        EditText label = v.findViewById(R.id.edtDialogLesionLabel);
        Spinner body = v.findViewById(R.id.spinnerDialogBodyLocation);
        EditText notes = v.findViewById(R.id.edtDialogLesionNotes);

        ArrayAdapter<CharSequence> bodyAdapter = ArrayAdapter.createFromResource(this, R.array.body_locations, R.layout.spinner_dialog_item_dermatrace);
        bodyAdapter.setDropDownViewResource(R.layout.spinner_dialog_dropdown_dermatrace);
        body.setAdapter(bodyAdapter);

        if (existing != null) {
            label.setText(existing.label);
            notes.setText(existing.notes);
            setSpinnerToValue(body, existing.bodyLocation);
        }

        new AlertDialog.Builder(this)
                .setTitle(existing == null ? "Add lesion" : "Edit lesion")
                .setView(v)
                .setPositiveButton(existing == null ? "Save" : "Update", (d, which) -> {
                    if (existing == null) {
                        long id = db.addLesion(patientId, label.getText().toString(), String.valueOf(body.getSelectedItem()), notes.getText().toString());
                        refresh();
                        openLesion(id);
                    } else {
                        db.updateLesion(existing.id, label.getText().toString(), String.valueOf(body.getSelectedItem()), notes.getText().toString());
                        refresh();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void openLesion(long id) {
        Intent i = new Intent(this, LesionDetailActivity.class);
        i.putExtra(LesionDetailActivity.EXTRA_LESION_ID, id);
        startActivity(i);
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
