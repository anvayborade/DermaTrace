package com.dermatrace.app;

import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.dermatrace.app.data.DermaTraceDbHelper;
import com.dermatrace.app.model.AssessmentRecord;
import com.dermatrace.app.model.Lesion;
import com.dermatrace.app.model.Patient;
import com.dermatrace.app.util.FileUtils;
import com.dermatrace.app.util.IntentUtils;
import com.dermatrace.app.util.ReportUtils;
import com.google.android.material.button.MaterialButton;

import java.io.File;

public class ReportViewerActivity extends BaseDrawerActivity {
    public static final String EXTRA_ASSESSMENT_ID = "assessment_id";

    private AssessmentRecord record;
    private Patient patient;
    private Lesion lesion;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setDrawerContentView(R.layout.activity_report_viewer, R.id.toolbarReportViewer);

        long id = getIntent().getLongExtra(EXTRA_ASSESSMENT_ID, -1);
        DermaTraceDbHelper db = new DermaTraceDbHelper(this);
        record = db.getAssessment(id);
        if (record == null) { finish(); return; }
        lesion = db.getLesion(record.lesionId);
        patient = lesion == null ? null : db.getPatient(lesion.patientId);
        drawerToolbar.setSubtitle((patient == null ? "" : patient.name + " • ") + (lesion == null ? "Assessment" : lesion.label));

        if (record.imagePath != null && !record.imagePath.isEmpty()) {
            ((ImageView)findViewById(R.id.imgReportViewer)).setImageBitmap(com.dermatrace.app.util.BitmapUtils.loadSampledOriented(record.imagePath, 1280));
        }

        refreshTxt();

        ((MaterialButton)findViewById(R.id.btnReportSaveTxt)).setOnClickListener(v -> save(false, false));
        ((MaterialButton)findViewById(R.id.btnReportShareTxt)).setOnClickListener(v -> save(false, true));
        ((MaterialButton)findViewById(R.id.btnReportSavePdf)).setOnClickListener(v -> save(true, false));
        ((MaterialButton)findViewById(R.id.btnReportSharePdf)).setOnClickListener(v -> save(true, true));
    }

    private void refreshTxt() {
        try {
            File txt = ReportUtils.createTxt(this, record, patient, lesion);
            ((TextView)findViewById(R.id.txtReportContents)).setText(FileUtils.readTextFile(txt));
        } catch (Exception e) {
            ((TextView)findViewById(R.id.txtReportContents)).setText("Could not generate TXT report: " + e.getMessage());
        }
    }

    private void save(boolean pdf, boolean share) {
        try {
            File f = pdf ? ReportUtils.createPdf(this, record, patient, lesion) : ReportUtils.createTxt(this, record, patient, lesion);
            if (share) IntentUtils.shareFile(this, f, pdf ? "application/pdf" : "text/plain");
            else Toast.makeText(this, "Saved:\n" + f.getAbsolutePath(), Toast.LENGTH_LONG).show();
            if (!pdf) refreshTxt();
        } catch (Exception e) {
            Toast.makeText(this, "Report failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
