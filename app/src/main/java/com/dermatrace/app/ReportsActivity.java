package com.dermatrace.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

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

public class ReportsActivity extends BaseDrawerActivity {
    public static final String EXTRA_LESION_ID = "lesion_id";

    private DermaTraceDbHelper db;
    private HistoryAdapter adapter;
    private TextView empty;
    private long lesionId;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setDrawerContentView(R.layout.activity_reports, R.id.toolbarReports);
        db = new DermaTraceDbHelper(this);
        View settingsTab = findViewById(R.id.btnReportsSettingsTab);
        if (settingsTab != null) settingsTab.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        lesionId = getIntent().getLongExtra(EXTRA_LESION_ID, -1);

        if (lesionId > 0) {
            Lesion l = db.getLesion(lesionId);
            if (l != null) drawerToolbar.setSubtitle(l.label);
        }

        empty = findViewById(R.id.txtReportsEmpty);
        ListView list = findViewById(R.id.listReports);
        adapter = new HistoryAdapter(this, db, getItems());
        list.setAdapter(adapter);
        list.setDivider(null);
        list.setDividerHeight(0);
        list.setClipToPadding(false);
        list.setOnItemClickListener((p, v, pos, id) -> openViewer(adapter.getItem(pos).id));
        list.setOnItemLongClickListener((parent, view, position, id) -> {
            showPopup(view, adapter.getItem(position));
            return true;
        });
        refresh();
    }

    @Override protected void onResume() { super.onResume(); refresh(); }

    private List<AssessmentRecord> getItems() {
        return lesionId > 0 ? db.getAssessmentsForLesion(lesionId) : db.getAssessments();
    }

    private void refresh() {
        if (adapter == null) return;
        List<AssessmentRecord> items = getItems();
        adapter.setItems(items);
        empty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void showPopup(View anchor, AssessmentRecord record) {
        PopupMenu p = new PopupMenu(this, anchor);
        p.inflate(R.menu.report_actions_menu);
        p.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.report_open) { openViewer(record.id); return true; }
            if (id == R.id.report_save_txt) { export(record, false, false); return true; }
            if (id == R.id.report_share_txt) { export(record, false, true); return true; }
            if (id == R.id.report_save_pdf) { export(record, true, false); return true; }
            if (id == R.id.report_share_pdf) { export(record, true, true); return true; }
            if (id == R.id.report_view_assessment) {
                Intent i = new Intent(this, ResultActivity.class);
                i.putExtra(ResultActivity.EXTRA_ASSESSMENT_ID, record.id);
                startActivity(i); return true;
            }
            return false;
        });
        p.show();
    }

    private void openViewer(long id) {
        Intent i = new Intent(this, ReportViewerActivity.class);
        i.putExtra(ReportViewerActivity.EXTRA_ASSESSMENT_ID, id);
        startActivity(i);
    }

    private void export(AssessmentRecord a, boolean pdf, boolean share) {
        try {
            Lesion l = db.getLesion(a.lesionId);
            Patient p = l == null ? null : db.getPatient(l.patientId);
            File f = pdf ? ReportUtils.createPdf(this, a, p, l) : ReportUtils.createTxt(this, a, p, l);
            if (share) IntentUtils.shareFile(this, f, pdf ? "application/pdf" : "text/plain");
            else Toast.makeText(this, "Saved:\n" + f.getAbsolutePath(), Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Report failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
