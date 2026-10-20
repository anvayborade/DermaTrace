package com.dermatrace.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.PopupMenu;

import com.dermatrace.app.adapter.HistoryAdapter;
import com.dermatrace.app.data.DermaTraceDbHelper;
import com.dermatrace.app.model.AssessmentRecord;
import com.dermatrace.app.model.Lesion;
import com.dermatrace.app.model.Patient;
import com.dermatrace.app.util.IntentUtils;
import com.dermatrace.app.util.ReportUtils;

import java.io.File;
import java.util.List;

public class HistoryActivity extends BaseDrawerActivity {
    private DermaTraceDbHelper db;
    private HistoryAdapter adapter;
    private TextView empty;
    private AssessmentRecord contextRecord;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setDrawerContentView(R.layout.activity_history, R.id.toolbarHistory);

        db = new DermaTraceDbHelper(this);
        ListView list = findViewById(R.id.listHistory);
        empty = findViewById(R.id.txtHistoryEmpty);
        adapter = new HistoryAdapter(this, db, db.getAssessments());
        list.setAdapter(adapter);
        list.setDivider(null);
        list.setDividerHeight(0);
        list.setClipToPadding(false);
        list.setOnItemClickListener((p, v, pos, id) -> open(adapter.getItem(pos).id));
        list.setOnItemLongClickListener((parent, view, position, id) -> {
            showPopup(view, adapter.getItem(position));
            return true;
        });
        refresh();
    }

    @Override protected void onResume() { super.onResume(); refresh(); }

    private void refresh() {
        if (adapter == null) return;
        List<AssessmentRecord> items = db.getAssessments();
        adapter.setItems(items);
        empty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void showPopup(View anchor, AssessmentRecord record) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.inflate(R.menu.history_actions_menu);
        popup.setOnMenuItemClickListener(item -> handleAction(item.getItemId(), record));
        popup.show();
    }

    private boolean handleAction(int id, AssessmentRecord record) {
        if (id == R.id.action_view) { open(record.id); return true; }
        if (id == R.id.action_edit_notes) { editNotes(record); return true; }
        if (id == R.id.action_compare) {
            Intent i = new Intent(this, ComparisonActivity.class);
            i.putExtra(ComparisonActivity.EXTRA_ASSESSMENT_ID, record.id);
            startActivity(i); return true;
        }
        if (id == R.id.action_reports) {
            Intent i = new Intent(this, ReportViewerActivity.class);
            i.putExtra(ReportViewerActivity.EXTRA_ASSESSMENT_ID, record.id);
            startActivity(i); return true;
        }
        if (id == R.id.action_export_pdf) { export(record, true, false); return true; }
        if (id == R.id.action_export_txt) { export(record, false, false); return true; }
        if (id == R.id.action_share) { export(record, true, true); return true; }
        if (id == R.id.action_delete) {
            new AlertDialog.Builder(this)
                    .setTitle("Delete assessment?")
                    .setMessage("This permanently removes the saved assessment from the local SQLite database.")
                    .setPositiveButton("Delete", (d, w) -> { db.deleteAssessment(record.id); refresh(); })
                    .setNegativeButton("Cancel", null).show();
            return true;
        }
        return false;
    }

    private void editNotes(AssessmentRecord record) {
        EditText input = new EditText(this);
        input.setText(record.userNotes == null ? "" : record.userNotes);
        input.setHint("Optional assessment note");
        input.setMaxLines(6);
        int p = (int)(18 * getResources().getDisplayMetrics().density);
        input.setPadding(p, p, p, p);

        new AlertDialog.Builder(this)
                .setTitle("Edit assessment note")
                .setView(input)
                .setPositiveButton("Update", (d, which) -> {
                    db.updateAssessmentNotes(record.id, input.getText().toString());
                    refresh();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void export(AssessmentRecord a, boolean pdf, boolean share) {
        try {
            Lesion l = db.getLesion(a.lesionId);
            Patient p = l == null ? null : db.getPatient(l.patientId);
            File file = pdf ? ReportUtils.createPdf(this, a, p, l) : ReportUtils.createTxt(this, a, p, l);
            if (share) IntentUtils.shareFile(this, file, pdf ? "application/pdf" : "text/plain");
            else Toast.makeText(this, "Saved report:\n" + file.getAbsolutePath(), Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Report failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void open(long id) {
        Intent i = new Intent(this, ResultActivity.class);
        i.putExtra(ResultActivity.EXTRA_ASSESSMENT_ID, id);
        startActivity(i);
    }
}
